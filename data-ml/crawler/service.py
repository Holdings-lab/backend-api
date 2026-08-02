from __future__ import annotations

import asyncio
import logging
import os
import sys
from concurrent.futures import ThreadPoolExecutor
from datetime import date
from pathlib import Path
from typing import Any, Callable, TypeVar

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

T = TypeVar("T")


def _apply_runtime_env_overrides() -> None:
    """
    crawler 패키지 파일은 원본과 동일하게 유지하고,
    Docker/운영 환경 값만 service 계층에서 주입한다.
    """
    ollama_base = (os.getenv("OLLAMA_BASE_URL") or "").strip()
    ollama_model = (os.getenv("OLLAMA_MODEL") or "").strip()
    if not ollama_base and not ollama_model:
        return

    try:
        import crawler.postprocessing.text_summarizer as text_summarizer
    except Exception as error:
        logger.warning("[crawl] failed to import text_summarizer for OLLAMA override: %s", error)
        return

    if ollama_base:
        base = ollama_base.rstrip("/")
        text_summarizer.OLLAMA_BASE_URL = base
        text_summarizer.OLLAMA_GENERATE_URL = f"{base}/api/generate"
        text_summarizer.OLLAMA_TAGS_URL = f"{base}/api/tags"
        logger.info("[crawl] OLLAMA_BASE_URL override applied: %s", base)

    if ollama_model:
        text_summarizer.OLLAMA_MODEL = ollama_model
        logger.info("[crawl] OLLAMA_MODEL override applied: %s", ollama_model)


def _run_in_isolated_thread(fn: Callable[..., T], *args: Any, **kwargs: Any) -> T:
    """
    Playwright Sync API 는 running asyncio loop 가 있는 스레드에서 호출되면 실패한다.
    FastAPI/uvicorn/APScheduler 경로와 무관하게 수집은 항상 별도 스레드에서 실행한다.
    """

    def _call() -> T:
        # 격리 스레드에 실수로 loop 가 붙어 있지 않은지 방어
        try:
            asyncio.get_running_loop()
            raise RuntimeError("isolated crawl thread unexpectedly has a running event loop")
        except RuntimeError as error:
            if "unexpectedly has a running event loop" in str(error):
                raise
        return fn(*args, **kwargs)

    with ThreadPoolExecutor(max_workers=1, thread_name_prefix="crawl-isolated") as pool:
        return pool.submit(_call).result()


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

    _apply_runtime_env_overrides()
    init_db()
    raw_csv = str(raw_csv_path) if raw_csv_path else collected_csv_path("policy_updates_monitor.csv")
    processed_csv = (
        str(processed_csv_path) if processed_csv_path else feature_csv_path("policy_updates_features.csv")
    )

    # EIA(Playwright sync) 포함 수집은 asyncio loop 밖 스레드에서 실행
    raw_df = _run_in_isolated_thread(
        collect_policy_updates,
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

    # 요약(Ollama)이 필요한 후처리 구간에만 컨테이너 자동 기동/종료
    from ollama_lifecycle import ollama_session

    with ollama_session():
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
