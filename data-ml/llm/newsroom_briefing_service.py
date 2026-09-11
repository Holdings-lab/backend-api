from __future__ import annotations

import importlib.util
import json
import logging
import os
import re
import sys
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Any

import pandas as pd

from db.db import (
    fetch_latest_prediction_summary,
    fetch_news_frame_for_sector,
    fetch_sector_ai_briefings,
    fetch_sector_daily_summaries,
    list_distinct_sectors,
    upsert_sector_ai_briefing,
    upsert_sector_daily_summary,
)

logger = logging.getLogger(__name__)

DEFAULT_DAILY_WINDOW = 1
DEFAULT_AI_NEWS_WINDOW = 5
DEFAULT_MODEL = "claude-haiku-4-5-20251001"


def _as_date(value: date | datetime | str | None, fallback: date | None = None) -> date:
    if value is None:
        return fallback or datetime.utcnow().date()
    if isinstance(value, datetime):
        return value.date()
    if isinstance(value, date):
        return value
    parsed = datetime.fromisoformat(str(value)[:10])
    return parsed.date()


def _crawler_app_root() -> Path:
    raw = (os.getenv("CRAWLER_APP_ROOT") or "/opt/riseai/apps/crawler").strip()
    return Path(raw).resolve()


def _load_apps_crawler_module(module_file_stem: str):
    """
    /opt/riseai/apps/crawler 의 postprocessing 모듈을 로드한다.
    data-ml 내 crawler 패키지를 가리지 않도록 로드 후 sys.path를 복구한다.
    """
    crawler_root = _crawler_app_root()
    module_path = crawler_root / "crawler" / "postprocessing" / f"{module_file_stem}.py"
    if not module_path.exists():
        raise FileNotFoundError(
            f"apps crawler 모듈이 없습니다: {module_path} (CRAWLER_APP_ROOT={crawler_root})"
        )

    full_name = f"apps_crawler_postprocessing_{module_file_stem}"
    if full_name in sys.modules:
        return sys.modules[full_name]

    inserted = str(crawler_root)
    already_present = inserted in sys.path
    if not already_present:
        sys.path.insert(0, inserted)
    try:
        spec = importlib.util.spec_from_file_location(full_name, module_path)
        if spec is None or spec.loader is None:
            raise ImportError(f"모듈 스펙 생성 실패: {module_path}")
        module = importlib.util.module_from_spec(spec)
        sys.modules[full_name] = module
        spec.loader.exec_module(module)
        return module
    finally:
        if not already_present:
            try:
                sys.path.remove(inserted)
            except ValueError:
                pass


def _parse_json_text(raw: str) -> dict[str, Any]:
    cleaned = re.sub(r"^```(?:json)?\s*|\s*```$", "", str(raw or "").strip(), flags=re.DOTALL)
    parsed = json.loads(cleaned)
    if not isinstance(parsed, dict):
        raise ValueError("LLM 응답이 JSON 객체가 아닙니다.")
    return parsed


def _clean_url(value: object) -> str:
    return str(value or "").strip().strip("<>").strip()


