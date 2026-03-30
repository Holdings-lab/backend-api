import os
from datetime import datetime
from typing import Any, Dict, List

import psycopg2
from psycopg2.extras import RealDictCursor


POSTGRES_HOST = os.getenv("POSTGRES_HOST", "localhost")
POSTGRES_PORT = int(os.getenv("POSTGRES_PORT", "5432"))
POSTGRES_DB = os.getenv("POSTGRES_DB", "holdings")
POSTGRES_USER = os.getenv("POSTGRES_USER", "postgres")
POSTGRES_PASSWORD = os.getenv("POSTGRES_PASSWORD", "password")


def _connect():
    return psycopg2.connect(
        host=POSTGRES_HOST,
        port=POSTGRES_PORT,
        dbname=POSTGRES_DB,
        user=POSTGRES_USER,
        password=POSTGRES_PASSWORD,
    )


def save_policy_event(event: Dict[str, Any]) -> Dict[str, Any]:
    query = """
        INSERT INTO policy_events (title, keyword, impact_score, analysis_summary, created_at)
        VALUES (%s, %s, %s, %s, %s)
        RETURNING id, title, keyword, impact_score, analysis_summary, created_at
    """

    created_at = event.get("created_at")
    if isinstance(created_at, str):
        created_at = datetime.fromisoformat(created_at)

    with _connect() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute(
                query,
                (
                    event["title"],
                    event["keyword"],
                    float(event["impact_score"]),
                    event["analysis_summary"],
                    created_at or datetime.now(),
                ),
            )
            row = cur.fetchone()
            conn.commit()
            return dict(row) if row else {}


def fetch_policy_events(limit: int = 100) -> List[Dict[str, Any]]:
    query = """
        SELECT id, title, keyword, impact_score, analysis_summary, created_at
        FROM policy_events
        ORDER BY created_at DESC
        LIMIT %s
    """

    with _connect() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute(query, (limit,))
            rows = cur.fetchall()

    results: List[Dict[str, Any]] = []
    for row in rows:
        item = dict(row)
        created_at = item.get("created_at")
        if created_at is not None:
            item["created_at"] = created_at.isoformat()
        results.append(item)

    return results
