from __future__ import annotations

import json
import os
import hashlib
import logging
from collections import Counter
from datetime import datetime
from pathlib import Path
from threading import Lock
from copy import deepcopy
from urllib import error as urllib_error
from urllib import request as urllib_request

import pandas as pd
from dotenv import load_dotenv
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from crawler.service import run_crawl_now
from training.service import run_prediction_now

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
run_lock = Lock()
policy_feed_cache_lock = Lock()
ML_PREFIX = "/ml"
BASE_DIR = Path(__file__).resolve().parent
TRAINING_DIR = BASE_DIR / "training"
MERGED_FINBERT_PATH = BASE_DIR / "merged_finbert.csv"
MODEL_METADATA_PATH = TRAINING_DIR / "qqq_model_metadata.json"
TRAINING_SUMMARY_PATH = TRAINING_DIR / "qqq_training_summary.json"
POLICY_FEED_SNAPSHOT_DIR = BASE_DIR / "cache" / "policy_feed"
policy_feed_cache = {
    "day_key": datetime.utcnow().strftime("%Y-%m-%d"),
    "signal_revision": 0,
    "items": {},
}

POSTGRES_CONFIG = {
    "host": os.getenv("POSTGRES_HOST", "localhost"),
    "port": int(os.getenv("POSTGRES_PORT", "5432")),
    "dbname": os.getenv("POSTGRES_DB", "pwa_db"),
    "user": os.getenv("POSTGRES_USER", "postgres"),
    "password": os.getenv("POSTGRES_PASSWORD", "password"),
}

WEBHOOK_URL = os.getenv("WEBHOOK_URL", "http://localhost:8080/api/internal/webhook/event")
WEBHOOK_SECRET = os.getenv("WEBHOOK_SECRET", "")


def _current_day_key() -> str:
    return datetime.utcnow().strftime("%Y-%m-%d")


def _normalize_policy_feed_payload(payload: dict) -> dict:
    return {
        "limit": int(payload.get("limit") or 20),
        "category": _safe_str(payload.get("category"), "all"),
        "dateFrom": _safe_str(payload.get("dateFrom"), ""),
        "dateTo": _safe_str(payload.get("dateTo"), ""),
        "userId": _safe_int(payload.get("userId"), 1),
    }


def _policy_feed_cache_key(payload: dict) -> str:
    return json.dumps(payload, ensure_ascii=False, sort_keys=True)


def _policy_feed_snapshot_path(payload: dict) -> Path:
    cache_key = _policy_feed_cache_key(payload)
    digest = hashlib.sha256(cache_key.encode("utf-8")).hexdigest()
    return POLICY_FEED_SNAPSHOT_DIR / f"{digest}.json"


def _save_policy_feed_snapshot(payload: dict, result: dict):
    try:
        POLICY_FEED_SNAPSHOT_DIR.mkdir(parents=True, exist_ok=True)
        target_path = _policy_feed_snapshot_path(payload)
        target_path.write_text(
            json.dumps(result, ensure_ascii=False),
            encoding="utf-8",
        )
    except Exception as error:
        logger.warning("policy-feed snapshot save failed: %s", error)


def _load_policy_feed_snapshot(payload: dict):
    try:
        target_path = _policy_feed_snapshot_path(payload)
        if not target_path.exists():
            return None
        return json.loads(target_path.read_text(encoding="utf-8"))
    except Exception as error:
        logger.warning("policy-feed snapshot load failed: %s", error)
        return None


def _invalidate_policy_feed_cache(reason: str):
    with policy_feed_cache_lock:
        policy_feed_cache["signal_revision"] += 1
        policy_feed_cache["items"].clear()
    logger.info("policy-feed cache invalidated: %s", reason)


def _get_cached_policy_feed(payload: dict):
    day_key = _current_day_key()
    cache_key = _policy_feed_cache_key(payload)

    with policy_feed_cache_lock:
        if policy_feed_cache["day_key"] != day_key:
            policy_feed_cache["day_key"] = day_key
            policy_feed_cache["items"].clear()

        entry = policy_feed_cache["items"].get(cache_key)
        if entry is None:
            return None

        if entry.get("signal_revision") != policy_feed_cache["signal_revision"]:
            return None

        return deepcopy(entry.get("result"))


