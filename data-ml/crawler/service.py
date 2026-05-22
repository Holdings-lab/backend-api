from __future__ import annotations

import logging
import os
import sys
from pathlib import Path

import pandas as pd

PROJECT_ROOT = Path(__file__).resolve().parents[1]
PROJECT_ROOT_STR = str(PROJECT_ROOT)
if PROJECT_ROOT_STR not in sys.path:
    sys.path.insert(0, PROJECT_ROOT_STR)

from crawler.collectors.policy_monitor import collect_policy_updates, run_postprocessing_pipeline
from crawler.support_legacy.data_paths import collected_csv_path, feature_csv_path
from db.db import init_db, persist_policy_pipeline_outputs


logger = logging.getLogger(__name__)

DEFAULT_BIS_MAX_PAGES = int(os.getenv("BIS_MAX_PAGES", "5"))
DEFAULT_SLEEP_SEC = float(os.getenv("CRAWL_SLEEP_SEC", "1"))


def run_crawl_now(
    bis_max_pages: int = DEFAULT_BIS_MAX_PAGES,
    sleep_sec: float = DEFAULT_SLEEP_SEC,
    keyword_config_path: str | Path | None = None,
    doc_types: list[str] | None = None,
) -> dict:
    init_db()
    raw_csv = collected_csv_path("policy_updates_monitor.csv")
    processed_csv = feature_csv_path("policy_updates_features.csv")

    raw_df = collect_policy_updates(
        bis_max_pages=bis_max_pages,
        sleep_sec=sleep_sec,
        keyword_config_path=keyword_config_path,
        doc_types=doc_types,
    )

    if raw_df.empty:
        persist_result = persist_policy_pipeline_outputs(
            raw_df=raw_df,
            processed_df=pd.DataFrame(),
            raw_csv_path=raw_csv,
            processed_csv_path=processed_csv,
            run_type="crawler_service",
        )
        return {
            "status": "success",
            "raw_count": 0,
            "processed_count": 0,
            "message": "수집된 정책 뉴스가 없습니다.",
            **persist_result,
        }

    processed_df = run_postprocessing_pipeline(raw_df)

    # CSV와 DB에 동시에 저장한다.
    persist_result = persist_policy_pipeline_outputs(
        raw_df=raw_df,
        processed_df=processed_df,
        raw_csv_path=raw_csv,
        processed_csv_path=processed_csv,
        run_type="crawler_service",
    )

    return {
        "status": "success",
        "message": "정책 뉴스 수집과 후처리를 완료했습니다.",
        **persist_result,
    }