def _normalize_prediction_for_apps(prediction: dict[str, Any], sector: str, as_of: date) -> dict[str, Any]:
    """policy_prediction_runs / 테스트 JSON을 apps ai_analysis 입력 형태로 맞춘다."""
    if "prediction" in prediction and ("asset" in prediction or "prediction_date" in prediction):
        payload = dict(prediction)
    else:
        metrics = prediction.get("metrics") or {}
        policy_score = float(metrics.get("policyScore") or 0.0)
        up_probability = float(metrics.get("topLabelProbability") or metrics.get("directionAccuracy") or 0.5)
        if policy_score > 0:
            direction = "UP"
        elif policy_score < 0:
            direction = "DOWN"
        else:
            direction = "FLAT"
        horizon_days = int(prediction.get("bestHorizonDays") or 0)
        payload = {
            "asset": str(sector or prediction.get("targetTicker") or "QQQ").upper(),
            "prediction_date": as_of.isoformat(),
            "as_of_date": as_of.isoformat(),
            "news_window_days": int(prediction.get("news_window_days") or DEFAULT_AI_NEWS_WINDOW),
            "horizon": f"{horizon_days}일" if horizon_days else "",
            "horizon_days": horizon_days,
            "prediction": {
                "direction": direction,
                "up_probability": up_probability if direction != "DOWN" else max(0.0, 1.0 - up_probability),
                "expected_return_pct": round(policy_score * 100, 4),
            },
            "market_data": prediction.get("market_data") or {},
        }

    payload["asset"] = str(sector or payload.get("asset") or "QQQ").upper()
    payload["prediction_date"] = str(payload.get("prediction_date") or as_of.isoformat())
    payload["as_of_date"] = str(payload.get("as_of_date") or payload["prediction_date"])
    payload.setdefault("news_window_days", DEFAULT_AI_NEWS_WINDOW)
    return payload


def _call_daily_news_summary(
    *,
    sector: str,
    as_of: date,
    window_days: int,
    news_df: pd.DataFrame,
    model: str,
) -> dict[str, Any]:
    module = _load_apps_crawler_module("daily_news_summary")
    summarize = getattr(module, "summarize_news_for_date")
    default_model = getattr(module, "DEFAULT_MODEL", DEFAULT_MODEL)

    kwargs: dict[str, Any] = {
        "target_sector": sector,
        "target_date": datetime.combine(as_of, datetime.min.time()),
        "window": int(window_days),
        "news_df": news_df,
    }
    # 서버 원본은 model 인자가 없을 수 있음
    try:
        import inspect

        if "model" in inspect.signature(summarize).parameters:
            kwargs["model"] = model or default_model
    except Exception:
        pass

    raw = summarize(**kwargs)
    if isinstance(raw, str):
        parsed = _parse_json_text(raw)
    elif isinstance(raw, dict):
        parsed = raw
    else:
        raise ValueError("daily_news_summary 반환 형식이 올바르지 않습니다.")

    image = parsed.get("image") or parsed.get("image_url")
    return {
        "sector": str(parsed.get("sector") or sector).lower(),
        "release_date": str(parsed.get("release_date") or as_of.isoformat()),
        "window_days": int(window_days),
        "title": str(parsed.get("title") or "").strip(),
        "content": str(parsed.get("content") or "").strip(),
        "image": image,
        "image_url": image,
        "source_count": int(parsed.get("source_count") or len(news_df)),
        "llm_provider": "anthropic",
        "llm_model": model or default_model,
        "summary_payload": parsed,
    }


def _call_ai_analysis(
    *,
    sector: str,
    as_of: date,
    news_window_days: int,
    news_df: pd.DataFrame,
    prediction: dict[str, Any],
    model: str,
) -> dict[str, Any]:
    module = _load_apps_crawler_module("ai_analysis")
    get_news = getattr(module, "_get_news")
    insert_news = getattr(module, "insert_news_to_payload")
    call_claude = getattr(module, "_call_claude_summary")
    fixed_prompt = getattr(module, "FIXED_PROMPT")
    default_model = getattr(module, "DEFAULT_MODEL", DEFAULT_MODEL)

    target_dt = datetime.combine(as_of, datetime.min.time())
    filtered_news = get_news(sector, target_dt, int(news_window_days), news_df)
    payload = _normalize_prediction_for_apps(prediction, sector=sector, as_of=as_of)
    payload_text = insert_news(json.dumps(payload, ensure_ascii=False), filtered_news)
    raw = call_claude(fixed_prompt + payload_text, model=model or default_model)
    parsed = _parse_json_text(raw)

    used_urls = parsed.get("used_news_url") or parsed.get("used_news_urls") or []
    if not isinstance(used_urls, list):
        used_urls = []
    used_urls = [_clean_url(url) for url in used_urls if _clean_url(url)]

    return {
        "sector": sector.lower(),
        "as_of_date": as_of.isoformat(),
        "horizon_days": int(payload.get("horizon_days") or 0),
        "title": str(parsed.get("title") or "AI는 이렇게 판단했어요").strip(),
        "headline": str(parsed.get("headline") or "").strip(),
        "reason": str(parsed.get("reason") or "").strip(),
        "alignment": str(parsed.get("alignment") or "").strip() or None,
        "used_news_urls": used_urls,
        "disclaimer": str(parsed.get("disclaimer") or "본 내용은 투자 판단의 근거가 아닙니다.").strip(),
        "llm_provider": "anthropic",
        "llm_model": model or default_model,
        "briefing_payload": {"prediction": payload, "raw": parsed},
    }