def _set_cached_policy_feed(payload: dict, result: dict):
    cache_key = _policy_feed_cache_key(payload)
    with policy_feed_cache_lock:
        policy_feed_cache["items"][cache_key] = {
            "signal_revision": policy_feed_cache["signal_revision"],
            "result": deepcopy(result),
        }
    _save_policy_feed_snapshot(payload, result)
    _upsert_policy_feed_snapshot_to_postgres(payload, result)


def _upsert_policy_feed_snapshot_to_postgres(payload: dict, result: dict):
    connection = None
    cursor = None
    try:
        import importlib
        psycopg2 = importlib.import_module("psycopg2")

        connection = psycopg2.connect(**POSTGRES_CONFIG)
        cursor = connection.cursor()
        cursor.execute(
            """
            INSERT INTO ml_policy_feed_snapshot (
                feed_limit,
                category,
                date_from,
                date_to,
                user_id,
                result_json,
                generated_at,
                updated_at
            )
            VALUES (%s, %s, %s, %s, %s, %s::jsonb, NOW(), NOW())
            ON CONFLICT (feed_limit, category, date_from, date_to, user_id)
            DO UPDATE SET
                result_json = EXCLUDED.result_json,
                generated_at = NOW(),
                updated_at = NOW()
            """,
            (
                int(payload.get("limit") or 20),
                _safe_str(payload.get("category"), "all"),
                _safe_str(payload.get("dateFrom"), ""),
                _safe_str(payload.get("dateTo"), ""),
                _safe_int(payload.get("userId"), 1),
                json.dumps(result, ensure_ascii=False),
            ),
        )
        connection.commit()
    except Exception as error:
        logger.warning("policy-feed postgres upsert failed: %s", error)
    finally:
        if cursor is not None:
            cursor.close()
        if connection is not None:
            connection.close()


def _save_policy_event_to_postgres(event: dict) -> dict:
    connection = None
    cursor = None
    try:
        import importlib

        psycopg2 = importlib.import_module("psycopg2")

        connection = psycopg2.connect(**POSTGRES_CONFIG)
        cursor = connection.cursor()
        cursor.execute(
            """
            INSERT INTO policy_events (
                title,
                keyword,
                impact_score,
                analysis_summary,
                created_at
            )
            VALUES (%s, %s, %s, %s, %s)
            RETURNING id, title, keyword, impact_score, analysis_summary, created_at
            """,
            (
                _safe_str(event.get("title"), "정책 피드 분석 결과"),
                _safe_str(event.get("keyword"), "policy-feed"),
                float(event.get("impact_score") or 0.0),
                _safe_str(event.get("analysis_summary"), "정책 피드 분석 결과가 저장되었습니다."),
                event.get("created_at") or datetime.utcnow(),
            ),
        )
        row = cursor.fetchone()
        connection.commit()
        if row is None:
            return {}
        columns = [column[0] for column in cursor.description or []]
        return dict(zip(columns, row))
    except Exception as error:
        logger.warning("policy-event postgres insert failed: %s", error)
        return {}
    finally:
        if cursor is not None:
            cursor.close()
        if connection is not None:
            connection.close()


