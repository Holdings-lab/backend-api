"""
/opt/riseai/apps/crawler 의 policy_monitor 를 한 사이클 실행하는 래퍼.

data-ml 의 crawler 패키지와 이름이 겹치므로 subprocess 전용 진입점으로 사용한다.
Ollama URL/모델은 환경변수로 주입한다 (외부 crawler 에 service.py 없음).
"""
from __future__ import annotations

import argparse
import os
import sys
from datetime import date, datetime
from pathlib import Path


def _parse_date(value: str | None) -> date | None:
    if not value:
        return None
    text = str(value).strip()
    if not text:
        return None
    try:
        return date.fromisoformat(text)
    except ValueError:
        return datetime.fromisoformat(text).date()


def _apply_ollama_overrides() -> None:
    ollama_base = (os.getenv("OLLAMA_BASE_URL") or "").strip()
    ollama_model = (os.getenv("OLLAMA_MODEL") or "").strip()
    if not ollama_base and not ollama_model:
        return

    import crawler.postprocessing.text_summarizer as text_summarizer

    if ollama_base:
        base = ollama_base.rstrip("/")
        text_summarizer.OLLAMA_BASE_URL = base
        text_summarizer.OLLAMA_GENERATE_URL = f"{base}/api/generate"
        text_summarizer.OLLAMA_TAGS_URL = f"{base}/api/tags"
        print(f"[external-monitor] OLLAMA_BASE_URL={base}", flush=True)
    if ollama_model:
        text_summarizer.OLLAMA_MODEL = ollama_model
        print(f"[external-monitor] OLLAMA_MODEL={ollama_model}", flush=True)


def _append_processed(output_path: Path, processed_news) -> int:
    import pandas as pd

    output_path.parent.mkdir(parents=True, exist_ok=True)
    if output_path.exists():
        try:
            existing = pd.read_csv(output_path, encoding="utf-8-sig")
        except Exception:
            existing = pd.DataFrame()
    else:
        existing = pd.DataFrame()

    if existing.empty:
        merged = processed_news.copy()
    else:
        merged = pd.concat([existing, processed_news], ignore_index=True, sort=False)

    if not merged.empty and {"sector", "url"}.issubset(merged.columns):
        merged = merged.drop_duplicates(subset=["sector", "url"], keep="last")
    if "sector" in merged.columns:
        ordered = ["sector"] + [column for column in merged.columns if column != "sector"]
        merged = merged[ordered]

    merged.to_csv(output_path, index=False, encoding="utf-8-sig")
    return int(len(merged))


def main() -> int:
    parser = argparse.ArgumentParser(description="Run apps/crawler policy_monitor for one cycle")
    parser.add_argument("--crawler-root", type=str, required=True, help="/opt/riseai/apps/crawler")
    parser.add_argument(
        "--output-path",
        type=str,
        default="/opt/riseai/data/crawler/policy_updates_features.csv",
    )
    parser.add_argument("--bis-max-pages", type=int, default=1)
    parser.add_argument("--sleep-sec", type=float, default=1.0)
    parser.add_argument("--target-date", type=str, default=None, help="YYYY-MM-DD (optional)")
    args = parser.parse_args()

    crawler_root = Path(args.crawler_root).resolve()
    if not crawler_root.exists():
        print(f"[external-monitor] crawler root not found: {crawler_root}", file=sys.stderr)
        return 2

    # data-ml 의 crawler 보다 외부 apps/crawler 를 우선
    root_str = str(crawler_root)
    while root_str in sys.path:
        sys.path.remove(root_str)
    sys.path.insert(0, root_str)

    # 이미 import 된 data-ml crawler 모듈이 있으면 제거
    for key in list(sys.modules):
        if key == "crawler" or key.startswith("crawler."):
            del sys.modules[key]

    _apply_ollama_overrides()

    from crawler.collectors.policy_monitor import (  # noqa: WPS433
        collect_policy_updates,
        run_monitor,
        run_postprocessing_pipeline,
    )

    output_path = Path(args.output_path)
    target_date = _parse_date(args.target_date)

    if target_date is None:
        print(
            f"[external-monitor] run_monitor once output={output_path} "
            f"bis_max_pages={args.bis_max_pages}",
            flush=True,
        )
        run_monitor(
            output_path=str(output_path),
            interval_sec=0,
            bis_max_pages=args.bis_max_pages,
            sleep_sec=args.sleep_sec,
            max_cycles=1,
        )
        return 0

    print(
        f"[external-monitor] one-shot target_date={target_date.isoformat()} "
        f"output={output_path}",
        flush=True,
    )
    raw_df = collect_policy_updates(
        bis_max_pages=args.bis_max_pages,
        sleep_sec=args.sleep_sec,
        target_date=target_date,
    )
    if raw_df is None or raw_df.empty:
        print("[external-monitor] no rows collected", flush=True)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        if not output_path.exists():
            raw_df.to_csv(output_path, index=False, encoding="utf-8-sig")
        return 0

    processed = run_postprocessing_pipeline(raw_df)
    total = _append_processed(output_path, processed)
    print(f"[external-monitor] saved rows={total} path={output_path}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
