from __future__ import annotations

import json
import os
from datetime import date, datetime
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

try:
    import psycopg2
    from psycopg2 import extras
except ImportError:  # pragma: no cover - dependency is expected in the runtime venv
    psycopg2 = None
    extras = None


PROJECT_ROOT = Path(__file__).resolve().parents[1]
SCHEMA_PATH = Path(__file__).with_name("schema.sql")


def _env(*names: str, default: str = "") -> str:
    for name in names:
        value = os.getenv(name)
        if value:
            return value
    return default


def _db_config() -> dict[str, Any]:
    return {
        "host": _env("DB_HOST", "POSTGRES_HOST", default="localhost"),
        "port": int(_env("DB_PORT", "POSTGRES_PORT", default="5432")),
        "dbname": _env("DB_NAME", "POSTGRES_DB", "PGDATABASE", default="holdings"),
        "user": _env("DB_USER", "POSTGRES_USER", default="postgres"),
        "password": _env("DB_PASSWORD", "POSTGRES_PASSWORD", default="postgres"),
    }


def _connect(real_dict_cursor: bool = False):
    if psycopg2 is None:
        raise RuntimeError("psycopg2 is not installed")

    config = _db_config()
    if real_dict_cursor:
        return psycopg2.connect(**config, cursor_factory=extras.RealDictCursor)
    return psycopg2.connect(**config)


def init_db() -> None:
    if psycopg2 is None:
        raise RuntimeError("psycopg2 is not installed")

    schema_sql = SCHEMA_PATH.read_text(encoding="utf-8")
    with _connect() as conn:
        with conn.cursor() as cursor:
            cursor.execute(schema_sql)
            cursor.execute(
                """
                ALTER TABLE IF EXISTS crawler_run_logs
                    ADD COLUMN IF NOT EXISTS run_type TEXT,
                    ADD COLUMN IF NOT EXISTS status TEXT,
                    ADD COLUMN IF NOT EXISTS counts JSONB NOT NULL DEFAULT '{}'::jsonb,
                    ADD COLUMN IF NOT EXISTS payload JSONB NOT NULL DEFAULT '{}'::jsonb,
                    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
                ALTER TABLE IF EXISTS policy_prediction_runs
                    ADD COLUMN IF NOT EXISTS model_version TEXT,
                    ADD COLUMN IF NOT EXISTS model_target TEXT,
                    ADD COLUMN IF NOT EXISTS best_horizon_days INTEGER,
                    ADD COLUMN IF NOT EXISTS best_threshold DOUBLE PRECISION,
                    ADD COLUMN IF NOT EXISTS policy_score DOUBLE PRECISION,
                    ADD COLUMN IF NOT EXISTS direction_accuracy DOUBLE PRECISION,
                    ADD COLUMN IF NOT EXISTS top_label TEXT,
                    ADD COLUMN IF NOT EXISTS top_label_probability DOUBLE PRECISION,
                    ADD COLUMN IF NOT EXISTS summary_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
                    ADD COLUMN IF NOT EXISTS metadata_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
                    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
                """
            )
            cursor.execute(
                """
                DELETE FROM policy_document_features a
                USING policy_document_features b
                WHERE a.document_id = b.document_id
                  AND a.id > b.id;
                CREATE UNIQUE INDEX IF NOT EXISTS policy_document_features_document_id_idx
                    ON policy_document_features(document_id);
                """
            )
        conn.commit()


def _to_jsonable(value: Any) -> Any:
    if value is None:
        return None

    if isinstance(value, pd.Timestamp):
        if pd.isna(value):
            return None
        return value.isoformat()

    if isinstance(value, (datetime, date)):
        return value.isoformat()

    if isinstance(value, np.generic):
        return value.item()

    if isinstance(value, np.ndarray):
        return value.tolist()

    if isinstance(value, dict):
        return {str(key): _to_jsonable(item) for key, item in value.items()}

    if isinstance(value, (list, tuple)):
        return [_to_jsonable(item) for item in value]

    try:
        if pd.isna(value):
            return None
    except Exception:
        pass

    return value


