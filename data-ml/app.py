from __future__ import annotations

import json
import logging
import os
import re
import ast
import hashlib
import uuid
from threading import Lock, Thread
from datetime import datetime, date, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo
from urllib import error as urllib_error
from urllib import request as urllib_request

import pandas as pd
from dotenv import load_dotenv
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, Response

from crawler.service import run_crawl_now
from crawler.support_legacy.data_paths import feature_csv_path
from crawler.postprocessing import sentiment_score as sentiment_score_module
from scheduler import build_scheduler
from training.service import run_prediction_now
from db.db import fetch_policy_feed_frame, fetch_user_watch_asset_names, init_db
from llm.service import ArticleInsightGenerationService, HomeBriefingGenerationService
from llm.providers import build_llm_client
from lstm_signal.runner import (
    SignalRunnerError,
    load_latest_signal,
    parse_signal_request,
    run_signal,
)


load_dotenv(Path(__file__).resolve().with_name(".env"))
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Data-ML Service")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.error(f"예기치 못한 에러 발생: {exc}", exc_info=True)
    return _error_response(
        message="ML 서버 오류가 발생했습니다.", 
        code="INTERNAL_SERVER_ERROR", 
        status_code=500
    )

ML_PREFIX = "/ml"
BASE_DIR = Path(__file__).resolve().parent
TRAINING_DIR = BASE_DIR / "training"
POLICY_FEED_CANDIDATES = [
    feature_csv_path("policy_updates_features.csv"),
    feature_csv_path("daily_news_features.csv"),
]
MODEL_METADATA_PATH = TRAINING_DIR / "qqq_model_metadata.json"
TRAINING_SUMMARY_PATH = TRAINING_DIR / "qqq_training_summary.json"

WEBHOOK_URL = os.getenv("WEBHOOK_URL", "http://localhost:8080/api/internal/webhooks/events")
WEBHOOK_SECRET = os.getenv("WEBHOOK_SECRET", "")
RUN_PIPELINE_ON_STARTUP = os.getenv("RUN_PIPELINE_ON_STARTUP", "false").lower() == "true"
PIPELINE_BIS_MAX_PAGES = int(os.getenv("BIS_MAX_PAGES", "5"))
PIPELINE_SLEEP_SEC = float(os.getenv("CRAWL_SLEEP_SEC", "1"))

run_lock = Lock()
scheduler_instance = None
pipeline_job_state: dict[str, object] = {}
pipeline_job_state_lock = Lock()
article_insight_service = ArticleInsightGenerationService()
home_briefing_service = HomeBriefingGenerationService()


def _safe_extract_probs_from_output(output_one_text) -> dict:
    if output_one_text is None:
        return {
            "positive_prob": None,
            "negative_prob": None,
            "neutral_prob": None,
            "sentiment_score": None,
        }

    if isinstance(output_one_text, dict):
        if "label" in output_one_text and "score" in output_one_text:
            output_one_text = [output_one_text]
        else:
            normalized = []
            for value in output_one_text.values():
                if isinstance(value, dict) and "label" in value and "score" in value:
                    normalized.append(value)
                elif isinstance(value, (list, tuple)):
                    normalized.extend(
                        item for item in value
                        if isinstance(item, dict) and "label" in item and "score" in item
                    )
            output_one_text = normalized

    if not isinstance(output_one_text, (list, tuple)):
        return {
            "positive_prob": None,
            "negative_prob": None,
            "neutral_prob": None,
            "sentiment_score": None,
        }

    score_map = {}
    for item in output_one_text:
        if isinstance(item, dict) and "label" in item and "score" in item:
            score_map[str(item["label"]).lower()] = item["score"]

    pos = score_map.get("positive", 0.0)
    neg = score_map.get("negative", 0.0)
    neu = score_map.get("neutral", 0.0)

    return {
        "positive_prob": pos,
        "negative_prob": neg,
        "neutral_prob": neu,
        "sentiment_score": pos - neg,
    }


sentiment_score_module.extract_probs_from_output = _safe_extract_probs_from_output


def _safe_str(value, default=""):
    if value is None:
        return default
    text = str(value).strip()
    return text if text else default


def _safe_float(value, default=0.0):
    try:
        if pd.isna(value):
            return float(default)
        return float(value)
    except Exception:
        return float(default)


def _safe_json_load(path: Path) -> dict:
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return {}


def _success_response(result=None, message="요청에 성공했습니다.", code="SUCCESS-200"):
    data = {
        "isSuccess": True,
        "code": code,
        "message": message,
        "result": result,
    }
    return Response(
        status_code=200,
        content=json.dumps(data, ensure_ascii=False, indent=2),
        media_type="application/json",
    )


def _error_response(message="요청에 실패했습니다.", code="FAIL-001", status_code=500, details=None):
    data = {
        "isSuccess": False,
        "code": code,
        "message": message,
    }
    if details:
        data["details"] = details
    return Response(
        status_code=status_code,
        content=json.dumps(data, ensure_ascii=False, indent=2, default=str),
        media_type="application/json",
    )


def _remove_message_fields(value):
    if isinstance(value, dict):
        return {k: _remove_message_fields(v) for k, v in value.items() if k != "message"}
    if isinstance(value, list):
        return [_remove_message_fields(item) for item in value]
    return value


