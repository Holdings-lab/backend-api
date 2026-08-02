from __future__ import annotations

"""
policy_updates_features.csv → news_event_features.csv + market_long_features.csv

ml-worker/shared 파이프라인으로 숫자 피처를 만든다.
1. policy CSV → 뉴스 소스 스키마
2. 일자별 뉴스 피처
3. yfinance 시장 피처 (market_long)
4. market + news merge (news_event)
"""

import logging
import os
import sys
import tempfile
import traceback
from dataclasses import replace
from datetime import date, timedelta
from pathlib import Path
from typing import Any, Callable

import pandas as pd

logger = logging.getLogger(__name__)

DEFAULT_ML_WORKER_ROOT = Path("/opt/riseai/apps/ml-worker")
DEFAULT_START_DATE = "2017-01-12"


class FeatureBuildError(Exception):
    def __init__(self, message: str, *, details: dict[str, Any] | None = None):
        super().__init__(message)
        self.message = message
        self.details = details or {}


def _ml_worker_root() -> Path:
    return Path(os.getenv("ML_WORKER_ROOT", str(DEFAULT_ML_WORKER_ROOT)))


def _ensure_shared_importable() -> Path:
    root = _ml_worker_root()
    marker = root / "shared" / "market" / "data.py"
    if not marker.exists():
        raise FeatureBuildError(
            f"ml-worker/shared 를 찾을 수 없습니다: {root}",
            details={"ml_worker_root": str(root), "marker": str(marker)},
        )
    root_str = str(root)
    if root_str not in sys.path:
        sys.path.insert(0, root_str)
    return root


def _market_start_date() -> str:
    return (os.getenv("SIGNAL_MARKET_START_DATE") or DEFAULT_START_DATE).strip() or DEFAULT_START_DATE


def _market_end_date(target_date: date | None = None) -> str:
    env_value = (os.getenv("SIGNAL_MARKET_END_DATE") or "").strip()
    if env_value:
        return env_value
    # yfinance end 는 exclusive 에 가깝게 동작하므로 +1일
    base = target_date or date.today()
    return (base + timedelta(days=1)).isoformat()


def policy_updates_to_news_source_df(policy_df: pd.DataFrame) -> pd.DataFrame:
    """크롤 후처리 CSV 를 shared.load_news_source_table 입력 스키마로 맞춘다."""
    if policy_df.empty:
        raise FeatureBuildError("policy_updates_features.csv 가 비어 있습니다.")

    frame = policy_df.copy()

    if "date" not in frame.columns:
        if "release_date" in frame.columns:
            frame["date"] = frame["release_date"]
        elif "published_date" in frame.columns:
            frame["date"] = frame["published_date"]
        else:
            raise FeatureBuildError(
                "policy CSV 에 date/release_date/published_date 컬럼이 없습니다.",
                details={"columns": list(frame.columns)},
            )

    if "body" not in frame.columns:
        if "body_summary" in frame.columns:
            frame["body"] = frame["body_summary"].fillna("").astype(str)
        else:
            frame["body"] = ""

    for required in ("category", "doc_type", "title"):
        if required not in frame.columns:
            frame[required] = "unknown" if required != "title" else ""

    frame["category"] = frame["category"].fillna("Unknown").astype(str)
    frame["doc_type"] = frame["doc_type"].fillna("unknown").astype(str)
    frame["title"] = frame["title"].fillna("").astype(str)
    frame["body"] = frame["body"].fillna("").astype(str)

    return frame


def _run_step(step: str, fn: Callable[[], Any]) -> Any:
    try:
        return fn()
    except FeatureBuildError:
        raise
    except Exception as error:
        raise FeatureBuildError(
            f"{step} 실패: {type(error).__name__}: {error}",
            details={
                "step": step,
                "error_type": type(error).__name__,
                "error": str(error),
                "traceback": traceback.format_exc(),
            },
        ) from error


