"""
POLICY_MONITOR_PATH 의 policy_monitor.py 실행
"""
from __future__ import annotations

import logging
import os
import subprocess
import sys
from datetime import date, datetime, timezone
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

DEFAULT_CRAWL_TIMEOUT_SEC = 1800


class ExternalCrawlerError(Exception):
    def __init__(self, message: str, *, code: str = "ML_SIGNAL_CRAWL_FAILED", details: dict[str, Any] | None = None):
        super().__init__(message)
        self.message = message
        self.code = code
        self.details = details or {}


def _in_docker() -> bool:
    return Path("/.dockerenv").exists()


def _env_path(*names: str) -> Path:
    for name in names:
        raw = (os.getenv(name) or "").strip()
        if raw:
            return Path(raw)
    joined = ", ".join(names)
    raise ExternalCrawlerError(
        f"{joined} 환경변수가 없습니다.",
        code="ML_SIGNAL_CONFIG_ERROR",
    )


def policy_monitor_path() -> Path:
    return _env_path("POLICY_MONITOR_PATH")


def crawler_app_root() -> Path:
    return _env_path("CRAWLER_APP_ROOT")


def _crawler_python(crawler_root: Path) -> str:
    configured = (os.getenv("CRAWLER_PYTHON") or "").strip()
    if _in_docker():
        if configured and ".venv" in configured.replace("\\", "/"):
            logger.warning(
                "[Crawl] Ignoring host crawler venv inside Docker (%s); using container python",
                configured,
            )
        return sys.executable

    if configured:
        return configured

    venv_python = crawler_root / ".venv" / "bin" / "python"
    if venv_python.exists():
        return str(venv_python)
    return sys.executable


def _crawl_timeout_sec() -> int:
    raw = os.getenv("SIGNAL_CRAWL_TIMEOUT_SEC", str(DEFAULT_CRAWL_TIMEOUT_SEC))
    try:
        return max(60, int(raw))
    except ValueError:
        return DEFAULT_CRAWL_TIMEOUT_SEC


def bis_max_pages(override: int | None = None) -> int:
    if override is not None:
        return max(1, int(override))
    raw = os.getenv("BIS_MAX_PAGES", "5")
    try:
        return max(1, int(raw))
    except ValueError:
        return 5


def crawl_sleep_sec(override: float | None = None) -> float:
    if override is not None:
        return max(0.0, float(override))
    raw = os.getenv("CRAWL_SLEEP_SEC", "1")
    try:
        return max(0.0, float(raw))
    except ValueError:
        return 1.0


def _tail(text: str, limit: int = 4000) -> str:
    if not text:
        return ""
    if len(text) <= limit:
        return text
    return text[-limit:]


def _clip(text: str, limit: int = 200_000) -> str:
    if not text:
        return ""
    if len(text) <= limit:
        return text
    return text[-limit:]


def _failure_log_dir() -> Path | None:
    raw = (os.getenv("POLICY_MONITOR_LOG_DIR") or "").strip()
    if raw:
        return Path(raw)
    return None


def write_policy_monitor_failure_log(error: ExternalCrawlerError) -> str | None:
    """POLICY_MONITOR_LOG_DIR/policy_monitor_failure/<실행시각>.log 에 실패 로그를 남긴다."""
    log_root = _failure_log_dir()
    if log_root is None:
        logger.warning("[Crawl] POLICY_MONITOR_LOG_DIR 가 없어 실패 로그를 쓰지 못했습니다")
        return None

    try:
        log_dir = log_root / "policy_monitor_failure"
        log_dir.mkdir(parents=True, exist_ok=True)
        occurred_at = datetime.now(timezone.utc)
        stamp = occurred_at.strftime("%Y%m%dT%H%M%S") + f"_{occurred_at.microsecond:06d}Z"
        log_path = log_dir / f"{stamp}.log"
        details = error.details or {}
        command = details.get("command")
        command_text = (
            " ".join(str(part) for part in command)
            if isinstance(command, (list, tuple))
            else str(command or "")
        )
        stdout = details.get("stdout") or details.get("stdout_tail") or ""
        stderr = details.get("stderr") or details.get("stderr_tail") or ""
        extra_error = details.get("error") or ""
        exit_code = details.get("exit_code", "")

        block = (
            f"time: {occurred_at.strftime('%Y-%m-%dT%H:%M:%S.%fZ')}\n"
            f"code: {error.code}\n"
            f"message: {error.message}\n"
            f"exit_code: {exit_code}\n"
            f"command: {command_text}\n"
            f"error: {extra_error}\n"
            f"\n----- stdout -----\n{stdout}\n"
            f"\n----- stderr -----\n{stderr}\n"
        )
        log_path.write_text(block, encoding="utf-8")
        logger.warning("[Crawl] failure log written: %s", log_path)
        return str(log_path)
    except Exception as write_error:
        logger.warning("[Crawl] failed to write failure log: %s", write_error)
        return None