def _tail_text(value: str | None, limit: int = 500) -> str:
    text = value or ""
    if len(text) <= limit:
        return text
    return text[-limit:]


def _build_failure_reason(result: dict, fallback_message: str) -> str:
    message = _safe_str(result.get("message"), "")
    stderr_tail = _safe_str(result.get("stderr_tail"), "")
    stdout_tail = _safe_str(result.get("stdout_tail"), "")

    if message:
        return message
    if stderr_tail:
        return stderr_tail
    if stdout_tail:
        return stdout_tail
    return fallback_message


def _get_pipeline_job_snapshot() -> dict:
    with pipeline_job_state_lock:
        return dict(pipeline_job_state)


def _has_active_pipeline_job() -> bool:
    with pipeline_job_state_lock:
        return _safe_str(pipeline_job_state.get("status"), "").lower() in {"queued", "running"}


def _start_pipeline_job(trigger: str, bis_max_pages: int | None = None, sleep_sec: float | None = None,
                        target_date: str | None = None) -> dict:
    with pipeline_job_state_lock:
        current_status = _safe_str(pipeline_job_state.get("status"), "").lower()
        if current_status in {"queued", "running"}:
            return dict(pipeline_job_state)

        pipeline_job_state.clear()
        pipeline_job_state.update({
            "status": "queued",
            "trigger": trigger,
            "started_at": datetime.utcnow().isoformat() + "Z",
        })

    def _worker() -> None:
        with pipeline_job_state_lock:
            pipeline_job_state["status"] = "running"
        try:
            result = run_pipeline(
                trigger=trigger,
                bis_max_pages=bis_max_pages,
                sleep_sec=sleep_sec,
                target_date=target_date,
            )
            with pipeline_job_state_lock:
                pipeline_job_state.update({
                    "status": result.get("status", "unknown"),
                    "finished_at": datetime.utcnow().isoformat() + "Z",
                    "result": _remove_message_fields(result),
                })
        except Exception as exc:
            with pipeline_job_state_lock:
                pipeline_job_state.update({
                    "status": "failed",
                    "finished_at": datetime.utcnow().isoformat() + "Z",
                    "error": str(exc),
                })
            logger.exception("background pipeline job failed")

    Thread(target=_worker, daemon=True).start()
    return _get_pipeline_job_snapshot()


def _resolve_policy_feed_csv_path() -> Path | None:
    for candidate in POLICY_FEED_CANDIDATES:
        candidate_path = Path(candidate)
        if candidate_path.exists():
            return candidate_path
    return None


def _split_value_list(value) -> list[str]:
    if value is None:
        return []
    if isinstance(value, (list, tuple, set)):
        items = list(value)
    else:
        text = str(value).strip()
        if not text:
            return []
        if text.startswith("[") and text.endswith("]"):
            try:
                parsed = json.loads(text)
                if isinstance(parsed, list):
                    items = parsed
                else:
                    items = [text]
            except Exception:
                items = re.split(r"[;,|/]\s*|\n+", text)
        else:
            items = re.split(r"[;,|/]\s*|\n+", text)

    normalized = []
    seen = set()
    for item in items:
        text = _safe_str(item)
        if not text:
            continue
        lower = text.lower()
        if lower in seen:
            continue
        seen.add(lower)
        normalized.append(text)
    return normalized


def _build_news_id(row: pd.Series) -> str:
    seed = "|".join([
        _safe_str(row.get("source"), _safe_str(row.get("category"))),
        _safe_str(row.get("date"), _safe_str(row.get("release_date"))),
        _safe_str(row.get("doc_type")),
        _safe_str(row.get("title")),
        _safe_str(row.get("url"), _safe_str(row.get("link"))),
    ])
    digest = hashlib.sha1(seed.encode("utf-8")).hexdigest()[:12]
    return f"policy-{digest}"


def _build_asset_impact_score(impact: float) -> int:
    if impact >= 0.75:
        return 4
    if impact >= 0.55:
        return 3
    if impact >= 0.35:
        return 2
    return 1


def _build_model_asset_signal(horizon_days: int, predicted_log_return: float, confidence: float, cluster_label: str, global_signal: str) -> dict:
    impact = round(min(1.0, max(0.25, abs(predicted_log_return) * 20.0 + confidence * 0.2)), 2)
    model_payload = {
        "confidence": round(max(0.0, min(0.99, confidence)), 2),
        "horizonDays": int(horizon_days),
        "predictedReturnPct": round(predicted_log_return * 100.0, 2),
        "clusterLabel": cluster_label,
    }
    return {
        "ticker": "QQQ",
        "direction": global_signal,
        "impact": impact,
        "impactScore": _build_asset_impact_score(impact),
        "provenance": "model",
        "model": model_payload,
    }


