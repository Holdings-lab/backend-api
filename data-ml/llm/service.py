from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime
from typing import Any

from db.db import (
    fetch_article_insights,
    fetch_home_briefings,
    fetch_policy_feed_frame,
    upsert_article_insight,
    upsert_home_briefing,
)
from .base import LlmApiService
from .providers import build_llm_client
from .prompts import (
    build_article_insight_system_prompt,
    build_article_insight_user_prompt,
    build_home_briefing_system_prompt,
    build_home_briefing_user_prompt,
)


@dataclass(frozen=True)
class ArticleInsightResult:
    document_id: int
    insight_date: str
    summary: str
    keywords: list[str]
    asset_impacts: list[dict[str, Any]]
    llm_provider: str
    llm_model: str
    prompt_version: str
    cached: bool


@dataclass(frozen=True)
class HomeBriefingResult:
    user_id: int
    briefing_date: str
    briefing_headline: str
    briefing_paragraphs: list[str]
    push_data: dict[str, Any]
    llm_provider: str
    llm_model: str
    prompt_version: str
    cached: bool


def _normalize_date(value: date | str | None) -> str:
    if value is None:
        return datetime.utcnow().date().isoformat()
    if isinstance(value, date):
        return value.isoformat()
    text = str(value).strip()
    return text or datetime.utcnow().date().isoformat()


def _dedupe_keywords(values: list[Any]) -> list[str]:
    normalized: list[str] = []
    seen: set[str] = set()
    for value in values:
        text = str(value).strip()
        if not text:
            continue
        lowered = text.lower()
        if lowered in seen:
            continue
        seen.add(lowered)
        normalized.append(text)
    return normalized


class ArticleInsightGenerationService:
    def __init__(self, llm_client: LlmApiService | None = None):
        self.llm_client = llm_client or build_llm_client()
        self.prompt_version = "article-insight-v1"

    def generate_for_date(self, insight_date: date | str | None = None) -> dict[str, Any]:
        target_date = _normalize_date(insight_date)
        source_frame = fetch_policy_feed_frame(date_from=target_date, date_to=target_date, limit=None)
        if source_frame.empty:
            return {
                "status": "empty",
                "insightDate": target_date,
                "items": [],
                "message": "수집된 정책 기사가 없어 LLM 호출을 건너뜁니다.",
            }

        cached_frame = fetch_article_insights(insight_date=target_date)
        cached_document_ids = set(int(value) for value in cached_frame["document_id"].tolist()) if not cached_frame.empty else set()

        items: list[dict[str, Any]] = []
        for _, row in source_frame.iterrows():
            document_id = int(row.get("document_id") or row.get("id") or 0)
            if document_id <= 0:
                continue

            if document_id in cached_document_ids:
                cached_row = cached_frame[cached_frame["document_id"] == document_id].iloc[0].to_dict()
                items.append({
                    "documentId": document_id,
                    "cached": True,
                    "summary": cached_row.get("summary", ""),
                    "keywords": cached_row.get("keywords", []),
                    "assetImpacts": cached_row.get("asset_impacts", []),
                })
                continue

            article_payload = {
                "documentId": document_id,
                "date": row.get("date") or row.get("release_date"),
                "source": row.get("source"),
                "category": row.get("category"),
                "docType": row.get("doc_type"),
                "sector": row.get("sector"),
                "title": row.get("title"),
                "body": row.get("body_summary") or row.get("body"),
                "bodySummary": row.get("body_summary") or row.get("body"),
                "matchedKeywordGroups": row.get("matched_keyword_groups"),
                "matchedKeywords": row.get("matched_keywords"),
            }
            llm_result = self.llm_client.generate_json(
                build_article_insight_system_prompt(),
                build_article_insight_user_prompt(article_payload),
                temperature=0.15,
            )
            keywords = _dedupe_keywords(list(llm_result.get("keywords") or []))
            asset_impacts = list(llm_result.get("assetImpacts") or [])
            summary = str(llm_result.get("summary") or "").strip()
            if not summary:
                summary = str(row.get("body_summary") or row.get("title") or "").strip()

            persisted_id = upsert_article_insight(
                {
                    "document_id": document_id,
                    "insight_date": target_date,
                    "summary": summary,
                    "keywords": keywords,
                    "asset_impacts": asset_impacts,
                    "llm_provider": self.llm_client.provider_name,
                    "llm_model": self.llm_client.model_name,
                    "prompt_version": self.prompt_version,
                    "insight_payload": {
                        "documentId": document_id,
                        "insightDate": target_date,
                        "summary": summary,
                        "keywords": keywords,
                        "assetImpacts": asset_impacts,
                        "tone": llm_result.get("tone"),
                    },
                }
            )
            items.append(
                {
                    "documentId": document_id,
                    "cached": False,
                    "persistedId": persisted_id,
                    "summary": summary,
                    "keywords": keywords,
                    "assetImpacts": asset_impacts,
                }
            )

        return {
            "status": "success",
            "insightDate": target_date,
            "items": items,
            "count": len(items),
            "provider": self.llm_client.provider_name,
            "model": self.llm_client.model_name,
        }


