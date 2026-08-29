from __future__ import annotations

import logging
import os
import sys
from datetime import date
from pathlib import Path

import pandas as pd

PROJECT_ROOT = Path(__file__).resolve().parents[1]
PROJECT_ROOT_STR = str(PROJECT_ROOT)
if PROJECT_ROOT_STR not in sys.path:
    sys.path.insert(0, PROJECT_ROOT_STR)

from crawler.external import ExternalCrawlerError, run_apps_crawler_policy_monitor
from crawler.support_legacy.data_paths import collected_csv_path, feature_csv_path
from db.db import init_db, persist_policy_pipeline_outputs


logger = logging.getLogger(__name__)

DEFAULT_BIS_MAX_PAGES = int(os.getenv("BIS_MAX_PAGES", "5"))
DEFAULT_SLEEP_SEC = float(os.getenv("CRAWL_SLEEP_SEC", "1"))


def _read_csv_or_empty(path: Path) -> pd.DataFrame:
    if not path.exists() or path.stat().st_size == 0:
        return pd.DataFrame()
    try:
        return pd.read_csv(path, encoding="utf-8-sig")
    except pd.errors.EmptyDataError:
        return pd.DataFrame()
    except Exception as error:
        logger.warning("[crawl] failed to read csv %s: %s", path, error)
        return pd.DataFrame()


def _policy_row_keys(frame: pd.DataFrame) -> set[tuple[str, str]]:
    if frame is None or frame.empty:
        return set()
    if {"sector", "url"}.issubset(frame.columns):
        return {
            (str(sector), str(url))
            for sector, url in zip(frame["sector"].tolist(), frame["url"].tolist())
        }
    if "url" in frame.columns:
        return {("", str(url)) for url in frame["url"].tolist()}
    return set()


def _rows_not_in_keys(frame: pd.DataFrame, existing_keys: set[tuple[str, str]]) -> pd.DataFrame:
    if frame is None or frame.empty:
        return pd.DataFrame()
    if not existing_keys:
        return frame.copy()

    if {"sector", "url"}.issubset(frame.columns):
        mask = [
            (str(sector), str(url)) not in existing_keys
            for sector, url in zip(frame["sector"].tolist(), frame["url"].tolist())
        ]
        return frame.loc[mask].copy()
    if "url" in frame.columns:
        mask = [("", str(url)) not in existing_keys for url in frame["url"].tolist()]
        return frame.loc[mask].copy()
    return frame.copy()


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
    정책/시장 뉴스 수집 + 후처리.

    env 의 policy_monitor.py 를 한 사이클 실행한 뒤
    신규 행만 data-ml CSV/DB 에 반영한다.

    경로:
      - POLICY_MONITOR_PATH, POLICY_MONITOR_OUTPUT_PATH, CRAWLER_APP_ROOT
      - raw: data/crawler/collected/policy_updates_monitor.csv
      - processed: data/crawler/features/policy_updates_features.csv (누적 append)
    """
    if keyword_config_path is not None or doc_types is not None:
        logger.info(
            "[crawl] ignoring legacy kwargs keyword_config_path=%s doc_types=%s",
            keyword_config_path,
            doc_types,
        )

    init_db()
    raw_csv = str(raw_csv_path) if raw_csv_path else collected_csv_path("policy_updates_monitor.csv")
    processed_csv = (
        str(processed_csv_path) if processed_csv_path else feature_csv_path("policy_updates_features.csv")
    )
    existing_keys = _policy_row_keys(_read_csv_or_empty(Path(processed_csv)))

    try:
        crawl = run_apps_crawler_policy_monitor(
            target_date=target_date,
            bis_max_pages_override=bis_max_pages,
            sleep_sec=sleep_sec,
        )
    except ExternalCrawlerError as error:
        logger.warning("[crawl] apps/crawler policy_monitor failed: %s", error.message)
        return {
            "status": "failed",
            "raw_count": 0,
            "processed_count": 0,
            "message": error.message,
            "code": error.code,
            "details": error.details,
            "raw_csv_path": raw_csv,
            "processed_csv_path": processed_csv,
        }

    processed_df = _read_csv_or_empty(Path(crawl["output_path"]))
    new_rows = _rows_not_in_keys(processed_df, existing_keys)

    if new_rows.empty:
        logger.info(
            "[crawl] apps/crawler finished with no new policy rows (output=%s)",
            crawl["output_path"],
        )
        return {
            "status": "success",
            "raw_count": 0,
            "processed_count": 0,
            "cycle_processed_count": 0,
            "message": "수집된 정책 뉴스가 없습니다.",
            "policy_features_path": crawl["output_path"],
            "raw_csv_path": raw_csv,
            "processed_csv_path": processed_csv,
        }

    persist_result = persist_policy_pipeline_outputs(
        raw_df=new_rows,
        processed_df=new_rows,
        raw_csv_path=raw_csv,
        processed_csv_path=processed_csv,
        run_type=run_type,
        append_processed=True,
    )

    return {
        "status": "success",
        "message": "정책 뉴스 수집과 후처리를 완료했습니다.",
        "policy_features_path": crawl["output_path"],
        **persist_result,
        "raw_count": int(len(new_rows)),
        "processed_count": int(len(new_rows)),
    }