def _build_keyword_asset_signals(keywords: list[str], source_text: str) -> list[dict]:
    keyword_text = " ".join(keywords + [source_text]).lower()
    rules = [
        {
            "match": ["rate", "rates", "inflation", "hawkish", "tightening", "yield"],
            "signals": [
                {"ticker": "SOXX", "direction": "negative", "impact": 0.65, "impactScore": 3},
                {"ticker": "USD", "direction": "positive", "impact": 0.55, "impactScore": 3},
            ],
        },
        {
            "match": ["cut", "dovish", "easing", "liquidity", "stimulus", "accommodation"],
            "signals": [
                {"ticker": "SOXX", "direction": "positive", "impact": 0.65, "impactScore": 3},
                {"ticker": "USD", "direction": "negative", "impact": 0.55, "impactScore": 3},
            ],
        },
        {
            "match": ["semiconductor", "chip", "semis", "ai", "technology"],
            "signals": [
                {"ticker": "SOXX", "direction": "positive", "impact": 0.7, "impactScore": 3},
            ],
        },
    ]

    asset_signals: list[dict] = []
    seen_tickers = {"QQQ"}

    for rule in rules:
        if not any(term in keyword_text for term in rule["match"]):
            continue
        for signal in rule["signals"]:
            ticker = signal["ticker"]
            if ticker in seen_tickers:
                continue
            seen_tickers.add(ticker)
            asset_signals.append(
                {
                    "ticker": ticker,
                    "direction": signal["direction"],
                    "impact": signal["impact"],
                    "impactScore": signal["impactScore"],
                    "provenance": "keyword_rule",
                    "model": None,
                }
            )

    return asset_signals


def _parse_json_like(value):
    if value is None:
        return None
    if isinstance(value, (dict, list, tuple)):
        return value
    try:
        if pd.isna(value):
            return None
    except Exception:
        pass

    text = str(value).strip()
    if not text:
        return None

    for parser in (json.loads, ast.literal_eval):
        try:
            parsed = parser(text)
            if parsed is not None:
                return parsed
        except Exception:
            continue
    return None


def _extract_asset_signal_tickers(row: pd.Series) -> set[str]:
    tickers: set[str] = set()

    def _collect_from_value(value):
        parsed = _parse_json_like(value)
        if parsed is None:
            return
        signals = parsed.get("assetSignals") if isinstance(parsed, dict) else parsed
        if isinstance(signals, dict):
            signals = [signals]
        if not isinstance(signals, (list, tuple)):
            return
        for signal in signals:
            if not isinstance(signal, dict):
                continue
            ticker = _safe_str(signal.get("ticker"), "").upper()
            if ticker:
                tickers.add(ticker)

    if isinstance(row, pd.Series):
        if "assetSignals" in row:
            _collect_from_value(row.get("assetSignals"))
        if "feature_payload" in row:
            _collect_from_value(row.get("feature_payload"))
    else:
        _collect_from_value(row.get("assetSignals"))
        _collect_from_value(row.get("feature_payload"))

    return tickers


def _resolve_user_asset_names(user_id: int | None) -> list[str]:
    if user_id is None:
        return []
    try:
        asset_names = fetch_user_watch_asset_names(int(user_id))
    except Exception as error:
        logger.warning("[PolicyFeed] failed to resolve watch assets for userId=%s: %s", user_id, error)
        return []

    normalized = []
    seen = set()
    for name in asset_names:
        text = _safe_str(name, "").upper()
        if not text or text in seen:
            continue
        seen.add(text)
        normalized.append(text)
    return normalized


def _filter_policy_feed_by_user_assets(df: pd.DataFrame, user_id: int | None) -> pd.DataFrame:
    if df.empty or user_id is None:
        return df

    user_assets = _resolve_user_asset_names(user_id)
    if not user_assets:
        logger.warning("[PolicyFeed] userId=%s has no resolved watch assets; returning unfiltered feed", user_id)
        return df

    user_asset_set = {asset.upper() for asset in user_assets}

    def _matches(row: pd.Series) -> bool:
        tickers = _extract_asset_signal_tickers(row)
        return bool(tickers.intersection(user_asset_set))

    filtered = df[df.apply(_matches, axis=1)].copy()
    logger.info(
        "[PolicyFeed] filtered by userId=%s assets=%s => %s rows",
        user_id,
        user_assets,
        len(filtered),
    )
    return filtered


