from __future__ import annotations

import json
import logging
import os
import re
import subprocess
import sys
from datetime import date, datetime
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

DEFAULT_ML_WORKER_ROOT = Path("/opt/riseai/apps/ml-worker")
DEFAULT_PREDICTIONS_DIR = Path("/opt/riseai/data/predictions")
DEFAULT_FEATURES_ROOT = Path("/opt/riseai/data/features")
DEFAULT_SIGNAL_TICKERS = ("QQQ", "XLE", "XLF", "XLV")
DEFAULT_TIMEOUT_SEC = 120


class SignalRunnerError(Exception):
    def __init__(self, message: str, *, code: str = "ML_SIGNAL_FAILED", details: dict[str, Any] | None = None):
        super().__init__(message)
        self.message = message
        self.code = code
        self.details = details or {}


def _ticker_slug(ticker: str) -> str:
    return ticker.strip().lower().replace("^", "").replace("/", "_").replace("-", "_")


def _ml_worker_root() -> Path:
    return Path(os.getenv("ML_WORKER_ROOT", str(DEFAULT_ML_WORKER_ROOT)))


def _in_docker() -> bool:
    return Path("/.dockerenv").exists()


def _ml_worker_python(worker_root: Path) -> str:
    configured = (os.getenv("ML_WORKER_PYTHON") or "").strip()
    if _in_docker():
        if configured and ".venv" in configured.replace("\\", "/"):
            logger.warning(
                "[Signal] Ignoring host venv python inside Docker (%s); using container python",
                configured,
            )
        return sys.executable

    if configured:
        if configured in {"python", "python3"}:
            return configured
        return configured

    venv_python = worker_root / ".venv" / "bin" / "python"
    if venv_python.exists():
        return str(venv_python)
    return sys.executable


def _predict_env(worker_root: Path) -> dict[str, str]:
    env = os.environ.copy()
    existing = env.get("PYTHONPATH", "")
    worker = str(worker_root)
    env["PYTHONPATH"] = worker if not existing else f"{worker}{os.pathsep}{existing}"
    return env


def _predictions_dir() -> Path:
    return DEFAULT_PREDICTIONS_DIR


def _features_root() -> Path:
    return DEFAULT_FEATURES_ROOT


def _default_output_path(ticker: str) -> Path:
    # /opt/riseai/data/predictions/{qqq|xle|xlf|xlv}_latest_signal.json
    return _predictions_dir() / f"{_ticker_slug(ticker)}_latest_signal.json"


def _news_features_path(ticker: str = "QQQ") -> Path:
    # /opt/riseai/data/features/{ticker}/news_event_features.csv
    return _features_root() / _ticker_slug(ticker) / "news_event_features.csv"


def _market_features_path(ticker: str = "QQQ") -> Path:
    # /opt/riseai/data/features/{ticker}/market_long_features.csv
    return _features_root() / _ticker_slug(ticker) / "market_long_features.csv"


def _timeout_sec() -> int:
    raw = os.getenv("SIGNAL_TIMEOUT_SEC", str(DEFAULT_TIMEOUT_SEC))
    try:
        return max(1, int(raw))
    except ValueError:
        return DEFAULT_TIMEOUT_SEC


def _tail(text: str, limit: int = 4000) -> str:
    if not text:
        return ""
    if len(text) <= limit:
        return text
    return text[-limit:]


def _parse_target_date(value: Any) -> date | None:
    if value is None:
        return None
    text = str(value).strip()
    if not text:
        return None
    try:
        return date.fromisoformat(text)
    except Exception:
        try:
            return datetime.fromisoformat(text).date()
        except Exception:
            raise SignalRunnerError(
                f"targetDate 형식이 올바르지 않습니다: {value}",
                code="ML_SIGNAL_BAD_REQUEST",
            ) from None


def _parse_bool(value: Any, default: bool = False) -> bool:
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    text = str(value).strip().lower()
    if text in {"1", "true", "yes", "y", "on"}:
        return True
    if text in {"0", "false", "no", "n", "off"}:
        return False
    return default


