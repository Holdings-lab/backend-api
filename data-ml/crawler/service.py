from __future__ import annotations

import re
from datetime import datetime, timedelta
from pathlib import Path
import random

import numpy as np
import pandas as pd
from transformers import pipeline


BASE_DIR = Path(__file__).resolve().parents[1]
MERGED_FINBERT_PATH = BASE_DIR / "merged_finbert.csv"
DAILY_NEWS_FEATURES_PATH = BASE_DIR / "data" / "crawler" / "features" / "daily_news_features.csv"
MODEL_NAME = "ProsusAI/finbert"
MAX_CHARS_PER_CHUNK = 800
BATCH_SIZE = 8

_classifier = None

FAKE_POLICY_TEMPLATES = [
    {
        "category": "FOMC",
        "doc_type": "statement",
        "title": "Federal Reserve indicates policy continuity amid inflation moderation",
        "body": "The Federal Reserve signaled a stable rate path while monitoring inflation and labor market risks.",
    },
    {
        "category": "BIS",
        "doc_type": "report",
        "title": "BIS warns of liquidity tightening in risk assets",
        "body": "BIS commentary highlighted tighter funding conditions and elevated downside volatility in global equities.",
    },
    {
        "category": "White House",
        "doc_type": "briefing",
        "title": "White House outlines semiconductor and infrastructure investment support",
        "body": "Government policy update emphasized long-term productivity initiatives and supply-chain resilience.",
    },
]


def _get_classifier():
    global _classifier
    if _classifier is None:
        _classifier = pipeline(
            task="text-classification",
            model=MODEL_NAME,
            tokenizer=MODEL_NAME,
        )
    return _classifier


def _clean_text(text: str) -> str:
    if pd.isna(text):
        return ""
    return re.sub(r"\s+", " ", str(text)).strip()


def _split_text_into_chunks(text: str, max_chars: int = MAX_CHARS_PER_CHUNK) -> list[str]:
    text = _clean_text(text)
    if not text:
        return []

    sentences = re.split(r"(?<=[.!?])\s+", text)
    chunks = []
    current_chunk = ""

    for sentence in sentences:
        sentence = sentence.strip()
        if not sentence:
            continue

        if len(sentence) > max_chars:
            if current_chunk:
                chunks.append(current_chunk.strip())
                current_chunk = ""
            for index in range(0, len(sentence), max_chars):
                piece = sentence[index:index + max_chars].strip()
                if piece:
                    chunks.append(piece)
            continue

        if len(current_chunk) + len(sentence) + 1 <= max_chars:
            current_chunk = f"{current_chunk} {sentence}".strip()
        else:
            if current_chunk:
                chunks.append(current_chunk.strip())
            current_chunk = sentence

    if current_chunk:
        chunks.append(current_chunk.strip())

    return chunks


def _extract_probs_from_output(output_one_text) -> dict:
    if isinstance(output_one_text, dict):
        raw_items = [output_one_text]
    elif isinstance(output_one_text, list) and output_one_text and isinstance(output_one_text[0], list):
        raw_items = output_one_text[0]
    elif isinstance(output_one_text, list):
        raw_items = output_one_text
    else:
        raw_items = []

    score_map = {
        str(item.get("label", "")).lower(): float(item.get("score", 0.0))
        for item in raw_items
        if isinstance(item, dict)
    }
    positive = float(score_map.get("positive", 0.0))
    negative = float(score_map.get("negative", 0.0))
    neutral = float(score_map.get("neutral", 0.0))
    return {
        "positive_prob": positive,
        "negative_prob": negative,
        "neutral_prob": neutral,
        "sentiment_score": positive - negative,
    }


def _empty_scores() -> dict:
    return {
        "positive_prob": None,
        "negative_prob": None,
        "neutral_prob": None,
        "sentiment_score": None,
    }


def _classify_texts(text_list: list[str], batch_size: int = BATCH_SIZE) -> list[dict]:
    cleaned = [_clean_text(text) for text in text_list]
    non_empty_indices = [index for index, text in enumerate(cleaned) if text]
    results = [None] * len(cleaned)

    if non_empty_indices:
        classifier = _get_classifier()
        non_empty_texts = [cleaned[index] for index in non_empty_indices]
        outputs = classifier(
            non_empty_texts,
            top_k=None,
            batch_size=batch_size,
            truncation=True,
        )
        for index, output in zip(non_empty_indices, outputs):
            results[index] = _extract_probs_from_output(output)

    for index, result in enumerate(results):
        if result is None:
            results[index] = _empty_scores()

    return results