def _read_policy_feed_frame(payload: dict, apply_limit: bool = True) -> pd.DataFrame:
    category = _safe_str(payload.get("category"), "all")
    date_from = _safe_str(payload.get("dateFrom"), "")
    date_to = _safe_str(payload.get("dateTo"), "")
    user_id = payload.get("userId")
    limit = int(payload.get("limit") or 20)
    db_limit = None if user_id is not None else (limit if apply_limit else None)

    try:
        db_frame = fetch_policy_feed_frame(
            category=category,
            date_from=date_from,
            date_to=date_to,
            limit=db_limit,
        )
        if not db_frame.empty:
            logger.info("[PolicyFeed] using database-backed policy feed: %s rows", len(db_frame))
            frame = db_frame
        else:
            frame = pd.DataFrame()
    except Exception as error:
        logger.warning("[PolicyFeed] database feed lookup failed: %s", error)

        frame = pd.DataFrame()

    if frame.empty:
        csv_path = _resolve_policy_feed_csv_path()
        if csv_path is None:
            logger.warning("[PolicyFeed] policy feed csv not found. candidates=%s", [str(path) for path in POLICY_FEED_CANDIDATES])
            return pd.DataFrame()

        if Path(csv_path).stat().st_size == 0:
            logger.warning("[PolicyFeed] policy feed csv is empty: %s", csv_path)
            return pd.DataFrame()

        logger.info("[PolicyFeed] using policy feed csv path: %s", csv_path)
        try:
            frame = pd.read_csv(csv_path)
        except pd.errors.EmptyDataError:
            logger.warning("[PolicyFeed] policy feed csv has no parseable rows: %s", csv_path)
            return pd.DataFrame()
        if frame.empty:
            return frame

    logger.info(f"[PolicyFeed] Initial rows: {len(frame)}, category: {category}, dateFrom: {date_from}, dateTo: {date_to}")
    
    if category.lower() != "all" and "category" in frame.columns:
        frame = frame[frame["category"].astype(str).str.lower() == category.lower()]
        logger.info(f"[PolicyFeed] After category filter: {len(frame)} rows")

    if "date" not in frame.columns:
        if "release_date" in frame.columns:
            frame["date"] = frame["release_date"]
        elif "published_date" in frame.columns:
            frame["date"] = frame["published_date"]
        elif "collected_at" in frame.columns:
            frame["date"] = frame["collected_at"]
        else:
            frame["date"] = ""

    if "link" not in frame.columns and "url" in frame.columns:
        frame["link"] = frame["url"]

    if "date" in frame.columns and (date_from or date_to):
        date_series = pd.to_datetime(frame["date"], errors="coerce")
        if date_from:
            frame = frame[date_series >= pd.to_datetime(date_from, errors="coerce")]
            logger.info(f"[PolicyFeed] After dateFrom filter: {len(frame)} rows")
        if date_to:
            frame = frame[date_series <= pd.to_datetime(date_to, errors="coerce")]
            logger.info(f"[PolicyFeed] After dateTo filter: {len(frame)} rows")

    if user_id is not None and not frame.empty:
        frame = _filter_policy_feed_by_user_assets(frame, user_id)
        if frame.empty:
            logger.info("[PolicyFeed] userId=%s produced no matching policy rows", user_id)

    sort_columns = ["date"]
    sort_orders = [False]
    if "title" in frame.columns:
        sort_columns.append("title")
        sort_orders.append(True)
    elif "body_summary" in frame.columns:
        sort_columns.append("body_summary")
        sort_orders.append(True)

    sorted_df = frame.sort_values(by=sort_columns, ascending=sort_orders, na_position="last")
    if apply_limit and limit > 0:
        sorted_df = sorted_df.head(limit)
    logger.info(f"[PolicyFeed] Final rows: {len(sorted_df)}")
    return sorted_df


def _build_policy_feed_stats(payload: dict) -> dict:
    df = _read_policy_feed_frame(payload, apply_limit=False)
    logger.info(f"[PolicyFeedStats] payload: {payload}")

    if df.empty:
        return {
            "totalItemCount": 0,
            "totalCategoryCount": 0,
            "categories": [],
            "dateCounts": [],
        }

    categories = []
    if "category" in df.columns:
        categories = [
            _safe_str(value)
            for value in df["category"].tolist()
            if _safe_str(value)
        ]
    unique_categories = list(dict.fromkeys(categories))

    if "date" not in df.columns:
        if "release_date" in df.columns:
            df["date"] = df["release_date"]
        elif "published_date" in df.columns:
            df["date"] = df["published_date"]
        elif "collected_at" in df.columns:
            df["date"] = df["collected_at"]
        else:
            df["date"] = ""

    date_frame = df.copy()
    date_frame["date"] = date_frame["date"].astype(str).str.strip()
    date_frame = date_frame[date_frame["date"] != ""]
    if not date_frame.empty:
        date_frame = (
            date_frame.groupby("date", as_index=False)
            .size()
            .rename(columns={"size": "itemCount"})
            .sort_values(by="date", ascending=False)
        )
    else:
        date_frame = pd.DataFrame(columns=["date", "itemCount"])

    limit = int(payload.get("limit") or 0)
    if limit > 0:
        date_frame = date_frame.head(limit)

    return {
        "totalItemCount": int(len(df)),
        "totalCategoryCount": int(len(unique_categories)),
        "categories": unique_categories,
        "dateCounts": [
            {
                "date": _safe_str(row.get("date")),
                "itemCount": int(row.get("itemCount") or 0),
            }
            for _, row in date_frame.iterrows()
        ],
    }