def _row_to_dict(row: Any) -> dict[str, Any]:
    if isinstance(row, pd.Series):
        data = row.to_dict()
    elif isinstance(row, dict):
        data = dict(row)
    else:
        data = dict(row)

    return {key: _to_jsonable(value) for key, value in data.items()}


def _json_param(value: Any) -> Any:
    return extras.Json(_to_jsonable(value), dumps=lambda obj: json.dumps(obj, ensure_ascii=False))


def _safe_int(value: Any, default: int | None = None) -> int | None:
    try:
        if value is None or pd.isna(value):
            return default
        return int(value)
    except Exception:
        return default


def _safe_float(value: Any, default: float | None = None) -> float | None:
    try:
        if value is None or pd.isna(value):
            return default
        return float(value)
    except Exception:
        return default


def _safe_str(value: Any, default: str | None = None) -> str | None:
    if value is None:
        return default
    text = str(value).strip()
    return text if text else default


def _safe_date(value: Any) -> date | None:
    if value is None or value == "":
        return None
    parsed = pd.to_datetime(value, errors="coerce")
    if pd.isna(parsed):
        return None
    return parsed.date()


def fetch_user_watch_asset_names(user_id: int) -> list[str]:
    if psycopg2 is None:
        return []

    try:
        with _connect(real_dict_cursor=True) as conn:
            with conn.cursor() as cursor:
                cursor.execute(
                    """
                    SELECT asset_name
                    FROM user_watch_assets
                    WHERE user_id = %s
                    ORDER BY display_order ASC, id ASC
                    """,
                    (int(user_id),),
                )
                rows = cursor.fetchall()
                names = [str(row["asset_name"]).strip() for row in rows if row.get("asset_name")]
                if names:
                    return names

            with conn.cursor() as cursor:
                cursor.execute(
                    """
                    SELECT asset_name
                    FROM watch_asset_catalog
                    ORDER BY display_order ASC, id ASC
                    LIMIT 3
                    """
                )
                rows = cursor.fetchall()
                return [str(row["asset_name"]).strip() for row in rows if row.get("asset_name")]
    except Exception:
        return []


def upsert_policy_document(record: dict[str, Any]) -> int:
    if psycopg2 is None:
        raise RuntimeError("psycopg2 is not installed")

    payload = _row_to_dict(record)
    published_date = _safe_date(payload.get("published_date") or payload.get("release_date"))
    collected_at = payload.get("collected_at")
    body_text = _safe_str(payload.get("body"), "") or ""

    query = """
        INSERT INTO policy_documents (
            source, category, doc_type, published_date, release_date,
            title, url, body, matched_keyword_groups, matched_keywords,
            collected_at, raw_payload, updated_at
        ) VALUES (
            %(source)s, %(category)s, %(doc_type)s, %(published_date)s, %(release_date)s,
            %(title)s, %(url)s, %(body)s, %(matched_keyword_groups)s, %(matched_keywords)s,
            %(collected_at)s, %(raw_payload)s, NOW()
        )
        ON CONFLICT (url) DO UPDATE SET
            source = EXCLUDED.source,
            category = EXCLUDED.category,
            doc_type = EXCLUDED.doc_type,
            published_date = COALESCE(EXCLUDED.published_date, policy_documents.published_date),
            release_date = COALESCE(NULLIF(EXCLUDED.release_date, ''), policy_documents.release_date),
            title = COALESCE(NULLIF(EXCLUDED.title, ''), policy_documents.title),
            body = CASE
                WHEN EXCLUDED.body IS NOT NULL AND EXCLUDED.body <> '' THEN EXCLUDED.body
                ELSE policy_documents.body
            END,
            matched_keyword_groups = COALESCE(NULLIF(EXCLUDED.matched_keyword_groups, ''), policy_documents.matched_keyword_groups),
            matched_keywords = COALESCE(NULLIF(EXCLUDED.matched_keywords, ''), policy_documents.matched_keywords),
            collected_at = COALESCE(EXCLUDED.collected_at, policy_documents.collected_at),
            raw_payload = EXCLUDED.raw_payload,
            updated_at = NOW()
        RETURNING id
    """

    params = {
        "source": _safe_str(payload.get("source"), "unknown"),
        "category": _safe_str(payload.get("category"), "unknown"),
        "doc_type": _safe_str(payload.get("doc_type"), "unknown"),
        "published_date": published_date,
        "release_date": _safe_str(payload.get("release_date"), "") or "",
        "title": _safe_str(payload.get("title"), "") or "",
        "url": _safe_str(payload.get("url"), "") or "",
        "body": body_text,
        "matched_keyword_groups": _safe_str(payload.get("matched_keyword_groups"), "") or "",
        "matched_keywords": _safe_str(payload.get("matched_keywords"), "") or "",
        "collected_at": collected_at,
        "raw_payload": extras.Json(payload, dumps=lambda obj: json.dumps(obj, ensure_ascii=False)),
    }

    with _connect() as conn:
        with conn.cursor() as cursor:
            cursor.execute(query, params)
            row = cursor.fetchone()
        conn.commit()
        return int(row[0])