def generate_and_store_daily_summaries(
    target_date: date | datetime | str | None = None,
    window_days: int = DEFAULT_DAILY_WINDOW,
    sectors: list[str] | None = None,
    model: str = DEFAULT_MODEL,
) -> dict[str, Any]:
    as_of = _as_date(target_date, fallback=datetime.utcnow().date() - timedelta(days=1))
    sector_list = [s.lower() for s in (sectors or list_distinct_sectors()) if str(s).strip()]
    stored: list[dict[str, Any]] = []
    errors: list[dict[str, str]] = []

    for sector in sector_list:
        try:
            news_df = fetch_news_frame_for_sector(
                sector=sector,
                date_from=as_of - timedelta(days=max(0, int(window_days))),
                date_to=as_of,
                limit=100,
            )
            summary = _call_daily_news_summary(
                sector=sector,
                as_of=as_of,
                window_days=window_days,
                news_df=news_df,
                model=model,
            )
            if not summary.get("title") or not summary.get("content"):
                raise ValueError("요약 결과에 title/content가 비어 있습니다.")
            summary_id = upsert_sector_daily_summary(summary)
            stored.append({"sector": sector, "id": summary_id, "title": summary.get("title")})
        except Exception as error:
            logger.warning("[DailySummary] sector=%s failed: %s", sector, error)
            errors.append({"sector": sector, "error": str(error)})

    return {
        "status": "success" if stored else "failed",
        "release_date": as_of.isoformat(),
        "window_days": int(window_days),
        "crawlerAppRoot": str(_crawler_app_root()),
        "stored_count": len(stored),
        "stored": stored,
        "errors": errors,
    }


def generate_and_store_ai_briefings(
    prediction_summary: dict[str, Any] | None = None,
    target_date: date | datetime | str | None = None,
    news_window_days: int = DEFAULT_AI_NEWS_WINDOW,
    sectors: list[str] | None = None,
    model: str = DEFAULT_MODEL,
) -> dict[str, Any]:
    prediction = prediction_summary or fetch_latest_prediction_summary()
    if not prediction:
        return {
            "status": "skipped",
            "message": "prediction summary not found",
            "stored_count": 0,
            "stored": [],
            "errors": [],
        }

    as_of = _as_date(target_date)
    if target_date is None:
        for key in ("as_of_date", "asOfDate", "prediction_date", "generatedAt"):
            if prediction.get(key):
                as_of = _as_date(prediction.get(key), fallback=as_of)
                break

    sector_list = [s.lower() for s in (sectors or list_distinct_sectors()) if str(s).strip()]
    target_ticker = str(prediction.get("targetTicker") or prediction.get("asset") or "").strip().lower()
    if target_ticker and target_ticker not in sector_list:
        sector_list = [target_ticker, *sector_list]

    stored: list[dict[str, Any]] = []
    errors: list[dict[str, str]] = []

    for sector in sector_list:
        try:
            news_df = fetch_news_frame_for_sector(
                sector=sector,
                date_from=as_of - timedelta(days=max(0, int(news_window_days))),
                date_to=as_of,
                limit=100,
            )
            briefing = _call_ai_analysis(
                sector=sector,
                as_of=as_of,
                news_window_days=news_window_days,
                news_df=news_df,
                prediction=prediction,
                model=model,
            )
            briefing_id = upsert_sector_ai_briefing(briefing)
            stored.append(
                {
                    "sector": sector,
                    "id": briefing_id,
                    "headline": briefing.get("headline"),
                    "alignment": briefing.get("alignment"),
                }
            )
        except Exception as error:
            logger.warning("[AiBriefing] sector=%s failed: %s", sector, error)
            errors.append({"sector": sector, "error": str(error)})

    return {
        "status": "success" if stored else "failed",
        "as_of_date": as_of.isoformat(),
        "news_window_days": int(news_window_days),
        "crawlerAppRoot": str(_crawler_app_root()),
        "stored_count": len(stored),
        "stored": stored,
        "errors": errors,
    }