def _build_policy_feed(payload: dict) -> dict:
    limit = int(payload.get("limit") or 20)
    df = _read_policy_feed_frame(payload)
    logger.info(f"[PolicyFeed] _build_policy_feed payload: {payload}")

    metadata = _safe_json_load(MODEL_METADATA_PATH)
    summary = _safe_json_load(TRAINING_SUMMARY_PATH)

    if df.empty:
        return {
            "feedType": "policy_news_with_model_signal",
            "generatedAt": datetime.utcnow().isoformat() + "Z",
            "source": {
                "dataset": "policy_updates_features",
                "modelTarget": "QQQ",
                "modelVersion": summary.get("modelVersion", "policy-rule-v1"),
            },
            "summary": {
                "totalCount": 0,
                "positiveCount": 0,
                "negativeCount": 0,
                "neutralCount": 0,
                "overallSentiment": "neutral",
                "overallSentimentScore": 0.0,
            },
            "model": {
                "targetTicker": metadata.get("target_ticker", "QQQ"),
                "bestHorizonDays": summary.get("bestHorizonDays", 15),
                "bestFeatures": metadata.get("best_features", []),
                "metrics": summary.get("metrics", {}),
            },
            "cards": [],
        }

    score_col = "body_sentiment_score" if "body_sentiment_score" in df.columns else "title_sentiment_score"
    score_series = df[score_col] if score_col in df.columns else pd.Series([0.0] * len(df), index=df.index)

    positive_count = int((score_series > 0.15).sum())
    negative_count = int((score_series < -0.15).sum())
    neutral_count = int(len(df) - positive_count - negative_count)

    cards = []
    best_horizon = int(summary.get("bestHorizonDays", 15) or 15)
    threshold = float(summary.get("bestThreshold", 0.004) or 0.004)
    metrics = summary.get("metrics") or {}
    cluster_prediction = summary.get("clusterPrediction") or {}
    predicted_log_return = _safe_float(metrics.get("policyScore"), 0.0)
    confidence = _safe_float(metrics.get("topLabelProbability"), _safe_float(metrics.get("directionAccuracy"), 0.6))
    cluster_top_label = _safe_str(cluster_prediction.get("topLabel"), "flat")

    if predicted_log_return > threshold:
        global_signal = "buy"
    elif predicted_log_return < -threshold:
        global_signal = "sell"
    else:
        global_signal = "hold"

    for idx, row in df.head(limit).iterrows():
        row_source = _safe_str(row.get("source"), _safe_str(row.get("category")))
        row_category = _safe_str(row.get("category"), row_source)
        row_keywords = _split_value_list(row.get("matched_keyword_groups"))
        row_keyword_terms = _split_value_list(row.get("matched_keywords"))
        row_body = _safe_str(row.get("body_summary"), _safe_str(row.get("body")))
        keyword_signals = _build_keyword_asset_signals(
            keywords=row_keywords + row_keyword_terms,
            source_text=" ".join([row_category, row_source, _safe_str(row.get("title")), row_body]),
        )
        asset_signals = [
            _build_model_asset_signal(
                horizon_days=best_horizon,
                predicted_log_return=predicted_log_return,
                confidence=confidence,
                cluster_label=cluster_top_label,
                global_signal=global_signal,
            ),
            *keyword_signals,
        ]

        cards.append(
            {
                "id": f"card-{idx}",
                "newsId": _build_news_id(row),
                "date": _safe_str(row.get("date"), _safe_str(row.get("release_date"))),
                "source": row_source,
                "category": row_category,
                "docType": _safe_str(row.get("doc_type")),
                "sector": _safe_str(row.get("sector")),
                "title": _safe_str(row.get("title")),
                "bodySummary": _safe_str(row.get("body_summary"), _safe_str(row.get("body"))),
                "thumbnailUrl": _safe_str(row.get("thumbnail_url"), _safe_str(row.get("image_url"))),
                "link": _safe_str(row.get("url"), _safe_str(row.get("link"))),
                "matchedKeywordGroups": row_keywords,
                "matchedKeywords": row_keyword_terms,
                "assetSignals": asset_signals,
                "sentiment": {
                    "titleSentimentScore": _safe_float(row.get("title_sentiment_score", 0.0)),
                    "bodySentimentScore": _safe_float(row.get("body_sentiment_score", 0.0)),
                },
                "modelSignal": {
                    "horizonDays": best_horizon,
                    "predictedLogReturn": round(predicted_log_return, 6),
                    "predictedReturnPct": round(predicted_log_return * 100, 2),
                    "signal": global_signal,
                    "thresholdUsed": threshold,
                    "confidence": round(max(0.5, min(0.99, confidence)), 2),
                    "clusterLabel": cluster_top_label,
                },
                # LLM metadata
                "bodySummarySource": (
                    row.get("body_summary_source")
                    or (row.get("feature_payload") or {}).get("llm_summary_meta", {}).get("status")
                    or "unknown"
                ),
                "llmError": (
                    row.get("llm_error") or (row.get("feature_payload") or {}).get("llm_summary_meta", {}).get("error")
                ),
                "llmSuccess": (
                    True if (
                        (row.get("body_summary_source") or (row.get("feature_payload") or {}).get("llm_summary_meta", {}).get("status") or "").lower() == "ok"
                    ) else False
                ),
                "llmFailureReason": (
                    (row.get("llm_error") or (row.get("feature_payload") or {}).get("llm_summary_meta", {}).get("error"))
                ),
            }
        )

    return {
        "feedType": "policy_news_with_model_signal",
        "generatedAt": datetime.utcnow().isoformat() + "Z",
        "source": {
            "dataset": "policy_updates_features",
            "modelTarget": metadata.get("target_ticker", "QQQ"),
            "modelVersion": summary.get("modelVersion", "policy-rule-v1"),
        },
        "summary": {
            "totalCount": int(len(df)),
            "positiveCount": positive_count,
            "negativeCount": negative_count,
            "neutralCount": neutral_count,
            "overallSentiment": "positive" if _safe_float(score_series.mean()) > 0.15 else ("negative" if _safe_float(score_series.mean()) < -0.15 else "neutral"),
            "overallSentimentScore": round(_safe_float(score_series.mean()), 6),
        },
        "model": {
            "targetTicker": metadata.get("target_ticker", "QQQ"),
            "bestHorizonDays": summary.get("bestHorizonDays", 15),
            "bestFeatures": metadata.get("best_features", []),
            "metrics": summary.get("metrics", {}),
        },
        "cards": cards,
    }