def _read_signal_json(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise SignalRunnerError(
            f"시그널 결과 파일이 없습니다: {path}",
            code="ML_SIGNAL_RESULT_NOT_FOUND",
        )
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except Exception as error:
        raise SignalRunnerError(
            f"시그널 결과 JSON을 읽지 못했습니다: {path}",
            code="ML_SIGNAL_RESULT_INVALID",
            details={"error": str(error)},
        ) from error
    if not isinstance(payload, dict):
        raise SignalRunnerError(
            "시그널 결과 JSON 형식이 올바르지 않습니다.",
            code="ML_SIGNAL_RESULT_INVALID",
        )
    return payload


def load_latest_signal(ticker: str = "QQQ") -> dict[str, Any]:
    return _read_signal_json(_default_output_path(ticker))


def prepare_features_existing(ticker: str = "QQQ") -> dict[str, Any]:
    news_features_path = _news_features_path(ticker)
    market_features_path = _market_features_path(ticker)
    if not news_features_path.exists():
        raise SignalRunnerError(
            f"뉴스 feature 파일이 없습니다: {news_features_path}",
            code="ML_SIGNAL_FEATURES_NOT_FOUND",
        )
    if not market_features_path.exists():
        raise SignalRunnerError(
            f"시장 feature 파일이 없습니다: {market_features_path}",
            code="ML_SIGNAL_FEATURES_NOT_FOUND",
        )
    logger.info(
        "[Signal] using existing feature CSVs ticker=%s news=%s market=%s",
        ticker,
        news_features_path,
        market_features_path,
    )
    return {
        "ticker": _ticker_slug(ticker).upper(),
        "news_features_path": str(news_features_path),
        "market_features_path": str(market_features_path),
    }


def prepare_features_from_crawl(
    *,
    target_date: date | str | None = None,
    bis_max_pages: int | None = None,
    sleep_sec: float | None = None,
    ticker: str = "QQQ",
) -> dict[str, Any]:
    """
    1) env 의 policy_monitor.py 실행 (CSV 출력은 해당 스크립트가 담당)
    2) 티커별 news_event/market_long feature CSV 경로를 반환
    """
    from crawler.external import ExternalCrawlerError, run_apps_crawler_policy_monitor

    parsed_target_date = target_date if isinstance(target_date, date) else _parse_target_date(target_date)

    try:
        run_apps_crawler_policy_monitor(
            target_date=parsed_target_date,
            bis_max_pages_override=bis_max_pages,
            sleep_sec=sleep_sec,
        )
    except ExternalCrawlerError as error:
        raise SignalRunnerError(error.message, code=error.code, details=error.details) from error

    return prepare_features_existing(ticker=ticker)


def prepare_features(
    *,
    refresh_features: bool = True,
    target_date: date | str | None = None,
    bis_max_pages: int | None = None,
    sleep_sec: float | None = None,
    ticker: str = "QQQ",
) -> dict[str, Any]:
    """
    feature 준비 진입점.
    - 기본(True): 크롤 후 티커별 feature CSV 경로 반환
    - False: 기존 고정 CSV만 검증/사용
    """
    if bool(refresh_features):
        return prepare_features_from_crawl(
            target_date=target_date,
            bis_max_pages=bis_max_pages,
            sleep_sec=sleep_sec,
            ticker=ticker,
        )
    return prepare_features_existing(ticker=ticker)


def run_signal(
    ticker: str = "QQQ",
    *,
    refresh_features: bool = True,
    target_date: date | str | None = None,
    bis_max_pages: int | None = None,
    sleep_sec: float | None = None,
) -> dict[str, Any]:
    normalized_ticker = (ticker or "QQQ").strip().upper() or "QQQ"

    feature_prep = prepare_features(
        refresh_features=refresh_features,
        target_date=target_date,
        bis_max_pages=bis_max_pages,
        sleep_sec=sleep_sec,
        ticker=normalized_ticker,
    )

    worker_root = _ml_worker_root()
    python_bin = _ml_worker_python(worker_root)
    script_path = worker_root / "shared" / "predict_signal.py"
    output_path = _default_output_path(normalized_ticker)
    news_features_path = Path(feature_prep["news_features_path"])
    market_features_path = Path(feature_prep["market_features_path"])

    python_path = Path(python_bin)
    if python_bin not in {"python", "python3"} and not python_path.exists():
        raise SignalRunnerError(
            f"ml-worker Python 을 찾을 수 없습니다: {python_bin}",
            code="ML_SIGNAL_CONFIG_ERROR",
        )
    if not script_path.exists():
        raise SignalRunnerError(
            f"predict_signal.py 를 찾을 수 없습니다: {script_path}",
            code="ML_SIGNAL_CONFIG_ERROR",
        )
    if not news_features_path.exists():
        raise SignalRunnerError(
            f"뉴스 feature 파일이 없습니다: {news_features_path}",
            code="ML_SIGNAL_FEATURES_NOT_FOUND",
        )
    if not market_features_path.exists():
        raise SignalRunnerError(
            f"시장 feature 파일이 없습니다: {market_features_path}",
            code="ML_SIGNAL_FEATURES_NOT_FOUND",
        )

    command = [
        python_bin,
        "-B",
        str(script_path),
        "--ticker",
        normalized_ticker,
        "--news-features",
        str(news_features_path),
        "--market-features",
        str(market_features_path),
        "--output",
        str(output_path),
    ]

    output_path.parent.mkdir(parents=True, exist_ok=True)
    logger.info("[Signal] running: %s", " ".join(command))

    try:
        completed = subprocess.run(
            command,
            cwd=str(worker_root),
            env=_predict_env(worker_root),
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=_timeout_sec(),
            check=False,
        )
    except subprocess.TimeoutExpired as error:
        raise SignalRunnerError(
            f"시그널 예측이 {_timeout_sec()}초를 초과했습니다.",
            code="ML_SIGNAL_TIMEOUT",
            details={
                "stdout_tail": _tail(error.stdout or ""),
                "stderr_tail": _tail(error.stderr or ""),
            },
        ) from error
    except OSError as error:
        raise SignalRunnerError(
            f"시그널 프로세스를 실행하지 못했습니다: {error}",
            code="ML_SIGNAL_EXEC_FAILED",
        ) from error

    if completed.returncode != 0:
        details = {
            "exit_code": completed.returncode,
            "stdout_tail": _tail(completed.stdout),
            "stderr_tail": _tail(completed.stderr),
            "command": command,
        }
        logger.error(
            "[Signal] predict_signal failed exit=%s stderr_tail=%s stdout_tail=%s",
            completed.returncode,
            _tail(completed.stderr, 2000),
            _tail(completed.stdout, 1000),
        )
        raise SignalRunnerError(
            "시그널 예측 실행에 실패했습니다.",
            code="ML_SIGNAL_FAILED",
            details=details,
        )

    signal = _read_signal_json(output_path)
    signal.setdefault("ticker", normalized_ticker)
    return signal


def signal_to_prediction_summary(signal: dict[str, Any] | None, ticker: str = "QQQ") -> dict[str, Any]:
    """predict_signal.json 을 AI briefing / webhook 이 쓰는 summary 형태로 맞춘다."""
    payload = dict(signal or {})
    ticker_upper = (ticker or payload.get("ticker") or "QQQ")
    ticker_upper = str(ticker_upper).strip().upper() or "QQQ"

    direction = str(
        payload.get("signal")
        or payload.get("direction")
        or (payload.get("prediction") or {}).get("direction")
        or "hold"
    ).strip().lower()
    if direction in {"up", "buy", "long"}:
        direction = "buy"
    elif direction in {"down", "sell", "short"}:
        direction = "sell"
    else:
        direction = "hold"

    predicted_return_pct = payload.get("predictedReturnPct")
    if predicted_return_pct is None:
        predicted_return_pct = (payload.get("prediction") or {}).get("expected_return_pct")
    if predicted_return_pct is None:
        predicted_return_pct = (payload.get("metrics") or {}).get("predictedReturnPct")
    try:
        predicted_return_pct = float(predicted_return_pct or 0.0)
    except Exception:
        predicted_return_pct = 0.0

    policy_score = predicted_return_pct / 100.0
    confidence = payload.get("confidence")
    if confidence is None:
        confidence = (payload.get("metrics") or {}).get("confidence")
    try:
        confidence = float(confidence if confidence is not None else 0.6)
    except Exception:
        confidence = 0.6

    horizon_days = payload.get("horizonDays") or payload.get("bestHorizonDays") or 15
    try:
        horizon_days = int(horizon_days)
    except Exception:
        horizon_days = 15

    generated_at = str(payload.get("generatedAt") or datetime.utcnow().isoformat() + "Z")
    as_of = (
        payload.get("as_of_date")
        or payload.get("asOfDate")
        or payload.get("as-of-date")
        or payload.get("prediction_date")
        or payload.get("predictionDate")
    )
    as_of_date = _parse_target_date(as_of)
    if as_of_date is None:
        as_of_date = _parse_target_date(generated_at)
    if as_of_date is None:
        as_of_date = datetime.utcnow().date()

    return {
        "modelVersion": str(payload.get("modelVersion") or "predict-signal-v1"),
        "targetTicker": ticker_upper,
        "bestHorizonDays": horizon_days,
        "bestThreshold": 0.004,
        "as_of_date": as_of_date.isoformat(),
        "prediction_date": as_of_date.isoformat(),
        "metrics": {
            "policyScore": policy_score,
            "topLabelProbability": confidence,
            "directionAccuracy": confidence,
            "predictedReturnPct": predicted_return_pct,
            "topLabel": direction,
        },
        "clusterPrediction": {
            "topLabel": direction,
            "topProbability": confidence,
        },
        "signal": direction,
        "generatedAt": generated_at,
        "rawSignal": payload,
    }


def run_signals_for_tickers(
    tickers: list[str] | tuple[str, ...] | None = None,
    *,
    refresh_features: bool = False,
    target_date: date | str | None = None,
    bis_max_pages: int | None = None,
    sleep_sec: float | None = None,
) -> dict[str, Any]:
    """
    QQQ/XLE/XLF 등 여러 티커에 대해 predict_signal.py 를 순차 실행한다.
    refresh_features=True 이면 크롤은 1회만 수행한다.
    """
    ticker_list = [
        str(t).strip().upper()
        for t in (tickers or DEFAULT_SIGNAL_TICKERS)
        if str(t).strip()
    ]
    if not ticker_list:
        ticker_list = list(DEFAULT_SIGNAL_TICKERS)

    if refresh_features:
        # 크롤은 공유 1회, feature 경로는 티커별로 다름
        prepare_features_from_crawl(
            target_date=target_date,
            bis_max_pages=bis_max_pages,
            sleep_sec=sleep_sec,
            ticker=ticker_list[0],
        )

    by_ticker: dict[str, Any] = {}
    errors: list[dict[str, str]] = []
    for ticker in ticker_list:
        try:
            by_ticker[ticker] = run_signal(
                ticker,
                refresh_features=False,
                target_date=target_date,
                bis_max_pages=bis_max_pages,
                sleep_sec=sleep_sec,
            )
        except SignalRunnerError as error:
            logger.warning("[Signal] ticker=%s failed: %s", ticker, error.message)
            errors.append({"ticker": ticker, "error": error.message, "code": error.code})
        except Exception as error:
            logger.warning("[Signal] ticker=%s failed: %s", ticker, error)
            errors.append({"ticker": ticker, "error": str(error), "code": "ML_SIGNAL_FAILED"})

    stored_count = len(by_ticker)
    if stored_count == 0:
        status = "failed"
    elif errors:
        status = "partial"
    else:
        status = "success"

    return {
        "status": status,
        "tickers": ticker_list,
        "stored_count": stored_count,
        "byTicker": by_ticker,
        "errors": errors,
        "executed_at": datetime.utcnow().isoformat() + "Z",
    }


def parse_signal_request(payload: dict[str, Any] | None) -> dict[str, Any]:
    body = payload or {}

    raw_tickers = body.get("tickers") or body.get("Tickers")
    ticker = body.get("ticker") or body.get("Ticker")
    tickers: list[str] | None = None
    if isinstance(raw_tickers, str) and raw_tickers.strip():
        if raw_tickers.strip().lower() in {"all", "*"}:
            tickers = list(DEFAULT_SIGNAL_TICKERS)
        else:
            tickers = [part.strip().upper() for part in raw_tickers.split(",") if part.strip()]
    elif isinstance(raw_tickers, (list, tuple)):
        tickers = [str(part).strip().upper() for part in raw_tickers if str(part).strip()]
    elif isinstance(ticker, str) and ticker.strip():
        if ticker.strip().lower() in {"all", "*"}:
            tickers = list(DEFAULT_SIGNAL_TICKERS)
        else:
            tickers = [ticker.strip().upper()]

    if not tickers:
        tickers = list(DEFAULT_SIGNAL_TICKERS)

    for item in tickers:
        if not re.fullmatch(r"[A-Za-z0-9^._-]{1,32}", item):
            raise SignalRunnerError("ticker 형식이 올바르지 않습니다.", code="ML_SIGNAL_BAD_REQUEST")

    target_date = body.get("targetDate") or body.get("target_date") or body.get("date")
    _parse_target_date(target_date)

    refresh_features = _parse_bool(
        body.get("refreshFeatures", body.get("refresh_features")),
        default=False,
    )

    params: dict[str, Any] = {
        "tickers": tickers,
        "refresh_features": refresh_features,
        "target_date": target_date,
    }
    if body.get("bisMaxPages") is not None or body.get("bis_max_pages") is not None:
        params["bis_max_pages"] = body.get("bisMaxPages", body.get("bis_max_pages"))
    if body.get("sleepSec") is not None or body.get("sleep_sec") is not None:
        params["sleep_sec"] = body.get("sleepSec", body.get("sleep_sec"))
    return params