def upsert_policy_document_features(document_id: int, record: dict[str, Any]) -> None:
    if psycopg2 is None:
        raise RuntimeError("psycopg2 is not installed")

    payload = _row_to_dict(record)
    query = """
        INSERT INTO policy_document_features (
            document_id, body_summary, body_original_length,
            title_positive_prob, title_negative_prob, title_neutral_prob, title_sentiment_score,
            body_positive_prob, body_negative_prob, body_neutral_prob, body_sentiment_score, body_n_chunks,
            body_summary_embedding, feature_payload, updated_at
        ) VALUES (
            %(document_id)s, %(body_summary)s, %(body_original_length)s,
            %(title_positive_prob)s, %(title_negative_prob)s, %(title_neutral_prob)s, %(title_sentiment_score)s,
            %(body_positive_prob)s, %(body_negative_prob)s, %(body_neutral_prob)s, %(body_sentiment_score)s, %(body_n_chunks)s,
            %(body_summary_embedding)s, %(feature_payload)s, NOW()
        )
        ON CONFLICT (document_id) DO UPDATE SET
            body_summary = EXCLUDED.body_summary,
            body_original_length = EXCLUDED.body_original_length,
            title_positive_prob = EXCLUDED.title_positive_prob,
            title_negative_prob = EXCLUDED.title_negative_prob,
            title_neutral_prob = EXCLUDED.title_neutral_prob,
            title_sentiment_score = EXCLUDED.title_sentiment_score,
            body_positive_prob = EXCLUDED.body_positive_prob,
            body_negative_prob = EXCLUDED.body_negative_prob,
            body_neutral_prob = EXCLUDED.body_neutral_prob,
            body_sentiment_score = EXCLUDED.body_sentiment_score,
            body_n_chunks = EXCLUDED.body_n_chunks,
            body_summary_embedding = EXCLUDED.body_summary_embedding,
            feature_payload = EXCLUDED.feature_payload,
            updated_at = NOW()
    """

    params = {
        "document_id": int(document_id),
        "body_summary": _safe_str(payload.get("body_summary"), "") or "",
        "body_original_length": _safe_int(payload.get("body_original_length"), 0),
        "title_positive_prob": _safe_float(payload.get("title_positive_prob"), 0.0),
        "title_negative_prob": _safe_float(payload.get("title_negative_prob"), 0.0),
        "title_neutral_prob": _safe_float(payload.get("title_neutral_prob"), 0.0),
        "title_sentiment_score": _safe_float(payload.get("title_sentiment_score"), 0.0),
        "body_positive_prob": _safe_float(payload.get("body_positive_prob"), 0.0),
        "body_negative_prob": _safe_float(payload.get("body_negative_prob"), 0.0),
        "body_neutral_prob": _safe_float(payload.get("body_neutral_prob"), 0.0),
        "body_sentiment_score": _safe_float(payload.get("body_sentiment_score"), 0.0),
        "body_n_chunks": _safe_float(payload.get("body_n_chunks"), 0.0),
        "body_summary_embedding": extras.Json(_to_jsonable(payload.get("body_summary_embedding")), dumps=lambda obj: json.dumps(obj, ensure_ascii=False)),
        "feature_payload": extras.Json(payload, dumps=lambda obj: json.dumps(obj, ensure_ascii=False)),
    }

    with _connect() as conn:
        with conn.cursor() as cursor:
            cursor.execute(query, params)
        conn.commit()


