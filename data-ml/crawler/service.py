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
    target_date: date | None = None,
    *,
    raw_csv_path: str | Path | None = None,
    processed_csv_path: str | Path | None = None,
    run_type: str = "crawler_service",
) -> dict:
    """
    정책/시장 뉴스 수집 + 후처리.

    collectors/postprocessing 을 사용한다.
    레거시 keyword_config_path / doc_types 인자는 호환용으로만 받고 무시한다.

    후처리 산출 스키마 (body 컬럼 없음):
      sector, source, category, doc_type, release_date, url, title?,
      body_summary, body_original_length, category_*, sentiment, embeddings

    기본 경로:
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

    raw_df = collect_policy_updates(
        bis_max_pages=bis_max_pages,
        sleep_sec=sleep_sec,
        target_date=target_date,
    )

    if raw_df is None or raw_df.empty:
        persist_result = persist_policy_pipeline_outputs(
            raw_df=raw_df if raw_df is not None else pd.DataFrame(),
            processed_df=pd.DataFrame(),
            raw_csv_path=raw_csv,
            processed_csv_path=processed_csv,
            run_type=run_type,
            append_processed=True,
        )
        return {
            "status": "success",
            "raw_count": 0,
            "processed_count": 0,
            "message": "수집된 정책 뉴스가 없습니다.",
            **persist_result,
        }

    # 후처리: body → body_summary rename (원문 body 컬럼 삭제)
    processed_df = run_postprocessing_pipeline(raw_df)

    persist_result = persist_policy_pipeline_outputs(
        raw_df=raw_df,
        processed_df=processed_df,
        raw_csv_path=raw_csv,
        processed_csv_path=processed_csv,
        run_type=run_type,
        append_processed=True,
    )

    return {
        "status": "success",
        "message": "정책 뉴스 수집과 후처리를 완료했습니다.",
        **persist_result,
    }