def _weighted_average_scores(score_dicts: list[dict], weights: list[int]) -> dict:
    if not score_dicts or not weights or len(score_dicts) != len(weights):
        return _empty_scores()

    total_weight = sum(weights)
    if total_weight <= 0:
        return _empty_scores()

    positive = sum(score["positive_prob"] * weight for score, weight in zip(score_dicts, weights)) / total_weight
    negative = sum(score["negative_prob"] * weight for score, weight in zip(score_dicts, weights)) / total_weight
    neutral = sum(score["neutral_prob"] * weight for score, weight in zip(score_dicts, weights)) / total_weight
    return {
        "positive_prob": positive,
        "negative_prob": negative,
        "neutral_prob": neutral,
        "sentiment_score": positive - negative,
    }


def _analyze_titles(titles: list[str]) -> list[dict]:
    title_scores = _classify_texts(titles, batch_size=BATCH_SIZE)
    return [
        {
            "title_positive_prob": result["positive_prob"],
            "title_negative_prob": result["negative_prob"],
            "title_neutral_prob": result["neutral_prob"],
            "title_sentiment_score": result["sentiment_score"],
        }
        for result in title_scores
    ]


def _analyze_bodies(bodies: list[str]) -> list[dict]:
    cleaned_bodies = [_clean_text(body) for body in bodies]
    chunks_per_body = []
    all_chunks = []

    for body in cleaned_bodies:
        chunks = _split_text_into_chunks(body, max_chars=MAX_CHARS_PER_CHUNK) if body else []
        chunks_per_body.append(chunks)
        all_chunks.extend(chunks)

    all_chunk_scores = _classify_texts(all_chunks, batch_size=BATCH_SIZE) if all_chunks else []

    results = []
    score_start = 0

    for body, chunks in zip(cleaned_bodies, chunks_per_body):
        if not body or not chunks:
            results.append(
                {
                    "body_positive_prob": None,
                    "body_negative_prob": None,
                    "body_neutral_prob": None,
                    "body_sentiment_score": None,
                    "body_n_chunks": 0,
                }
            )
            continue

        score_end = score_start + len(chunks)
        chunk_scores = all_chunk_scores[score_start:score_end]
        score_start = score_end

        average = _weighted_average_scores(chunk_scores, [len(chunk) for chunk in chunks])
        results.append(
            {
                "body_positive_prob": average["positive_prob"],
                "body_negative_prob": average["negative_prob"],
                "body_neutral_prob": average["neutral_prob"],
                "body_sentiment_score": average["sentiment_score"],
                "body_n_chunks": len(chunks),
            }
        )

    return results


def _build_fake_policy_rows(count: int) -> list[dict]:
    now = datetime.utcnow()
    rows: list[dict] = []
    for idx in range(count):
        template = FAKE_POLICY_TEMPLATES[idx % len(FAKE_POLICY_TEMPLATES)]
        created_at = now - timedelta(minutes=idx * 10)
        title = template["title"]
        body = template["body"]

        date_str = created_at.strftime("%Y-%m-%d")
        rows.append(
            {
                "date": date_str,
                "category": template["category"],
                "doc_type": template["doc_type"],
                "title": title,
                "body": body,
                "link": f"https://policy.local/{template['category'].lower().replace(' ', '-')}/{created_at.strftime('%Y%m%d%H%M%S')}",
                "body_original_length": len(body),
                "day_of_week": created_at.strftime("%A"),
                "month": created_at.month,
                "is_weekend": created_at.weekday() >= 5,
            }
        )
    return rows