def insert_crawler_run_log(run_type: str, status: str, counts: dict[str, Any] | None = None, payload: dict[str, Any] | None = None) -> None:
    if psycopg2 is None:
        raise RuntimeError("psycopg2 is not installed")

    with _connect() as conn:
        with conn.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO crawler_run_logs (run_type, status, counts, payload)
                VALUES (%s, %s, %s, %s)
                """,
                (
                    run_type,
                    status,
                    extras.Json(_to_jsonable(counts or {}), dumps=lambda obj: json.dumps(obj, ensure_ascii=False)),
                    extras.Json(_to_jsonable(payload or {}), dumps=lambda obj: json.dumps(obj, ensure_ascii=False)),
                ),
            )
        conn.commit()


def insert_prediction_run(summary: dict[str, Any], metadata: dict[str, Any] | None = None) -> None:
    if psycopg2 is None:
        raise RuntimeError("psycopg2 is not installed")

    metrics = summary.get("metrics") or {}
    params = {
        "model_version": _safe_str(summary.get("modelVersion"), "") or "",
        "model_target": _safe_str(summary.get("targetTicker"), "") or "",
        "best_horizon_days": _safe_int(summary.get("bestHorizonDays"), 0),
        "best_threshold": _safe_float(summary.get("bestThreshold"), 0.0),
        "policy_score": _safe_float(metrics.get("policyScore"), 0.0),
        "direction_accuracy": _safe_float(metrics.get("directionAccuracy"), 0.0),
        "top_label": _safe_str(metrics.get("topLabel"), "") or "",
        "top_label_probability": _safe_float(metrics.get("topLabelProbability"), 0.0),
        "summary_payload": extras.Json(_to_jsonable(summary), dumps=lambda obj: json.dumps(obj, ensure_ascii=False)),
        "metadata_payload": extras.Json(_to_jsonable(metadata or {}), dumps=lambda obj: json.dumps(obj, ensure_ascii=False)),
    }

    with _connect() as conn:
        with conn.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO policy_prediction_runs (
                    model_version, model_target, best_horizon_days, best_threshold,
                    policy_score, direction_accuracy, top_label, top_label_probability,
                    summary_payload, metadata_payload
                ) VALUES (
                    %(model_version)s, %(model_target)s, %(best_horizon_days)s, %(best_threshold)s,
                    %(policy_score)s, %(direction_accuracy)s, %(top_label)s, %(top_label_probability)s,
                    %(summary_payload)s, %(metadata_payload)s
                )
                """,
                params,
            )
        conn.commit()