def _send_webhook_to_api_server(event_id: int, keyword: str) -> dict:
    if not WEBHOOK_URL:
        return {
            "success": False,
            "error_type": "MissingWebhookUrl",
            "details": "WEBHOOK_URL is not configured",
        }

    payload = json.dumps({
        "eventId": int(event_id),
        "keyword": _safe_str(keyword, "unknown"),
    }).encode("utf-8")

    request_obj = urllib_request.Request(
        WEBHOOK_URL,
        data=payload,
        headers={
            "X-Webhook-Secret": WEBHOOK_SECRET,
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
        method="POST",
    )

    try:
        with urllib_request.urlopen(request_obj, timeout=10) as response:
            response_text = response.read().decode("utf-8", errors="replace")
            logger.info("policy-feed webhook sent: event_id=%s status=%s", event_id, response.status)
            return {
                "success": True,
                "status_code": response.status,
                "response_text": response_text,
            }
    except urllib_error.HTTPError as error:
        error_text = error.read().decode("utf-8", errors="replace") if error.fp else ""
        logger.warning("policy-feed webhook HTTP error: %s", error)
        return {
            "success": False,
            "error_type": type(error).__name__,
            "status_code": error.code,
            "details": error_text or str(error),
        }
    except Exception as error:
        logger.warning("policy-feed webhook send failed: %s", error)
        return {
            "success": False,
            "error_type": type(error).__name__,
            "details": str(error),
        }


def _build_policy_event_from_feed(payload: dict, feed_result: dict, prediction_result: dict) -> dict:
    cards = feed_result.get("cards") or []
    first_card = cards[0] if cards else {}
    summary = feed_result.get("summary") or {}
    source = feed_result.get("source") or {}
    model = feed_result.get("model") or {}
    impact = (first_card.get("impact") or {}) if isinstance(first_card, dict) else {}

    category = _safe_str(payload.get("category"), "all")
    title = _safe_str(first_card.get("title"), f"{category} 정책 피드 분석 결과")
    keyword = category if category.lower() != "all" else _safe_str(source.get("modelTarget"), "policy-feed")
    overall_sentiment = _safe_str(summary.get("overallSentiment"), "neutral")
    total_count = _safe_int(summary.get("totalCount"), 0)
    best_horizon = _safe_int(model.get("bestHorizonDays"), 0)
    predicted_strength = _safe_int((first_card.get("modelSignal") or {}).get("signalStrength"), 0)
    impact_score = min(
        100,
        max(
            0,
            int(
                round(
                    abs(_safe_float(summary.get("overallSentimentScore"), 0.0) * 80)
                    + predicted_strength * 0.2
                    + _safe_float(impact.get("score"), 0.0) * 0.2
                )
            ),
        ),
    )

    prediction_status = _safe_str(prediction_result.get("status"), "unknown")
    analysis_summary = _safe_str(
        impact.get("reason"),
        f"{total_count}건의 정책 문서를 분석해 {overall_sentiment} 흐름으로 판단했습니다. "
        f"예측 스크립트 상태는 {prediction_status}이며, 기준 horizon은 {best_horizon}일입니다."
    )

    return {
        "title": title,
        "keyword": keyword,
        "impact_score": impact_score,
        "analysis_summary": analysis_summary,
        "created_at": datetime.utcnow(),
    }


def _build_and_cache_policy_feed(payload: dict) -> dict:
    normalized_payload = _normalize_policy_feed_payload(payload)
    cached = _get_cached_policy_feed(normalized_payload)
    if cached is not None:
        return cached

    result = _build_policy_feed(normalized_payload)
    _set_cached_policy_feed(normalized_payload, result)
    return result


def _safe_json_load(path: Path) -> dict:
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return {}


def _success_response(result=None, message="요청에 성공했습니다.", code="SUCCESS-200"):
    return {
        "isSuccess": True,
        "code": code,
        "message": message,
        "result": {} if result is None else result,
    }


def _error_response(message="요청에 실패했습니다.", code="FAIL-001", status_code=500):
    return JSONResponse(
        status_code=status_code,
        content={
            "isSuccess": False,
            "code": code,
            "message": message,
        },
    )


def _remove_message_fields(value):
    if isinstance(value, dict):
        return {k: _remove_message_fields(v) for k, v in value.items() if k != "message"}
    if isinstance(value, list):
        return [_remove_message_fields(item) for item in value]
    return value


def _safe_float(value, default=None):
    try:
        if pd.isna(value):
            return default
        return float(value)
    except Exception:
        return default


def _safe_int(value, default=None):
    try:
        if pd.isna(value):
            return default
        return int(value)
    except Exception:
        return default


def _safe_str(value, default=""):
    if value is None or (isinstance(value, float) and pd.isna(value)):
        return default
    text = str(value).strip()
    return text if text else default


def _make_slug(text: str) -> str:
    slug = _safe_str(text).lower()
    slug = slug.replace("/", "-")
    slug = "".join(ch if ch.isalnum() or ch == "-" else "-" for ch in slug)
    while "--" in slug:
        slug = slug.replace("--", "-")
    return slug.strip("-")


def _classify_sentiment(score: float) -> str:
    if score is None:
        return "neutral"
    if score > 0.15:
        return "positive"
    if score < -0.15:
        return "negative"
    return "neutral"


def _build_target_assets(category: str) -> list[str]:
    normalized = _safe_str(category).lower()
    if normalized == "fomc":
        return ["TLT", "QQQ", "SPY"]
    if normalized == "white house":
        return ["QQQ", "SOXX", "ICLN"]
    return ["QQQ", "TLT", "UUP"]


def _build_feature_drivers(best_features: list[str]) -> list[str]:
    feature_map = {
        "slope_60": "60일 추세가 중장기 방향성을 가장 강하게 보여줍니다.",
        "slope_120": "120일 추세가 장기 추세 전환을 설명합니다.",
        "bb_width": "볼린저 밴드 폭은 변동성 확장을 반영합니다.",
        "vol_20": "20일 변동성은 위험 구간을 구분하는 데 유용합니다.",
        "vol_ratio": "QQQ 변동성이 SPY 대비 얼마나 큰지 보여줍니다.",
        "target_tlt_rel_ret": "QQQ와 장기채의 상대 강도는 리스크 온/오프 판단에 도움이 됩니다.",
        "vix_level": "변동성 지수 수준은 위험회피 국면을 해석하는 핵심 신호입니다.",
        "target_spy_ratio_20": "QQQ의 SPY 대비 상대 강도는 성장주 모멘텀을 나타냅니다.",
    }
    drivers = []
    for feature in best_features[:3]:
        driver = feature_map.get(feature)
        if driver:
            drivers.append(driver)
    if not drivers:
        drivers.append("선택된 핵심 피처를 기준으로 QQQ 방향성을 해석합니다.")
    return drivers


def _summarize_model_signal(sentiment_score: float, best_horizon: int, best_threshold: float, direction_accuracy: float) -> dict:
    confidence = direction_accuracy if direction_accuracy is not None else 0.5
    confidence = max(0.5, min(0.99, float(confidence)))
    predicted_log_return = round((sentiment_score or 0.0) * 0.01 * confidence, 6)
    predicted_return_pct = round(predicted_log_return * 100, 2)

    if predicted_log_return > best_threshold:
        signal = "buy"
    elif predicted_log_return < -best_threshold:
        signal = "sell"
    else:
        signal = "hold"

    signal_strength = int(min(100, max(0, round(abs(predicted_return_pct) * 12 + confidence * 35))))

    return {
        "horizonDays": best_horizon,
        "predictedLogReturn": predicted_log_return,
        "predictedReturnPct": predicted_return_pct,
        "predictedFuturePrice": None,
        "signal": signal,
        "signalStrength": signal_strength,
        "thresholdUsed": best_threshold,
        "confidence": round(confidence, 2),
    }


def _build_policy_feed(payload: dict) -> dict:
    if not MERGED_FINBERT_PATH.exists():
        return {
            "feedType": "policy_news_with_model_signal",
            "generatedAt": datetime.utcnow().isoformat() + "Z",
            "source": {
                "dataset": "merged_finbert",
                "modelTarget": "QQQ",
                "modelVersion": "unknown",
            },
            "summary": {
                "totalCount": 0,
                "positiveCount": 0,
                "negativeCount": 0,
                "neutralCount": 0,
                "overallSentiment": "neutral",
                "overallSentimentScore": 0.0,
                "latestDate": None,
                "topCategories": [],
            },
            "model": {
                "targetTicker": "QQQ",
                "bestHorizonDays": None,
                "bestFeatures": [],
                "metrics": {},
                "thresholdPerformance": [],
                "topFeatureImportance": [],
            },
            "filters": {
                "categories": ["BIS", "FOMC", "White House"],
                "docTypes": ["press_release", "statement", "minutes"],
                "dateRange": {"from": None, "to": None},
                "sentimentRange": {"min": -1, "max": 1},
            },
            "cards": [],
        }

    df = pd.read_csv(MERGED_FINBERT_PATH)
    limit = int(payload.get("limit") or 20)
    category = _safe_str(payload.get("category"), "all")
    date_from = _safe_str(payload.get("dateFrom"), "")
    date_to = _safe_str(payload.get("dateTo"), "")

    if category and category.lower() != "all" and "category" in df.columns:
        df = df[df["category"].astype(str).str.lower() == category.lower()]

    if (date_from or date_to) and "date" in df.columns:
        date_series = pd.to_datetime(df["date"], errors="coerce")
        if date_from:
            df = df[date_series >= pd.to_datetime(date_from, errors="coerce")]
        if date_to:
            df = df[date_series <= pd.to_datetime(date_to, errors="coerce")]

    df = df.sort_values(by=["date", "title"], ascending=[False, True], na_position="last")

    metadata = _safe_json_load(MODEL_METADATA_PATH)
    training_summary = _safe_json_load(TRAINING_SUMMARY_PATH)

    best_features = metadata.get("best_features") or training_summary.get("bestFeatures") or []
    best_horizon = metadata.get("best_horizon") or training_summary.get("bestHorizonDays") or 15
    best_threshold = training_summary.get("bestThreshold")
    if best_threshold is None:
        threshold_performance = training_summary.get("thresholdPerformance") or []
        if threshold_performance:
            best_threshold = threshold_performance[0].get("threshold", 0.004)
        else:
            best_threshold = 0.004

    metrics = training_summary.get("metrics") or {}
    direction_accuracy = metrics.get("directionAccuracy")

    sentiment_score_column = "body_sentiment_score" if "body_sentiment_score" in df.columns else "title_sentiment_score"
    score_series = df[sentiment_score_column] if sentiment_score_column in df.columns else pd.Series([0] * len(df), index=df.index)

    total_count = int(len(df))
    positive_count = int((score_series > 0.15).sum())
    negative_count = int((score_series < -0.15).sum())
    neutral_count = total_count - positive_count - negative_count
    overall_sentiment_score = _safe_float(score_series.mean(), 0.0)

    top_categories = []
    if "category" in df.columns and total_count > 0:
        category_counts = Counter(df["category"].astype(str))
        top_categories = [
            {"category": name, "count": int(count)}
            for name, count in category_counts.most_common(5)
        ]

    latest_date = None
    if "date" in df.columns and not df.empty:
        latest_date = _safe_str(df["date"].dropna().astype(str).iloc[0], None)

    cards = []
    for _, row in df.head(limit).iterrows():
        title_score = _safe_float(row.get("title_sentiment_score"), 0.0)
        body_score = _safe_float(row.get("body_sentiment_score"), title_score)
        combined_score = body_score if body_score is not None else title_score
        impact_label = _classify_sentiment(combined_score)
        model_signal = _summarize_model_signal(combined_score or 0.0, int(best_horizon), float(best_threshold), direction_accuracy or 0.5)
        cards.append(
            {
                "id": f"{_safe_str(row.get('category'))}_{_safe_str(row.get('date'))}_{_make_slug(row.get('title'))}",
                "date": _safe_str(row.get("date")),
                "category": _safe_str(row.get("category")),
                "docType": _safe_str(row.get("doc_type")),
                "title": _safe_str(row.get("title")),
                "bodySummary": _safe_str(row.get("body")),
                "bodyExcerpt": _safe_str(row.get("body"))[:240],
                "link": _safe_str(row.get("link")),
                "bodyOriginalLength": _safe_int(row.get("body_original_length"), 0),
                "bodyNChunks": _safe_int(row.get("body_n_chunks"), 0),
                "tags": [
                    _safe_str(row.get("category")),
                    _classify_sentiment(combined_score),
                ],
                "temporal": {
                    "dayOfWeek": _safe_str(row.get("day_of_week")),
                    "month": _safe_int(row.get("month"), None),
                    "isWeekend": bool(row.get("is_weekend")) if "is_weekend" in row else False,
                },
                "sentiment": {
                    "titlePositiveProb": _safe_float(row.get("title_positive_prob"), 0.0),
                    "titleNegativeProb": _safe_float(row.get("title_negative_prob"), 0.0),
                    "titleNeutralProb": _safe_float(row.get("title_neutral_prob"), 0.0),
                    "titleSentimentScore": title_score,
                    "bodyPositiveProb": _safe_float(row.get("body_positive_prob"), 0.0),
                    "bodyNegativeProb": _safe_float(row.get("body_negative_prob"), 0.0),
                    "bodyNeutralProb": _safe_float(row.get("body_neutral_prob"), 0.0),
                    "bodySentimentScore": body_score,
                },
                "modelSignal": {
                    **model_signal,
                    "predictedFuturePrice": None,
                },
                "impact": {
                    "label": impact_label,
                    "score": int(min(100, max(0, round(abs(combined_score or 0.0) * 100)))) ,
                    "reason": f"{_safe_str(row.get('category'))} 문서의 감성과 모델 피드가 결합된 해석입니다.",
                    "targetAssets": _build_target_assets(row.get("category")),
                },
                "features": {
                    "matchedFeatures": list(best_features[:3]),
                    "featureDrivers": _build_feature_drivers(best_features),
                },
            }
        )

    top_feature_importance = []
    training_top_features = training_summary.get("topFeatureImportance") or []
    if training_top_features:
        top_feature_importance = training_top_features
    elif best_features:
        top_feature_importance = [
            {"rank": idx + 1, "feature": feature, "importance": round(1.0 / (idx + 1), 3)}
            for idx, feature in enumerate(best_features[:5])
        ]

    return {
        "feedType": "policy_news_with_model_signal",
        "generatedAt": datetime.utcnow().isoformat() + "Z",
        "source": {
            "dataset": "merged_finbert",
            "modelTarget": metadata.get("target_ticker", "QQQ"),
            "modelVersion": training_summary.get("modelVersion", "qqq-xgb"),
        },
        "summary": {
            "totalCount": total_count,
            "positiveCount": positive_count,
            "negativeCount": negative_count,
            "neutralCount": neutral_count,
            "overallSentiment": _classify_sentiment(overall_sentiment_score),
            "overallSentimentScore": round(overall_sentiment_score or 0.0, 4),
            "latestDate": latest_date,
            "topCategories": top_categories,
        },
        "model": {
            "targetTicker": metadata.get("target_ticker", "QQQ"),
            "bestHorizonDays": int(best_horizon) if best_horizon is not None else None,
            "bestFeatures": best_features,
            "metrics": metrics,
            "thresholdPerformance": training_summary.get("thresholdPerformance") or [],
            "topFeatureImportance": top_feature_importance,
        },
        "filters": {
            "categories": ["BIS", "FOMC", "White House"],
            "docTypes": ["press_release", "statement", "minutes"],
            "dateRange": {
                "from": _safe_str(payload.get("dateFrom"), None),
                "to": _safe_str(payload.get("dateTo"), None),
            },
            "sentimentRange": {
                "min": -1,
                "max": 1,
            },
        },
        "cards": cards,
    }


def _empty_policy_feed_response(payload: dict, status: str = "warming") -> dict:
    return {
        "feedType": "policy_news_with_model_signal",
        "generatedAt": datetime.utcnow().isoformat() + "Z",
        "status": status,
        "source": {
            "dataset": "merged_finbert",
            "modelTarget": "QQQ",
            "modelVersion": "qqq-xgb",
        },
        "summary": {
            "totalCount": 0,
            "positiveCount": 0,
            "negativeCount": 0,
            "neutralCount": 0,
            "overallSentiment": "neutral",
            "overallSentimentScore": 0.0,
            "latestDate": None,
            "topCategories": [],
        },
        "model": {
            "targetTicker": "QQQ",
            "bestHorizonDays": None,
            "bestFeatures": [],
            "metrics": {},
            "thresholdPerformance": [],
            "topFeatureImportance": [],
        },
        "filters": {
            "categories": ["BIS", "FOMC", "White House"],
            "docTypes": ["press_release", "statement", "minutes"],
            "dateRange": {
                "from": _safe_str(payload.get("dateFrom"), None),
                "to": _safe_str(payload.get("dateTo"), None),
            },
            "sentimentRange": {
                "min": -1,
                "max": 1,
            },
        },
        "cards": [],
    }


def run_pipeline() -> dict:
    if not run_lock.acquire(blocking=False):
        return {
            "status": "busy",
            "message": "이미 다른 작업이 실행 중입니다.",
            "executed_at": datetime.utcnow().isoformat() + "Z",
        }

    try:
        crawl_result = run_crawl_now()
        predict_result = run_prediction_now()

        status = "success" if predict_result.get("status") == "success" else "failed"
        return {
            "status": status,
            "crawl": crawl_result,
            "predict": predict_result,
            "executed_at": datetime.utcnow().isoformat() + "Z",
        }
    finally:
        run_lock.release()


@app.get(f"{ML_PREFIX}/health")
def health():
    return _success_response(
        {
            "status": "healthy",
            "scheduler_running": False,
            "timestamp": datetime.utcnow().isoformat() + "Z",
        },
        message="데이터-ML 헬스체크에 성공했습니다.",
    )


@app.post(f"{ML_PREFIX}/crawl/run")
def run_crawl_endpoint():
    if not run_lock.acquire(blocking=False):
        return _error_response("이미 다른 작업이 실행 중입니다.", status_code=409)
    try:
        result = run_crawl_now()
        if result.get("status") in {"success", "skipped"}:
            return _success_response(_remove_message_fields(result), message="크롤링 실행에 성공했습니다.")
        return _error_response(status_code=500)
    finally:
        run_lock.release()


@app.post(f"{ML_PREFIX}/predict/run")
def run_predict_endpoint():
    if not run_lock.acquire(blocking=False):
        return _error_response("이미 다른 작업이 실행 중입니다.", status_code=409)
    try:
        result = run_prediction_now()
        if result.get("status") == "success":
            return _success_response(_remove_message_fields(result), message="예측 실행에 성공했습니다.")
        return _error_response(status_code=500)
    finally:
        run_lock.release()


@app.post(f"{ML_PREFIX}/content/policy-feed")
async def policy_feed_endpoint(request: Request):
    try:
        request_body = await request.json()
    except Exception:
        request_body = {}

    payload = _normalize_policy_feed_payload(request_body or {})
    if not run_lock.acquire(blocking=False):
        return _error_response("이미 다른 작업이 실행 중입니다.", status_code=409)

    try:
        try:
            prediction_result = run_prediction_now()
            if prediction_result.get("status") != "success":
                logger.warning("policy-feed prediction failed: %s", prediction_result)
                return _error_response(status_code=500)

            policy_feed_result = _build_policy_feed(payload)
            _set_cached_policy_feed(payload, policy_feed_result)

            policy_event = _build_policy_event_from_feed(payload, policy_feed_result, prediction_result)
            saved_event = _save_policy_event_to_postgres(policy_event)
            if saved_event.get("id") is not None:
                _send_webhook_to_api_server(int(saved_event["id"]), saved_event.get("keyword", policy_event["keyword"]))

            return _success_response(
                _remove_message_fields(policy_feed_result),
                message="정책 피드 조회에 성공했습니다.",
            )
        except Exception as error:
            logger.exception("policy-feed request failed: %s", error)
            return _error_response(status_code=500)
    finally:
        run_lock.release()


@app.post(f"{ML_PREFIX}/signal")
async def signal_endpoint(request: Request):
    """외부 신호 수신 즉시 파이프라인 실행.

    body 예시:
    {
      "type": "now",
      "source": "webhook"
    }
    """
    try:
        payload = await request.json()
    except Exception:
        payload = {}

    result = run_pipeline()
    result["signal"] = payload
    if result.get("status") == "success":
        return _success_response(_remove_message_fields(result), message="외부 신호 기반 파이프라인 실행에 성공했습니다.")
    if result.get("status") == "busy":
        return _error_response("이미 다른 작업이 실행 중입니다.", status_code=409)
    return _error_response(status_code=500)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app:app", host="0.0.0.0", port=int(os.getenv("ML_API_PORT", "9000")), reload=False)
