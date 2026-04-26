from __future__ import annotations

import json
import logging
import os
from datetime import datetime
from pathlib import Path
from threading import Lock
from urllib import error as urllib_error
from urllib import request as urllib_request

import pandas as pd
from dotenv import load_dotenv
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, Response

from crawler.service import run_crawl_now
from scheduler import build_scheduler
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

ML_PREFIX = "/ml"
BASE_DIR = Path(__file__).resolve().parent
TRAINING_DIR = BASE_DIR / "training"
MERGED_FINBERT_PATH = BASE_DIR / "merged_finbert.csv"
MODEL_METADATA_PATH = TRAINING_DIR / "qqq_model_metadata.json"
TRAINING_SUMMARY_PATH = TRAINING_DIR / "qqq_training_summary.json"

WEBHOOK_URL = os.getenv("WEBHOOK_URL", "http://localhost:8080/api/internal/webhook/event")
WEBHOOK_SECRET = os.getenv("WEBHOOK_SECRET", "")
RUN_PIPELINE_ON_STARTUP = os.getenv("RUN_PIPELINE_ON_STARTUP", "false").lower() == "true"

run_lock = Lock()
scheduler_instance = None


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
        "result": {} if result is None else result,
    }
    return Response(
        content=json.dumps(data, ensure_ascii=False, indent=2),
        media_type="application/json",
    )


def _error_response(message="요청에 실패했습니다.", code="FAIL-001", status_code=500):
    data = {
        "isSuccess": False,
        "code": code,
        "message": message,
    }
    return Response(
        status_code=status_code,
        content=json.dumps(data, ensure_ascii=False, indent=2),
        media_type="application/json",
    )


def _remove_message_fields(value):
    if isinstance(value, dict):
        return {k: _remove_message_fields(v) for k, v in value.items() if k != "message"}
    if isinstance(value, list):
        return [_remove_message_fields(item) for item in value]
    return value


def _read_policy_feed_frame(payload: dict) -> pd.DataFrame:
    if not MERGED_FINBERT_PATH.exists():
        return pd.DataFrame()

    df = pd.read_csv(MERGED_FINBERT_PATH)
    if df.empty:
        return df

    category = _safe_str(payload.get("category"), "all")
    date_from = _safe_str(payload.get("dateFrom"), "")
    date_to = _safe_str(payload.get("dateTo"), "")

    if category.lower() != "all" and "category" in df.columns:
        df = df[df["category"].astype(str).str.lower() == category.lower()]

    if "date" in df.columns and (date_from or date_to):
        date_series = pd.to_datetime(df["date"], errors="coerce")
        if date_from:
            df = df[date_series >= pd.to_datetime(date_from, errors="coerce")]
        if date_to:
            df = df[date_series <= pd.to_datetime(date_to, errors="coerce")]

    return df.sort_values(by=["date", "title"], ascending=[False, True], na_position="last")


def _build_policy_feed(payload: dict) -> dict:
    limit = int(payload.get("limit") or 20)
    df = _read_policy_feed_frame(payload)

    metadata = _safe_json_load(MODEL_METADATA_PATH)
    summary = _safe_json_load(TRAINING_SUMMARY_PATH)

    if df.empty:
        return {
            "feedType": "policy_news_with_model_signal",
            "generatedAt": datetime.utcnow().isoformat() + "Z",
            "source": {
                "dataset": "merged_finbert",
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
        cards.append(
            {
                "id": f"card-{idx}",
                "date": _safe_str(row.get("date")),
                "category": _safe_str(row.get("category")),
                "docType": _safe_str(row.get("doc_type")),
                "title": _safe_str(row.get("title")),
                "bodySummary": _safe_str(row.get("body")),
                "link": _safe_str(row.get("link")),
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
            }
        )

    return {
        "feedType": "policy_news_with_model_signal",
        "generatedAt": datetime.utcnow().isoformat() + "Z",
        "source": {
            "dataset": "merged_finbert",
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


def run_pipeline(trigger: str = "manual") -> dict:
    if not run_lock.acquire(blocking=False):
        return {
            "status": "busy",
            "message": "이미 다른 작업이 실행 중입니다.",
            "trigger": trigger,
            "executed_at": datetime.utcnow().isoformat() + "Z",
        }

    try:
        crawl_result = run_crawl_now()
        predict_result = run_prediction_now()

        signal_payload = {
            "trigger": trigger,
            "status": "ok" if predict_result.get("status") == "success" else "failed",
            "signal": "hold",
            "generatedAt": datetime.utcnow().isoformat() + "Z",
            "details": {
                "crawlStatus": crawl_result.get("status"),
                "predictStatus": predict_result.get("status"),
            },
        }

        summary = _safe_json_load(TRAINING_SUMMARY_PATH)
        metrics = summary.get("metrics") or {}
        policy_score = _safe_float(metrics.get("policyScore"), 0.0)
        threshold = float(summary.get("bestThreshold", 0.004) or 0.004)
        if policy_score > threshold:
            signal_payload["signal"] = "buy"
        elif policy_score < -threshold:
            signal_payload["signal"] = "sell"

        webhook_result = _send_signal_to_api_server(signal_payload)

        predict_ok = predict_result.get("status") == "success"
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

    def _scheduled_job():
        result = run_pipeline(trigger="scheduler")
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
        message="데이터-ML 헬스체크에 성공했습니다.",
    )


@app.post(f"{ML_PREFIX}/crawl/run")
def run_crawl_endpoint():
    if not run_lock.acquire(blocking=False):
        return _error_response("이미 다른 작업이 실행 중입니다.", status_code=409)
    try:
        result = run_crawl_now()
        if result.get("status") == "success":
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


@app.get(f"{ML_PREFIX}/predict/result")
def get_predict_result_endpoint():
    summary = _safe_json_load(TRAINING_SUMMARY_PATH)
    if not summary:
        return _error_response("예측 결과가 존재하지 않습니다.", status_code=404)
    return _success_response(summary, message="예측 연산 결과를 성공적으로 불러왔습니다.")


@app.post(f"{ML_PREFIX}/content/policy-feed")
async def policy_feed_endpoint(request: Request):
    try:
        payload = await request.json()
    except Exception:
        payload = {}

    result = _build_policy_feed(payload or {})
    return _success_response(_remove_message_fields(result), message="정책 피드 조회에 성공했습니다.")


@app.post(f"{ML_PREFIX}/signal")
async def signal_endpoint(request: Request):
    try:
        payload = await request.json()
    except Exception:
        payload = {}

    result = run_pipeline(trigger=_safe_str(payload.get("source"), "manual"))
    if result.get("status") == "busy":
        return _error_response("이미 다른 작업이 실행 중입니다.", status_code=409)
    if result.get("status") == "success":
        return _success_response(_remove_message_fields(result), message="외부 신호 기반 파이프라인 실행에 성공했습니다.")
    return _error_response(status_code=500)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app:app", host="0.0.0.0", port=int(os.getenv("ML_API_PORT", "9000")), reload=False)