def persist_policy_pipeline_outputs(
    raw_df: pd.DataFrame,
    processed_df: pd.DataFrame,
    raw_csv_path: str,
    processed_csv_path: str,
    run_type: str = "policy_monitor",
    *,
    append_processed: bool = True,
) -> dict[str, Any]:
    """CSV와 DB에 저장한다. processed CSV 는 기본적으로 기존 파일에 누적(append)한다."""
    init_db()

    raw_path = Path(raw_csv_path)
    processed_path = Path(processed_csv_path)
    raw_path.parent.mkdir(parents=True, exist_ok=True)
    processed_path.parent.mkdir(parents=True, exist_ok=True)

    def _dedupe_policy_frame(frame: pd.DataFrame) -> pd.DataFrame:
        if frame is None or frame.empty:
            return frame
        if {"sector", "url"}.issubset(frame.columns):
            return frame.drop_duplicates(subset=["sector", "url"], keep="last")
        if "url" in frame.columns:
            return frame.drop_duplicates(subset=["url"], keep="last")
        return frame

    if raw_df is not None:
        raw_df = _dedupe_policy_frame(raw_df)
        raw_df.to_csv(raw_path, index=False, encoding="utf-8-sig")

    cycle_processed = (
        _dedupe_policy_frame(processed_df.copy())
        if processed_df is not None and not processed_df.empty
        else pd.DataFrame()
    )

    persisted_processed = cycle_processed
    if processed_df is not None:
        to_write = cycle_processed.copy() if not cycle_processed.empty else pd.DataFrame()
        if append_processed and processed_path.exists() and not to_write.empty:
            try:
                existing = pd.read_csv(processed_path, encoding="utf-8-sig")
            except Exception:
                existing = pd.DataFrame()
            if not existing.empty:
                to_write = pd.concat([existing, to_write], ignore_index=True, sort=False)
                to_write = _dedupe_policy_frame(to_write)
        if not to_write.empty and "sector" in to_write.columns:
            ordered = ["sector"] + [c for c in to_write.columns if c != "sector"]
            to_write = to_write[ordered]
        to_write.to_csv(processed_path, index=False, encoding="utf-8-sig")
        persisted_processed = to_write

    raw_count = int(len(raw_df)) if raw_df is not None else 0
    cycle_count = int(len(cycle_processed))
    processed_count = int(len(persisted_processed)) if persisted_processed is not None else 0

    inserted_documents = 0
    inserted_features = 0

    if raw_df is not None and not raw_df.empty:
        for _, row in raw_df.iterrows():
            upsert_policy_document(row.to_dict())
            inserted_documents += 1

    if not cycle_processed.empty:
        for _, row in cycle_processed.iterrows():
            document_id = upsert_policy_document(row.to_dict())
            upsert_policy_document_features(document_id, row.to_dict())
            inserted_features += 1

    insert_crawler_run_log(
        run_type=run_type,
        status="success",
        counts={
            "raw_count": raw_count,
            "processed_count": processed_count,
            "cycle_processed_count": cycle_count,
            "inserted_documents": inserted_documents,
            "inserted_features": inserted_features,
        },
        payload={
            "raw_csv_path": str(raw_path),
            "processed_csv_path": str(processed_path),
            "append_processed": append_processed,
        },
    )

    return {
        "raw_csv_path": str(raw_path),
        "processed_csv_path": str(processed_path),
        "raw_count": raw_count,
        "processed_count": processed_count,
        "cycle_processed_count": cycle_count,
        "inserted_documents": inserted_documents,
        "inserted_features": inserted_features,
    }


def _load_jsonb_column(frame: pd.DataFrame, column: str) -> pd.DataFrame:
    if column not in frame.columns:
        return frame

    expanded = pd.json_normalize(frame[column].apply(lambda value: value if isinstance(value, dict) else {}))
    frame = frame.drop(columns=[column])
    if not expanded.empty:
        expanded = expanded.loc[:, ~expanded.columns.isin(frame.columns)]
        frame = pd.concat([frame.reset_index(drop=True), expanded.reset_index(drop=True)], axis=1)
    return frame


def fetch_policy_training_frame() -> pd.DataFrame:
    if psycopg2 is None:
        return pd.DataFrame()

    query = """
        SELECT
            COALESCE(
                d.published_date::text,
                NULLIF(d.release_date, ''),
                to_char(d.collected_at AT TIME ZONE 'UTC', 'YYYY-MM-DD')
            ) AS date,
            d.source,
            d.category,
            d.doc_type,
            d.title,
            d.url,
            d.body,
            d.matched_keyword_groups,
            d.matched_keywords,
            f.feature_payload
        FROM policy_documents d
        JOIN policy_document_features f ON f.document_id = d.id
        ORDER BY d.published_date DESC NULLS LAST, d.id DESC
    """

    with _connect(real_dict_cursor=True) as conn:
        with conn.cursor() as cursor:
            cursor.execute(query)
            rows = cursor.fetchall()

    if not rows:
        return pd.DataFrame()

    frame = pd.DataFrame(rows)
    frame = _load_jsonb_column(frame, "feature_payload")
    return frame


