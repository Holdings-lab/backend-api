from __future__ import annotations

import os

from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger

from pipeline_clock import pipeline_tz


def _cron_int(name: str, default: int) -> int:
    raw = (os.getenv(name) or "").strip()
    if not raw:
        return default
    try:
        return int(raw)
    except ValueError:
        return default


def build_scheduler(crawl_job, read_job, purge_job=None) -> BackgroundScheduler:
    """한국 시간 20:00 크롤, 22:25 신호 JSON 읽기, 03:30 보관 기간 삭제.

    22:00 호스트 예측 크론은 바꾸지 않는다.
    SCHEDULE_MODE=off 이면 등록하지 않는다.
    """
    tz = pipeline_tz()
    scheduler = BackgroundScheduler(timezone=tz)
    if os.getenv("SCHEDULE_MODE", "daily").lower() == "off":
        return scheduler

    crawl_trigger = CronTrigger(
        hour=_cron_int("CRAWL_CRON_HOUR", 20),
        minute=_cron_int("CRAWL_CRON_MINUTE", 0),
        timezone=tz,
    )
    read_trigger = CronTrigger(
        hour=_cron_int("SIGNAL_READ_CRON_HOUR", 22),
        minute=_cron_int("SIGNAL_READ_CRON_MINUTE", 25),
        timezone=tz,
    )
    scheduler.add_job(crawl_job, trigger=crawl_trigger, id="policy_crawl_job", replace_existing=True)
    scheduler.add_job(read_job, trigger=read_trigger, id="signal_read_job", replace_existing=True)
    if purge_job is not None:
        purge_trigger = CronTrigger(
            hour=_cron_int("RETENTION_CRON_HOUR", 3),
            minute=_cron_int("RETENTION_CRON_MINUTE", 30),
            timezone=tz,
        )
        scheduler.add_job(purge_job, trigger=purge_trigger, id="ml_retention_job", replace_existing=True)
    scheduler.start()
    return scheduler
