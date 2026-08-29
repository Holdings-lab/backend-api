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
DEFAULT_NEWS_FEATURES_PATH = Path("/opt/riseai/data/features/qqq/news_event_features.csv")
DEFAULT_MARKET_FEATURES_PATH = Path("/opt/riseai/data/features/qqq/market_long_features.csv")
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
    env_value = os.getenv("SIGNAL_PREDICTIONS_DIR")
    if env_value:
        return Path(env_value)
    return DEFAULT_PREDICTIONS_DIR


def _default_output_path(ticker: str) -> Path:
    env_value = os.getenv("SIGNAL_OUTPUT_PATH")
    if env_value and _ticker_slug(ticker) == "qqq":
        return Path(env_value)
    return _predictions_dir() / f"{_ticker_slug(ticker)}_latest_signal.json"


def _news_features_path() -> Path:
    return Path(os.getenv("SIGNAL_NEWS_FEATURES_PATH", str(DEFAULT_NEWS_FEATURES_PATH)))


def _market_features_path() -> Path:
    return Path(os.getenv("SIGNAL_MARKET_FEATURES_PATH", str(DEFAULT_MARKET_FEATURES_PATH)))


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


def prepare_features_existing() -> dict[str, Any]:
    news_features_path = _news_features_path()
    market_features_path = _market_features_path()
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
        "[Signal] using existing feature CSVs (news=%s, market=%s)",
        news_features_path,
        market_features_path,
    )
    return {
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
    1) env 의 policy_monitor.py → POLICY_MONITOR_OUTPUT_PATH
    2) policy CSV → news_event_features.csv + market_long_features.csv
    3) 두 feature 경로를 predict_signal 입력으로 반환
    """
    from crawler.external import ExternalCrawlerError, run_apps_crawler_policy_monitor
    from lstm_signal.build_qqq_features import FeatureBuildError, build_qqq_feature_csvs

    parsed_target_date = target_date if isinstance(target_date, date) else _parse_target_date(target_date)
    news_features_path = _news_features_path()
    market_features_path = _market_features_path()
    news_features_path.parent.mkdir(parents=True, exist_ok=True)
    market_features_path.parent.mkdir(parents=True, exist_ok=True)

    try:
        crawl = run_apps_crawler_policy_monitor(
            target_date=parsed_target_date,
            bis_max_pages_override=bis_max_pages,
            sleep_sec=sleep_sec,
        )
    except ExternalCrawlerError as error:
        raise SignalRunnerError(error.message, code=error.code, details=error.details) from error

    crawl_output_path = Path(crawl["output_path"])

    try:
        built = build_qqq_feature_csvs(
            policy_features_path=crawl_output_path,
            news_event_output_path=news_features_path,
            market_long_output_path=market_features_path,
            ticker=ticker,
            target_date=parsed_target_date,
        )
    except FeatureBuildError as error:
        raise SignalRunnerError(
            f"feature 변환에 실패했습니다: {error.message}",
            code="ML_SIGNAL_FEATURES_BUILD_FAILED",
            details=error.details,
        ) from error
    except Exception as error:
        raise SignalRunnerError(
            f"feature 변환에 실패했습니다: {error}",
            code="ML_SIGNAL_FEATURES_BUILD_FAILED",
            details={"error": str(error)},
        ) from error

    return {
        "news_features_path": str(news_features_path),
        "market_features_path": str(market_features_path),
        "policy_features_path": str(crawl_output_path),
        "feature_build": built,
    }


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
    - 기본(True): 크롤 → policy→news_event/market_long 변환
    - False: 기존 고정 CSV만 검증/사용
    """
    if bool(refresh_features):
        return prepare_features_from_crawl(
            target_date=target_date,
            bis_max_pages=bis_max_pages,
            sleep_sec=sleep_sec,
            ticker=ticker,
        )
    return prepare_features_existing()


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


def parse_signal_request(payload: dict[str, Any] | None) -> dict[str, Any]:
    body = payload or {}
    ticker = body.get("ticker") or body.get("Ticker") or "QQQ"
    if isinstance(ticker, str):
        ticker = ticker.strip() or "QQQ"
    else:
        ticker = "QQQ"
    if not re.fullmatch(r"[A-Za-z0-9^._-]{1,32}", ticker):
        raise SignalRunnerError("ticker 형식이 올바르지 않습니다.", code="ML_SIGNAL_BAD_REQUEST")

    target_date = body.get("targetDate") or body.get("target_date") or body.get("date")
    _parse_target_date(target_date)

    refresh_features = _parse_bool(
        body.get("refreshFeatures", body.get("refresh_features")),
        default=True,
    )

    params: dict[str, Any] = {
        "ticker": ticker,
        "refresh_features": refresh_features,
        "target_date": target_date,
    }
    if body.get("bisMaxPages") is not None or body.get("bis_max_pages") is not None:
        params["bis_max_pages"] = body.get("bisMaxPages", body.get("bis_max_pages"))
    if body.get("sleepSec") is not None or body.get("sleep_sec") is not None:
        params["sleep_sec"] = body.get("sleepSec", body.get("sleep_sec"))
    return params