def fetch_policy_feed_frame(
    category: str = "all",
    date_from: str = "",
    date_to: str = "",
    limit: int | None = 20,
) -> pd.DataFrame:
    if psycopg2 is None:
        return pd.DataFrame()

    query_parts = [
        """
        SELECT
            d.id AS document_id,
            COALESCE(
                d.published_date::text,
                NULLIF(d.release_date, ''),
                to_char(d.collected_at AT TIME ZONE 'UTC', 'YYYY-MM-DD')
            ) AS date,
            d.source,
            d.category,
            d.doc_type,
            d.title,
            d.url AS link,
            d.body,
            COALESCE(f.body_summary, LEFT(COALESCE(d.body, ''), 280)) AS body_summary,
            COALESCE(f.title_positive_prob, 0.0) AS title_positive_prob,
            COALESCE(f.title_negative_prob, 0.0) AS title_negative_prob,
            COALESCE(f.title_neutral_prob, 1.0) AS title_neutral_prob,
            COALESCE(f.title_sentiment_score, 0.0) AS title_sentiment_score,
            COALESCE(f.body_positive_prob, 0.0) AS body_positive_prob,
            COALESCE(f.body_negative_prob, 0.0) AS body_negative_prob,
            COALESCE(f.body_neutral_prob, 1.0) AS body_neutral_prob,
            COALESCE(f.body_sentiment_score, 0.0) AS body_sentiment_score,
            COALESCE(f.body_n_chunks, 1) AS body_n_chunks,
            f.body_summary_embedding,
            f.feature_payload
        FROM policy_documents d
        LEFT JOIN policy_document_features f ON f.document_id = d.id
        WHERE (%(category)s = 'all' OR LOWER(d.category) = LOWER(%(category)s))
        """
    ]
    params = {"category": category or "all"}

    if date_from:
        query_parts.append("AND d.published_date >= %(date_from)s::date")
        params["date_from"] = date_from
    if date_to:
        query_parts.append("AND d.published_date <= %(date_to)s::date")
        params["date_to"] = date_to

    query_parts.append("ORDER BY d.published_date DESC NULLS LAST, d.id DESC")
    query = "\n".join(query_parts)

    with _connect(real_dict_cursor=True) as conn:
        with conn.cursor() as cursor:
            cursor.execute(query, params)
            rows = cursor.fetchall()

    if not rows:
        fallback_query = """
            SELECT
                d.id AS document_id,
                COALESCE(
                d.published_date::text,
                NULLIF(d.release_date, ''),
                to_char(d.collected_at AT TIME ZONE 'UTC', 'YYYY-MM-DD')
            ) AS date,
                d.source,
                d.category,
                d.doc_type,
                d.title,
                d.url AS link,
                d.body,
                LEFT(COALESCE(d.body, ''), 280) AS body_summary,
                0.0 AS title_positive_prob,
                0.0 AS title_negative_prob,
                1.0 AS title_neutral_prob,
                0.0 AS title_sentiment_score,
                0.0 AS body_positive_prob,
                0.0 AS body_negative_prob,
                1.0 AS body_neutral_prob,
                0.0 AS body_sentiment_score,
                1 AS body_n_chunks,
                NULL AS body_summary_embedding,
                NULL AS feature_payload
            FROM policy_documents d
            WHERE (%(category)s = 'all' OR LOWER(d.category) = LOWER(%(category)s))
            ORDER BY d.published_date DESC NULLS LAST, d.id DESC
        """
        fallback_params = {"category": category or "all"}
        if date_from:
            fallback_query = fallback_query.replace(
                "            WHERE (%(category)s = 'all' OR LOWER(d.category) = LOWER(%(category)s))\n",
                "            WHERE (%(category)s = 'all' OR LOWER(d.category) = LOWER(%(category)s))\n              AND d.published_date >= %(date_from)s::date\n",
            )
            fallback_params["date_from"] = date_from
        if date_to:
            fallback_query = fallback_query.replace(
                "            ORDER BY d.published_date DESC NULLS LAST, d.id DESC\n",
                "              AND d.published_date <= %(date_to)s::date\n            ORDER BY d.published_date DESC NULLS LAST, d.id DESC\n",
            )
            fallback_params["date_to"] = date_to
        with _connect(real_dict_cursor=True) as conn:
            with conn.cursor() as cursor:
                cursor.execute(fallback_query, fallback_params)
                rows = cursor.fetchall()

        if not rows:
            return pd.DataFrame()

    frame = pd.DataFrame(rows)
    if limit is not None and limit > 0:
        frame = frame.head(limit)
    return frame