def _build_daily_news_features(merged_df: pd.DataFrame) -> pd.DataFrame:
    df = merged_df.copy()
    df["date"] = pd.to_datetime(df["date"], errors="coerce")
    df = df[df["date"].notna()].copy()
    if df.empty:
        return pd.DataFrame(
            columns=[
                "date",
                "news_count",
                "category_BIS",
                "category_FOMC",
                "category_UCSB",
                "day_of_week_sin",
                "day_of_week_cos",
                "month_sin",
                "month_cos",
                "is_weekend",
                "title_positive_prob",
                "title_negative_prob",
                "title_neutral_prob",
                "title_sentiment_score",
                "body_positive_prob",
                "body_negative_prob",
                "body_neutral_prob",
                "body_sentiment_score",
                "body_n_chunks",
            ]
        )

    for col in [
        "title_positive_prob",
        "title_negative_prob",
        "title_neutral_prob",
        "title_sentiment_score",
        "body_positive_prob",
        "body_negative_prob",
        "body_neutral_prob",
        "body_sentiment_score",
        "body_n_chunks",
    ]:
        if col not in df.columns:
            df[col] = 0.0
        df[col] = pd.to_numeric(df[col], errors="coerce").fillna(0.0)

    category = df.get("category", "").fillna("").astype(str).str.upper()
    df["category_BIS"] = (category == "BIS").astype(float)
    df["category_FOMC"] = (category == "FOMC").astype(float)
    df["category_UCSB"] = (category == "UCSB").astype(float)

    agg = (
        df.groupby(df["date"].dt.date)
        .agg(
            news_count=("title", "count"),
            category_BIS=("category_BIS", "mean"),
            category_FOMC=("category_FOMC", "mean"),
            category_UCSB=("category_UCSB", "mean"),
            title_positive_prob=("title_positive_prob", "mean"),
            title_negative_prob=("title_negative_prob", "mean"),
            title_neutral_prob=("title_neutral_prob", "mean"),
            title_sentiment_score=("title_sentiment_score", "mean"),
            body_positive_prob=("body_positive_prob", "mean"),
            body_negative_prob=("body_negative_prob", "mean"),
            body_neutral_prob=("body_neutral_prob", "mean"),
            body_sentiment_score=("body_sentiment_score", "mean"),
            body_n_chunks=("body_n_chunks", "mean"),
        )
        .reset_index()
        .rename(columns={"date": "date"})
    )

    agg["date"] = pd.to_datetime(agg["date"], errors="coerce")
    agg["is_weekend"] = (agg["date"].dt.weekday >= 5).astype(float)
    day = agg["date"].dt.weekday
    month = agg["date"].dt.month
    agg["day_of_week_sin"] = np.sin(2 * np.pi * day / 7)
    agg["day_of_week_cos"] = np.cos(2 * np.pi * day / 7)
    agg["month_sin"] = np.sin(2 * np.pi * month / 12)
    agg["month_cos"] = np.cos(2 * np.pi * month / 12)
    agg["date"] = agg["date"].dt.strftime("%Y-%m-%d")

    ordered_cols = [
        "date",
        "news_count",
        "category_BIS",
        "category_FOMC",
        "category_UCSB",
        "day_of_week_sin",
        "day_of_week_cos",
        "month_sin",
        "month_cos",
        "is_weekend",
        "title_positive_prob",
        "title_negative_prob",
        "title_neutral_prob",
        "title_sentiment_score",
        "body_positive_prob",
        "body_negative_prob",
        "body_neutral_prob",
        "body_sentiment_score",
        "body_n_chunks",
    ]
    return agg[ordered_cols].sort_values("date")


def run_crawl_now() -> dict:
    rows = _build_fake_policy_rows(count=12)
    base_df = pd.DataFrame(rows)

    # Keep fake policy source, but convert text features with FinBERT like root/data-ml.
    title_df = pd.DataFrame(_analyze_titles(base_df["title"].tolist()))
    body_df = pd.DataFrame(_analyze_bodies(base_df["body"].tolist()))
    new_df = pd.concat([base_df, title_df, body_df], axis=1)

    if MERGED_FINBERT_PATH.exists():
        old_df = pd.read_csv(MERGED_FINBERT_PATH)
        merged = pd.concat([new_df, old_df], ignore_index=True)
        merged = merged.drop_duplicates(subset=["date", "title", "link"], keep="first")
    else:
        merged = new_df

    merged = merged.sort_values(by=["date", "title"], ascending=[False, True]).head(1500)
    merged.to_csv(MERGED_FINBERT_PATH, index=False, encoding="utf-8-sig")

    daily_df = _build_daily_news_features(merged)
    DAILY_NEWS_FEATURES_PATH.parent.mkdir(parents=True, exist_ok=True)
    daily_df.to_csv(DAILY_NEWS_FEATURES_PATH, index=False, encoding="utf-8-sig")

    return {
        "status": "success",
        "message": "가짜 정책 데이터를 생성하고 FinBERT로 피처 변환 후 merged_finbert.csv에 저장했습니다.",
        "generated_count": int(len(new_df)),
        "total_rows": int(len(merged)),
        "output_path": str(MERGED_FINBERT_PATH),
        "daily_features_path": str(DAILY_NEWS_FEATURES_PATH),
        "executed_at": datetime.utcnow().isoformat() + "Z",
    }