def get_sector_briefings_bundle(
    sectors: list[str],
    briefing_date: date | datetime | str | None = None,
    window_days: int = DEFAULT_DAILY_WINDOW,
) -> dict[str, Any]:
    as_of = _as_date(briefing_date, fallback=datetime.utcnow().date())
    normalized = [str(s).strip().lower() for s in sectors if str(s).strip()]
    daily_df = fetch_sector_daily_summaries(
        sectors=normalized or None,
        release_date=as_of,
        window_days=window_days,
    )
    ai_df = fetch_sector_ai_briefings(
        sectors=normalized or None,
        as_of_date=as_of,
    )

    if daily_df.empty and normalized:
        daily_df = fetch_sector_daily_summaries(sectors=normalized, release_date=None, window_days=window_days)
        if not daily_df.empty:
            daily_df = daily_df.sort_values(["sector", "release_date"], ascending=[True, False]).drop_duplicates(
                "sector", keep="first"
            )
    if ai_df.empty and normalized:
        ai_df = fetch_sector_ai_briefings(sectors=normalized, as_of_date=None)
        if not ai_df.empty:
            ai_df = ai_df.sort_values(["sector", "as_of_date"], ascending=[True, False]).drop_duplicates(
                "sector", keep="first"
            )

    result: dict[str, Any] = {}
    for sector in normalized:
        result[sector] = {"dailySummary": None, "aiBriefing": None}

    if not daily_df.empty:
        for _, row in daily_df.iterrows():
            sector = str(row.get("sector") or "").lower()
            if normalized and sector not in result:
                continue
            result.setdefault(sector, {"dailySummary": None, "aiBriefing": None})
            image = row.get("image_url")
            if str(image).strip().lower() in {"", "nan", "none", "null"}:
                image = None
            result[sector]["dailySummary"] = {
                "sector": sector,
                "releaseDate": str(row.get("release_date")),
                "windowDays": int(row.get("window_days") or window_days),
                "title": row.get("title"),
                "content": row.get("content"),
                "imageUrl": image,
                "sourceCount": int(row.get("source_count") or 0),
            }

    if not ai_df.empty:
        for _, row in ai_df.iterrows():
            sector = str(row.get("sector") or "").lower()
            if normalized and sector not in result:
                continue
            result.setdefault(sector, {"dailySummary": None, "aiBriefing": None})
            result[sector]["aiBriefing"] = {
                "sector": sector,
                "asOfDate": str(row.get("as_of_date")),
                "horizonDays": int(row.get("horizon_days") or 0),
                "title": row.get("title"),
                "headline": row.get("headline"),
                "reason": row.get("reason"),
                "alignment": row.get("alignment"),
                "usedNewsUrls": row.get("used_news_urls") or [],
                "disclaimer": row.get("disclaimer"),
            }

    return {
        "briefingDate": as_of.isoformat(),
        "sectors": result,
    }
