from __future__ import annotations

import os
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo


def pipeline_tz() -> ZoneInfo:
    name = (os.getenv("PIPELINE_TZ") or "Asia/Seoul").strip() or "Asia/Seoul"
    return ZoneInfo(name)


def pipeline_now(moment: datetime | None = None) -> datetime:
    tz = pipeline_tz()
    if moment is None:
        return datetime.now(tz)
    if moment.tzinfo is None:
        return moment.replace(tzinfo=tz)
    return moment.astimezone(tz)


def _minutes(moment: datetime) -> int:
    return moment.hour * 60 + moment.minute


def is_host_cron_window(moment: datetime | None = None) -> bool:
    """호스트 예측 크론(22:00–22:15)과 겹치지 않도록 21:50–22:30 을 막는다."""
    local = pipeline_now(moment)
    return (21 * 60 + 50) <= _minutes(local) <= (22 * 60 + 30)


def today_at(hour: int, minute: int, moment: datetime | None = None) -> datetime:
    local = pipeline_now(moment)
    return local.replace(hour=hour, minute=minute, second=0, microsecond=0)


def signal_fresh_after(moment: datetime | None = None) -> datetime:
    """이 시각 이후에 수정된 JSON 만 신선한 신호로 본다.

    22:25 읽기 시각이 지난 뒤 -> 오늘 22:00 이후 파일
    그 전 -> 어제 22:00 크론 결과
    """
    today_refresh = today_at(22, 0, moment)
    if pipeline_now(moment) >= today_at(22, 25, moment):
        return today_refresh
    return today_refresh - timedelta(days=1)