def build_qqq_feature_csvs(
    *,
    policy_features_path: Path | str,
    news_event_output_path: Path | str,
    market_long_output_path: Path | str,
    ticker: str = "QQQ",
    target_date: date | None = None,
) -> dict[str, Any]:
    """
    policy_updates_features.csv 로부터 LSTM/XGB 입력 CSV 두 개를 생성한다.
    """
    shared_root = _run_step("shared import", _ensure_shared_importable)

    from shared.config.schema import MarketNewsTrainingConfig
    from shared.market.data import build_market_feature_frame, download_market_data
    from shared.news.features import build_daily_news_feature_table, load_news_source_table
    from shared.news.merge import merge_news_features_into_market_frame

    policy_path = Path(policy_features_path)
    news_out = Path(news_event_output_path)
    market_out = Path(market_long_output_path)

    if not policy_path.exists():
        raise FeatureBuildError(f"policy feature CSV 가 없습니다: {policy_path}")

    policy_df = _run_step(
        "read policy CSV",
        lambda: pd.read_csv(policy_path, encoding="utf-8-sig"),
    )
    news_source_df = _run_step(
        "normalize policy schema",
        lambda: policy_updates_to_news_source_df(policy_df),
    )

    def _load_prepared_news() -> pd.DataFrame:
        with tempfile.TemporaryDirectory(prefix="signal_news_") as tmp_dir:
            news_source_path = Path(tmp_dir) / "news_source.csv"
            news_source_df.to_csv(news_source_path, index=False, encoding="utf-8-sig")
            return load_news_source_table(news_source_path)

    prepared_news = _run_step("load news source table", _load_prepared_news)
    daily_news = _run_step(
        "build daily news features",
        lambda: build_daily_news_feature_table(prepared_news),
    )

    start_date = _market_start_date()
    end_date = _market_end_date(target_date)
    normalized_ticker = (ticker or "QQQ").strip().upper() or "QQQ"

    config = _run_step(
        "build market config",
        lambda: replace(
            MarketNewsTrainingConfig(),
            target_ticker=normalized_ticker,
            start_date=start_date,
            end_date=end_date,
            news_input_path=policy_path,
        ),
    )

    logger.info(
        "[SignalFeatures] downloading market data ticker=%s start=%s end=%s shared=%s",
        normalized_ticker,
        start_date,
        end_date,
        shared_root,
    )
    raw_market = _run_step("download market data", lambda: download_market_data(config))
    market_feature_df, _ = _run_step(
        "build market features",
        lambda: build_market_feature_frame(
            raw_market,
            config.target_ticker,
            supplementary_tickers=config.macro_tickers,
        ),
    )

    merged_df, _ = _run_step(
        "merge news into market",
        lambda: merge_news_features_into_market_frame(market_feature_df, daily_news),
    )

    def _write_outputs() -> None:
        news_out.parent.mkdir(parents=True, exist_ok=True)
        market_out.parent.mkdir(parents=True, exist_ok=True)
        market_feature_df.to_csv(market_out, index=False, encoding="utf-8-sig")
        merged_df.to_csv(news_out, index=False, encoding="utf-8-sig")

    _run_step("write feature CSVs", _write_outputs)

    logger.info(
        "[SignalFeatures] wrote market_long=%s rows=%s news_event=%s rows=%s "
        "daily_news_days=%s policy_docs=%s",
        market_out,
        len(market_feature_df),
        news_out,
        len(merged_df),
        len(daily_news),
        len(news_source_df),
    )

    return {
        "news_features_path": str(news_out),
        "market_features_path": str(market_out),
        "policy_features_path": str(policy_path),
        "market_rows": int(len(market_feature_df)),
        "news_event_rows": int(len(merged_df)),
        "daily_news_days": int(len(daily_news)),
        "policy_docs": int(len(news_source_df)),
        "shared_root": str(shared_root),
        "market_start_date": start_date,
        "market_end_date": end_date,
    }