def run_apps_crawler_policy_monitor(
    *,
    target_date: date | None = None,
    bis_max_pages_override: int | None = None,
    sleep_sec: float | None = None,
) -> dict[str, Any]:
    try:
        return _run_policy_monitor(
            target_date=target_date,
            bis_max_pages_override=bis_max_pages_override,
            sleep_sec=sleep_sec,
        )
    except ExternalCrawlerError as error:
        log_path = write_policy_monitor_failure_log(error)
        if log_path:
            error.details["failure_log_path"] = log_path
        raise


def _run_policy_monitor(
    *,
    target_date: date | None = None,
    bis_max_pages_override: int | None = None,
    sleep_sec: float | None = None,
) -> dict[str, Any]:
    """
    env 의 policy_monitor.py 를 --max-cycles 1 로 실행한다.
    CSV 출력 경로는 policy_monitor.py 가 스스로 정한다.
    """
    crawler_root = crawler_app_root()
    monitor_script = policy_monitor_path()
    python_bin = _crawler_python(crawler_root)

    if not crawler_root.exists():
        raise ExternalCrawlerError(
            f"CRAWLER_APP_ROOT 경로가 없습니다: {crawler_root}",
            code="ML_SIGNAL_CONFIG_ERROR",
        )
    if not monitor_script.exists():
        raise ExternalCrawlerError(
            f"POLICY_MONITOR_PATH 파일을 찾을 수 없습니다: {monitor_script}",
            code="ML_SIGNAL_CONFIG_ERROR",
        )
    if target_date is not None:
        logger.warning(
            "[Crawl] target_date=%s 는 policy_monitor.py CLI 에서 무시됩니다",
            target_date.isoformat(),
        )

    command = [
        python_bin,
        "-B",
        str(monitor_script),
        "--max-cycles",
        "1",
        "--interval-sec",
        "0",
        "--bis-max-pages",
        str(bis_max_pages(bis_max_pages_override)),
        "--sleep-sec",
        str(crawl_sleep_sec(sleep_sec)),
    ]

    env = os.environ.copy()
    env["PYTHONPATH"] = str(crawler_root)
    if not (env.get("OLLAMA_BASE_URL") or "").strip():
        env["OLLAMA_BASE_URL"] = "http://ollama:11434"

    logger.info(
        "[Crawl] running policy_monitor (script=%s, cmd=%s)",
        monitor_script,
        " ".join(command),
    )

    try:
        from ollama_lifecycle import ollama_session

        with ollama_session():
            completed = subprocess.run(
                command,
                cwd=str(crawler_root),
                env=env,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=_crawl_timeout_sec(),
                check=False,
            )
    except ExternalCrawlerError:
        raise
    except subprocess.TimeoutExpired as error:
        raise ExternalCrawlerError(
            f"policy_monitor 실행이 {_crawl_timeout_sec()}초를 초과했습니다.",
            code="ML_SIGNAL_CRAWL_FAILED",
            details={
                "command": command,
                "stdout": _clip(error.stdout or ""),
                "stderr": _clip(error.stderr or ""),
                "stdout_tail": _tail(error.stdout or ""),
                "stderr_tail": _tail(error.stderr or ""),
            },
        ) from error
    except Exception as error:
        raise ExternalCrawlerError(
            f"크롤링 실행에 실패했습니다: {error}",
            code="ML_SIGNAL_CRAWL_FAILED",
            details={"command": command, "error": str(error)},
        ) from error

    if completed.returncode != 0:
        raise ExternalCrawlerError(
            "policy_monitor 실행에 실패했습니다.",
            code="ML_SIGNAL_CRAWL_FAILED",
            details={
                "exit_code": completed.returncode,
                "command": command,
                "stdout": _clip(completed.stdout),
                "stderr": _clip(completed.stderr),
                "stdout_tail": _tail(completed.stdout),
                "stderr_tail": _tail(completed.stderr),
            },
        )

    logger.info(
        "[Crawl] policy_monitor finished stdout_tail=%s",
        _tail(completed.stdout, 800),
    )
    return {
        "stdout_tail": _tail(completed.stdout, 800),
        "stderr_tail": _tail(completed.stderr, 800),
        "command": command,
    }