def persist_prediction_run(summary: dict[str, Any], metadata: dict[str, Any] | None = None, csv_path: str | None = None) -> dict[str, Any]:
    """예측 결과를 CSV와 DB에 동시에 저장한다."""
    init_db()
    insert_prediction_run(summary, metadata)

    csv_written = None
    if csv_path:
        csv_file = Path(csv_path)
        csv_file.parent.mkdir(parents=True, exist_ok=True)
        row = {
            "generatedAt": summary.get("generatedAt"),
            "modelVersion": summary.get("modelVersion"),
            "targetTicker": summary.get("targetTicker"),
            "bestHorizonDays": summary.get("bestHorizonDays"),
            "bestThreshold": summary.get("bestThreshold"),
            "policyScore": (summary.get("metrics") or {}).get("policyScore"),
            "directionAccuracy": (summary.get("metrics") or {}).get("directionAccuracy"),
            "topLabel": (summary.get("metrics") or {}).get("topLabel"),
            "topLabelProbability": (summary.get("metrics") or {}).get("topLabelProbability"),
            "summaryJson": json.dumps(_to_jsonable(summary), ensure_ascii=False),
            "metadataJson": json.dumps(_to_jsonable(metadata or {}), ensure_ascii=False),
        }
        if csv_file.exists():
            try:
                existing_rows = pd.read_csv(csv_file, encoding="utf-8-sig")
                row_frame = pd.concat([existing_rows, pd.DataFrame([row])], ignore_index=True, sort=False)
            except Exception:
                row_frame = pd.DataFrame([row])
        else:
            row_frame = pd.DataFrame([row])
        row_frame.to_csv(csv_file, index=False, encoding="utf-8-sig")
        csv_written = str(csv_file)

    return {
        "csv_path": csv_written,
        "model_version": summary.get("modelVersion"),
        "target_ticker": summary.get("targetTicker"),
    }


def fetch_article_insights(document_ids: list[int] | None = None, insight_date: date | str | None = None) -> pd.DataFrame:
    if psycopg2 is None:
        return pd.DataFrame()

    query = [
        """
        SELECT
            ai.document_id,
            ai.insight_date,
            ai.summary,
            ai.keywords,
            ai.asset_impacts,
            ai.llm_provider,
            ai.llm_model,
            ai.prompt_version,
            ai.insight_payload,
            d.source,
            d.category,
            d.doc_type,
            d.title,
            d.url,
            d.body,
            COALESCE(
                d.published_date::text,
                NULLIF(d.release_date, ''),
                to_char(d.collected_at AT TIME ZONE 'UTC', 'YYYY-MM-DD')
            ) AS published_date
        FROM article_insights ai
        JOIN policy_documents d ON d.id = ai.document_id
        WHERE 1 = 1
        """
    ]
    params: dict[str, Any] = {}

    if document_ids:
        query.append("AND ai.document_id = ANY(%(document_ids)s)")
        params["document_ids"] = list(map(int, document_ids))

    if insight_date:
        query.append("AND ai.insight_date = %(insight_date)s::date")
        params["insight_date"] = str(insight_date)

    query.append("ORDER BY ai.insight_date DESC, ai.id DESC")

    with _connect(real_dict_cursor=True) as conn:
        with conn.cursor() as cursor:
            cursor.execute("\n".join(query), params)
            rows = cursor.fetchall()

    if not rows:
        return pd.DataFrame()

    return pd.DataFrame(rows)