def _send_signal_to_api_server(signal_payload: dict) -> dict:
    if not WEBHOOK_URL:
        return {"success": False, "error": "WEBHOOK_URL is empty"}

    request_body = {
        "eventId": int(datetime.utcnow().timestamp()),
        "keyword": _safe_str(signal_payload.get("signal"), "policy-signal"),
        "source": "data-ml-scheduler",
        "signal": signal_payload,
    }

    request_obj = urllib_request.Request(
        WEBHOOK_URL,
        data=json.dumps(request_body, ensure_ascii=False).encode("utf-8"),
        headers={
            "X-Webhook-Secret": WEBHOOK_SECRET,
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
        method="POST",
    )

    try:
        with urllib_request.urlopen(request_obj, timeout=10) as response:
            return {
                "success": True,
                "status_code": response.status,
                "response_text": response.read().decode("utf-8", errors="replace"),
            }
    except urllib_error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace") if error.fp else str(error)
        return {"success": False, "status_code": error.code, "error": detail}
    except Exception as error:
        return {"success": False, "error": str(error)}


def run_pipeline(trigger: str = "manual", bis_max_pages: int | None = None, sleep_sec: float | None = None, target_date: str | None = None) -> dict:
    if not run_lock.acquire(blocking=False):
        return {
            "status": "busy",
            "message": "이미 다른 작업이 실행 중입니다.",
            "trigger": trigger,
            "executed_at": datetime.utcnow().isoformat() + "Z",
        }

    try:
        # parse optional target_date (ISO YYYY-MM-DD) to pass to crawler
        parsed_target_date = None
        if target_date:
            try:
                # try date.fromisoformat first
                parsed_target_date = date.fromisoformat(str(target_date))
            except Exception:
                try:
                    parsed_target_date = datetime.fromisoformat(str(target_date)).date()
                except Exception:
                    parsed_target_date = None

        crawl_result = run_crawl_now(
            bis_max_pages=bis_max_pages or PIPELINE_BIS_MAX_PAGES,
            sleep_sec=sleep_sec or PIPELINE_SLEEP_SEC,
            target_date=parsed_target_date,
        )

        raw_count = int(crawl_result.get("raw_count") or 0)
        processed_count = int(crawl_result.get("processed_count") or 0)
        skip_prediction = (raw_count <= 0) and (processed_count <= 0)

        if skip_prediction:
            predict_result = {
                "status": "skipped",
                "message": "no policy rows collected; prediction skipped",
                "executed_at": datetime.utcnow().isoformat() + "Z",
            }
        else:
            predict_result = run_prediction_now()

        signal_payload = {
            "trigger": trigger,
            "status": "ok" if predict_result.get("status") in {"success", "skipped"} else "failed",
            "signal": "hold",
            "generatedAt": datetime.utcnow().isoformat() + "Z",
            "details": {
                "crawlStatus": crawl_result.get("status"),
                "predictStatus": predict_result.get("status"),
            },
        }

        if skip_prediction:
            webhook_result = {
                "success": True,
                "status": "skipped",
                "message": "prediction skipped due to empty crawl result; webhook skipped",
            }
        else:
            summary = _safe_json_load(TRAINING_SUMMARY_PATH)
            metrics = summary.get("metrics") or {}
            policy_score = _safe_float(metrics.get("policyScore"), 0.0)
            threshold = float(summary.get("bestThreshold", 0.004) or 0.004)
            if policy_score > threshold:
                signal_payload["signal"] = "buy"
            elif policy_score < -threshold:
                signal_payload["signal"] = "sell"

            webhook_result = _send_signal_to_api_server(signal_payload)

        if crawl_result.get("status") != "success":
            logger.warning("[Pipeline] crawl failed: %s", crawl_result)
        if predict_result.get("status") not in {"success", "skipped"}:
            logger.warning("[Pipeline] prediction failed: %s", predict_result)
        if not webhook_result.get("success"):
            logger.warning("[Pipeline] webhook failed: %s", webhook_result)

        predict_ok = predict_result.get("status") in {"success", "skipped"}
        webhook_ok = bool(webhook_result.get("success"))
        status = "success" if predict_ok else "failed"
        return {
            "status": status,
            "webhook_status": "success" if webhook_ok else "failed",
            "warning": None if webhook_ok else "signal webhook 전송에 실패했습니다.",
            "trigger": trigger,
            "crawl": crawl_result,
            "predict": predict_result,
            "signal": signal_payload,
            "webhook": webhook_result,
            "executed_at": datetime.utcnow().isoformat() + "Z",
        }
    finally:
        run_lock.release()


@app.on_event("startup")
def on_startup():
    global scheduler_instance

    try:
        init_db()
    except Exception as error:
        logger.warning("[Startup] data-ml schema initialization failed: %s", error)

    def _scheduled_job():
        # schedule runs at US/Eastern midnight; pipeline should process the previous day
        try:
            us_tz = ZoneInfo("America/New_York")
            now_us = datetime.now(us_tz)
            target_dt = (now_us.date() - timedelta(days=1))
            target_date_str = target_dt.isoformat()
        except Exception:
            target_date_str = None

        result = run_pipeline(
            trigger="scheduler",
            bis_max_pages=PIPELINE_BIS_MAX_PAGES,
            sleep_sec=PIPELINE_SLEEP_SEC,
            target_date=target_date_str,
        )
        if result.get("status") != "success":
            logger.warning("scheduled pipeline result: %s", result)

    scheduler_instance = build_scheduler(_scheduled_job)

    if RUN_PIPELINE_ON_STARTUP:
        startup_result = run_pipeline(trigger="startup")
        if startup_result.get("status") != "success":
            logger.warning("startup pipeline result: %s", startup_result)


@app.on_event("shutdown")
def on_shutdown():
    global scheduler_instance
    if scheduler_instance is not None and scheduler_instance.running:
        scheduler_instance.shutdown(wait=False)


@app.get(f"{ML_PREFIX}/health")
def health():
    return _success_response(
        {
            "status": "healthy",
            "scheduler_running": bool(scheduler_instance and scheduler_instance.running),
            "timestamp": datetime.utcnow().isoformat() + "Z",
        },
        message="ML 헬스 체크에 성공했습니다.",
    )


@app.get(f"{ML_PREFIX}/llm/health")
def llm_health():
    try:
        client = build_llm_client()
        result = client.health_check()
        if result.get("ok"):
            return _success_response(result, message="LLM 헬스체크에 성공했습니다.")
        return _error_response(message=f"LLM 헬스체크 실패: {result.get('error')}", code="LLM_HEALTH_FAILED", status_code=503)
    except Exception as e:
        return _error_response(message=f"LLM 헬스체크 실패: {e}", code="LLM_HEALTH_FAILED", status_code=503)


@app.post(f"{ML_PREFIX}/crawlers/run")
def run_crawl_endpoint():
    if not run_lock.acquire(blocking=False):
        return _error_response("이미 다른 작업이 실행 중입니다.", code="ML_CRAWL_BUSY", status_code=409)
    try:
        result = run_crawl_now(bis_max_pages=PIPELINE_BIS_MAX_PAGES, sleep_sec=PIPELINE_SLEEP_SEC)
        if result.get("status") == "success":
            return _success_response(_remove_message_fields(result), message="크롤링 실행에 성공했습니다.")
        return _error_response(message="크롤링 실행에 실패했습니다.", code="ML_CRAWL_FAILED", status_code=500)
    finally:
        run_lock.release()


@app.post(f"{ML_PREFIX}/predictions/run")
def run_predict_endpoint():
    if not run_lock.acquire(blocking=False):
        return _error_response("이미 다른 작업이 실행 중입니다.", code="ML_PREDICT_BUSY", status_code=409)
    try:
        result = run_prediction_now()
        if result.get("status") == "success":
            return _success_response(_remove_message_fields(result), message="예측 실행에 성공했습니다.")
        return _error_response(message="예측 실행에 실패했습니다.", code="ML_PREDICT_FAILED", status_code=500)
    finally:
        run_lock.release()


@app.get(f"{ML_PREFIX}/predictions/latest")
def get_predict_result_endpoint():
    summary = _safe_json_load(TRAINING_SUMMARY_PATH)
    if not summary:
        return _error_response("예측 결과가 존재하지 않습니다.", code="ML_PREDICT_RESULT_NOT_FOUND", status_code=404)
    return _success_response(summary, message="예측 연산 결과를 성공적으로 불러왔습니다.")


@app.post(f"{ML_PREFIX}/signal/run")
async def run_signal_endpoint(request: Request, date: str | None = None):
    if not run_lock.acquire(blocking=False):
        return _error_response("이미 다른 작업이 실행 중입니다.", code="ML_SIGNAL_BUSY", status_code=409)
    try:
        try:
            payload = await request.json()
        except Exception:
            payload = {}
        if not isinstance(payload, dict):
            payload = {}
        # pipelines/run 과 동일하게 query `date` 우선
        if date:
            payload = {**payload, "targetDate": date}
        try:
            params = parse_signal_request(payload)
            result = run_signal(**params)
            return _success_response(result, message="시그널 예측에 성공했습니다.")
        except SignalRunnerError as error:
            status_code = 400
            if error.code in {"ML_SIGNAL_TIMEOUT"}:
                status_code = 504
            elif error.code in {"ML_SIGNAL_RESULT_NOT_FOUND", "ML_SIGNAL_FEATURES_NOT_FOUND"}:
                status_code = 404
            elif error.code in {
                "ML_SIGNAL_CONFIG_ERROR",
                "ML_SIGNAL_EXEC_FAILED",
                "ML_SIGNAL_FAILED",
                "ML_SIGNAL_RESULT_INVALID",
                "ML_SIGNAL_CRAWL_FAILED",
                "ML_SIGNAL_FEATURES_BUILD_FAILED",
            }:
                status_code = 500
            message = error.message
            if error.details.get("stderr_tail"):
                message = f"{message} ({_tail_text(error.details.get('stderr_tail'))})"
            return _error_response(
                message=message,
                code=error.code,
                status_code=status_code,
                details=error.details or None,
            )
    finally:
        run_lock.release()


@app.get(f"{ML_PREFIX}/signal/latest")
def get_latest_signal_endpoint(ticker: str = "QQQ"):
    try:
        result = load_latest_signal(ticker=ticker)
        return _success_response(result, message="최신 시그널 조회에 성공했습니다.")
    except SignalRunnerError as error:
        status_code = 404 if error.code == "ML_SIGNAL_RESULT_NOT_FOUND" else 500
        return _error_response(message=error.message, code=error.code, status_code=status_code)


@app.get(f"{ML_PREFIX}/llm/article-insights")
def get_article_insights_endpoint(
    insightDate: str | None = None,
):
    result = article_insight_service.generate_for_date(insightDate)
    if result.get("status") == "empty":
        return _success_response(_remove_message_fields(result), message="기사 데이터가 없어 LLM 생성을 건너뜁니다.")
    return _success_response(_remove_message_fields(result), message="기사 인사이트 조회에 성공했습니다.")


@app.post(f"{ML_PREFIX}/llm/article-insights/rebuild")
async def rebuild_article_insights_endpoint(request: Request):
    try:
        payload = await request.json()
    except Exception:
        payload = {}
    insight_date = payload.get("insightDate") or payload.get("date")
    result = article_insight_service.generate_for_date(insight_date)
    if result.get("status") == "empty":
        return _success_response(_remove_message_fields(result), message="기사 데이터가 없어 재생성을 건너뜁니다.")
    return _success_response(_remove_message_fields(result), message="기사 인사이트 재생성에 성공했습니다.")


@app.get(f"{ML_PREFIX}/llm/home-briefings")
def get_home_briefings_endpoint(
    userId: int,
    briefingDate: str | None = None,
):
    result = home_briefing_service.generate_for_user(userId, briefingDate)
    if result.get("status") == "empty":
        return _success_response(_remove_message_fields(result), message="브리핑 데이터가 없어 LLM 생성을 건너뜁니다.")
    return _success_response(_remove_message_fields(result), message="홈 브리핑 조회에 성공했습니다.")


def _policy_feed_response(payload: dict):
    result = _build_policy_feed(payload or {})

    logger.info(f"[PolicyFeed] Endpoint received payload: {payload}")
    logger.info(f"[PolicyFeed] Response cards count: {len(result.get('cards', []))}")

    return _success_response(_remove_message_fields(result), message="정책 피드 조회에 성공했습니다.")


def _policy_feed_stats_response(payload: dict):
    result = _build_policy_feed_stats(payload or {})

    logger.info(f"[PolicyFeedStats] Endpoint received payload: {payload}")
    logger.info(f"[PolicyFeedStats] totalItemCount: {result.get('totalItemCount', 0)}")

    return _success_response(_remove_message_fields(result), message="정책 피드 통계 조회에 성공했습니다.")


@app.get(f"{ML_PREFIX}/feeds/policy")
def policy_feed_get_endpoint(
    userId: int | None = None,
    limit: int = 20,
    category: str = "all",
    dateFrom: str = "",
    dateTo: str = "",
):
    payload = {
        "userId": userId,
        "limit": limit,
        "category": category,
        "dateFrom": dateFrom,
        "dateTo": dateTo,
    }
    return _policy_feed_response(payload)


@app.get(f"{ML_PREFIX}/feeds/policy/stats")
def policy_feed_stats_get_endpoint(
    userId: int | None = None,
    limit: int = 20,
    category: str = "all",
    dateFrom: str = "",
    dateTo: str = "",
):
    payload = {
        "userId": userId,
        "limit": limit,
        "category": category,
        "dateFrom": dateFrom,
        "dateTo": dateTo,
    }
    return _policy_feed_stats_response(payload)


@app.post(f"{ML_PREFIX}/pipelines/run")
async def signal_endpoint(request: Request, date: str | None = None):
    try:
        payload = await request.json()
    except Exception:
        payload = {}

    # prioritize query param `date` over body fields
    target_date_value = date or payload.get("targetDate") or payload.get("target_date") or payload.get("date")

    trigger = _safe_str(payload.get("source"), "manual")
    current_state = _get_pipeline_job_snapshot()
    if _safe_str(current_state.get("status"), "").lower() in {"queued", "running"}:
        return JSONResponse(
            status_code=202,
            content={
                "isSuccess": True,
                "code": "ML_PIPELINE_ALREADY_RUNNING",
                "message": "이미 실행 중인 파이프라인이 있습니다.",
                "result": _remove_message_fields(current_state),
            },
        )

    current_state = _start_pipeline_job(
        trigger=trigger,
        bis_max_pages=PIPELINE_BIS_MAX_PAGES,
        sleep_sec=PIPELINE_SLEEP_SEC,
        target_date=_safe_str(target_date_value),
    )
    return JSONResponse(
        status_code=202,
        content={
            "isSuccess": True,
            "code": "ML_PIPELINE_QUEUED",
            "message": "파이프라인 작업을 비동기로 등록했습니다.",
            "result": {
                **_remove_message_fields(current_state),
            },
        },
    )


@app.get(f"{ML_PREFIX}/pipelines/job")
def get_pipeline_job_status():
    state = _get_pipeline_job_snapshot()
    if not state:
        return _error_response(message="실행 중인 파이프라인 작업이 없습니다.", code="ML_PIPELINE_NOT_FOUND", status_code=404)
    return _success_response(_remove_message_fields(state), message="파이프라인 작업 상태 조회에 성공했습니다.")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app:app", host="0.0.0.0", port=int(os.getenv("ML_API_PORT", "9000")), reload=False)