class HomeBriefingGenerationService:
    def __init__(self, llm_client: LlmApiService | None = None):
        self.llm_client = llm_client or build_llm_client()
        self.prompt_version = "home-briefing-v1"

    def generate_for_user(self, user_id: int, briefing_date: date | str | None = None) -> dict[str, Any]:
        target_date = _normalize_date(briefing_date)
        cached_frame = fetch_home_briefings(user_id=user_id, briefing_date=target_date)
        if not cached_frame.empty:
            row = cached_frame.iloc[0].to_dict()
            return {
                "status": "cached",
                "userId": int(user_id),
                "briefingDate": target_date,
                "headline": row.get("briefing_headline", ""),
                "paragraphs": row.get("briefing_paragraphs", []),
                "pushData": row.get("push_data", {}),
                "provider": row.get("llm_provider", ""),
                "model": row.get("llm_model", ""),
            }

        source_frame = fetch_policy_feed_frame(limit=3)
        if source_frame.empty:
            return {
                "status": "empty",
                "userId": int(user_id),
                "briefingDate": target_date,
                "message": "브리핑을 만들 정책 데이터가 없어 LLM 호출을 건너뜁니다.",
            }

        snapshot = {
            "userId": int(user_id),
            "briefingDate": target_date,
            "latestArticles": source_frame.head(3).to_dict(orient="records"),
            "totalCount": int(len(source_frame)),
        }
        llm_result = self.llm_client.generate_json(
            build_home_briefing_system_prompt(),
            build_home_briefing_user_prompt(snapshot),
            temperature=0.2,
        )

        headline = str(llm_result.get("headline") or "").strip()
        if not headline:
            headline = "오늘의 정책 흐름 브리핑"
        paragraphs = [str(item).strip() for item in (llm_result.get("paragraphs") or []) if str(item).strip()]
        push_data = {
            "title": str(llm_result.get("pushTitle") or headline).strip(),
            "body": str(llm_result.get("pushBody") or headline).strip(),
            "tone": llm_result.get("briefingTone") or "neutral",
        }

        persisted_id = upsert_home_briefing(
            {
                "user_id": int(user_id),
                "briefing_date": target_date,
                "briefing_headline": headline,
                "briefing_paragraphs": paragraphs,
                "push_data": push_data,
                "llm_provider": self.llm_client.provider_name,
                "llm_model": self.llm_client.model_name,
                "prompt_version": self.prompt_version,
                "briefing_payload": {
                    "userId": int(user_id),
                    "briefingDate": target_date,
                    "headline": headline,
                    "paragraphs": paragraphs,
                    "pushData": push_data,
                    "sourceSnapshot": snapshot,
                },
            }
        )

        return {
            "status": "success",
            "userId": int(user_id),
            "briefingDate": target_date,
            "headline": headline,
            "paragraphs": paragraphs,
            "pushData": push_data,
            "persistedId": persisted_id,
            "provider": self.llm_client.provider_name,
            "model": self.llm_client.model_name,
        }


def create_llm_service(kind: str) -> LlmApiService:
    client = build_llm_client()
    if kind == "article":
        return ArticleInsightGenerationService(client)  # type: ignore[return-value]
    return HomeBriefingGenerationService(client)  # type: ignore[return-value]