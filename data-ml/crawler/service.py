from __future__ import annotations

import logging
import os
import sys
from datetime import date
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
PROJECT_ROOT_STR = str(PROJECT_ROOT)
if PROJECT_ROOT_STR not in sys.path:
    sys.path.insert(0, PROJECT_ROOT_STR)

from crawler.external import ExternalCrawlerError, run_apps_crawler_policy_monitor


logger = logging.getLogger(__name__)

DEFAULT_BIS_MAX_PAGES = int(os.getenv("BIS_MAX_PAGES", "5"))
DEFAULT_SLEEP_SEC = float(os.getenv("CRAWL_SLEEP_SEC", "1"))


def run_crawl_now(
    bis_max_pages: int = DEFAULT_BIS_MAX_PAGES,
    sleep_sec: float = DEFAULT_SLEEP_SEC,
    keyword_config_path: str | Path | None = None,
    doc_types: list[str] | None = None,
    target_date: date | None = None,
    *,
    raw_csv_path: str | Path | None = None,
    processed_csv_path: str | Path | None = None,
    run_type: str = "crawler_service",
) -> dict:
    """
    env 의 policy_monitor.py 를 한 사이클 실행한다.

    CSV 출력은 policy_monitor.py 가 담당한다.
    """
    if keyword_config_path is not None or doc_types is not None:
        logger.info(
            "[crawl] ignoring legacy kwargs keyword_config_path=%s doc_types=%s",
            keyword_config_path,
            doc_types,
        )
    if raw_csv_path is not None or processed_csv_path is not None:
        logger.info(
            "[crawl] ignoring csv path kwargs raw=%s processed=%s",
            raw_csv_path,
            processed_csv_path,
        )

    try:
        crawl = run_apps_crawler_policy_monitor(
            target_date=target_date,
            bis_max_pages_override=bis_max_pages,
            sleep_sec=sleep_sec,
        )
    except ExternalCrawlerError as error:
        logger.warning("[crawl] policy_monitor failed: %s", error.message)
        return {
            "status": "failed",
            "message": error.message,
            "code": error.code,
            "details": error.details,
        }

    return {
        "status": "success",
        "message": "policy_monitor.py 실행을 완료했습니다.",
        **crawl,
    }