def upsert_article_insight(record: dict[str, Any]) -> int:
    if psycopg2 is None:
        raise RuntimeError("psycopg2 is not installed")

    payload = _row_to_dict(record)
    query = """
        INSERT INTO article_insights (
            document_id, insight_date, summary, keywords, asset_impacts,
            llm_provider, llm_model, prompt_version, insight_payload, updated_at
        ) VALUES (
            %(document_id)s, %(insight_date)s, %(summary)s, %(keywords)s, %(asset_impacts)s,
            %(llm_provider)s, %(llm_model)s, %(prompt_version)s, %(insight_payload)s, NOW()
        )
        ON CONFLICT (document_id) DO UPDATE SET
            insight_date = EXCLUDED.insight_date,
            summary = EXCLUDED.summary,
            keywords = EXCLUDED.keywords,
            asset_impacts = EXCLUDED.asset_impacts,
            llm_provider = EXCLUDED.llm_provider,
            llm_model = EXCLUDED.llm_model,
            prompt_version = EXCLUDED.prompt_version,
            insight_payload = EXCLUDED.insight_payload,
            updated_at = NOW()
        RETURNING id
    """

    params = {
        "document_id": int(payload.get("document_id")),
        "insight_date": _safe_date(payload.get("insight_date")) or datetime.utcnow().date(),
        "summary": _safe_str(payload.get("summary"), "") or "",
        "keywords": _json_param(payload.get("keywords") or []),
        "asset_impacts": _json_param(payload.get("asset_impacts") or []),
        "llm_provider": _safe_str(payload.get("llm_provider"), "gemini") or "gemini",
        "llm_model": _safe_str(payload.get("llm_model"), "") or "",
        "prompt_version": _safe_str(payload.get("prompt_version"), "v1") or "v1",
        "insight_payload": _json_param(payload.get("insight_payload") or payload),
    }

    with _connect() as conn:
        with conn.cursor() as cursor:
            cursor.execute(query, params)
            row = cursor.fetchone()
        conn.commit()
        return int(row[0])


def fetch_home_briefings(user_id: int, briefing_date: date | str | None = None) -> pd.DataFrame:
    if psycopg2 is None:
        return pd.DataFrame()

    query = [
        """
        SELECT
            id,
            user_id,
            briefing_date,
            briefing_headline,
            briefing_paragraphs,
            push_data,
            llm_provider,
            llm_model,
            prompt_version,
            briefing_payload
        FROM home_briefings
        WHERE user_id = %(user_id)s
        """
    ]
    params: dict[str, Any] = {"user_id": int(user_id)}

    if briefing_date:
        query.append("AND briefing_date = %(briefing_date)s::date")
        params["briefing_date"] = str(briefing_date)

    query.append("ORDER BY briefing_date DESC, id DESC")

    with _connect(real_dict_cursor=True) as conn:
        with conn.cursor() as cursor:
            cursor.execute("\n".join(query), params)
            rows = cursor.fetchall()

    if not rows:
        return pd.DataFrame()

    return pd.DataFrame(rows)


def upsert_home_briefing(record: dict[str, Any]) -> int:
    if psycopg2 is None:
        raise RuntimeError("psycopg2 is not installed")

    payload = _row_to_dict(record)
    query = """
        INSERT INTO home_briefings (
            user_id, briefing_date, briefing_headline, briefing_paragraphs,
            push_data, llm_provider, llm_model, prompt_version, briefing_payload, updated_at
        ) VALUES (
            %(user_id)s, %(briefing_date)s, %(briefing_headline)s, %(briefing_paragraphs)s,
            %(push_data)s, %(llm_provider)s, %(llm_model)s, %(prompt_version)s, %(briefing_payload)s, NOW()
        )
        ON CONFLICT (user_id, briefing_date) DO UPDATE SET
            briefing_headline = EXCLUDED.briefing_headline,
            briefing_paragraphs = EXCLUDED.briefing_paragraphs,
            push_data = EXCLUDED.push_data,
            llm_provider = EXCLUDED.llm_provider,
            llm_model = EXCLUDED.llm_model,
            prompt_version = EXCLUDED.prompt_version,
            briefing_payload = EXCLUDED.briefing_payload,
            updated_at = NOW()
        RETURNING id
    """

    params = {
        "user_id": int(payload.get("user_id")),
        "briefing_date": _safe_date(payload.get("briefing_date")) or datetime.utcnow().date(),
        "briefing_headline": _safe_str(payload.get("briefing_headline"), "") or "",
        "briefing_paragraphs": _json_param(payload.get("briefing_paragraphs") or []),
        "push_data": _json_param(payload.get("push_data") or {}),
        "llm_provider": _safe_str(payload.get("llm_provider"), "gemini") or "gemini",
        "llm_model": _safe_str(payload.get("llm_model"), "") or "",
        "prompt_version": _safe_str(payload.get("prompt_version"), "v1") or "v1",
        "briefing_payload": _json_param(payload.get("briefing_payload") or payload),
    }

    with _connect() as conn:
        with conn.cursor() as cursor:
            cursor.execute(query, params)
            row = cursor.fetchone()
        conn.commit()
        return int(row[0])
