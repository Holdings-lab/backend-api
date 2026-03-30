from __future__ import annotations

import os
from datetime import datetime
from threading import Lock
from pathlib import Path

from flask import Flask, jsonify, request
from dotenv import load_dotenv

from crawler.service import run_crawl_now
from scheduler import build_scheduler
from training.service import run_prediction_now

load_dotenv(Path(__file__).resolve().with_name(".env"))

app = Flask(__name__)
run_lock = Lock()


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


scheduler = build_scheduler(run_pipeline)


@app.get("/health")
def health():
    return jsonify(
        {
            "status": "healthy",
            "scheduler_running": scheduler.running,
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }
    )


@app.post("/api/crawl/run")
def run_crawl_endpoint():
    if not run_lock.acquire(blocking=False):
        return jsonify({"status": "busy", "message": "이미 다른 작업이 실행 중입니다."}), 409
    try:
        result = run_crawl_now()
        code = 200 if result.get("status") in {"success", "skipped"} else 500
        return jsonify(result), code
    finally:
        run_lock.release()


@app.post("/api/predict/run")
def run_predict_endpoint():
    if not run_lock.acquire(blocking=False):
        return jsonify({"status": "busy", "message": "이미 다른 작업이 실행 중입니다."}), 409
    try:
        result = run_prediction_now()
        code = 200 if result.get("status") == "success" else 500
        return jsonify(result), code
    finally:
        run_lock.release()


@app.post("/api/signal")
def signal_endpoint():
    """외부 신호 수신 즉시 파이프라인 실행.

    body 예시:
    {
      "type": "now",
      "source": "webhook"
    }
    """
    payload = request.get_json(silent=True) or {}
    result = run_pipeline()
    result["signal"] = payload
    code = 200 if result.get("status") == "success" else 409 if result.get("status") == "busy" else 500
    return jsonify(result), code


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.getenv("ML_API_PORT", "9000")), debug=False)
