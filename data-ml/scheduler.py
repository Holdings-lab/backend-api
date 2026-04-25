from __future__ import annotations

import os
from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
from apscheduler.triggers.interval import IntervalTrigger


def build_scheduler(job_func) -> BackgroundScheduler:
    scheduler = BackgroundScheduler(timezone="Asia/Seoul")

    mode = os.getenv("SCHEDULE_MODE", "interval").lower()  # interval | hourly | daily | off
    interval_minutes = int(os.getenv("INTERVAL_MINUTES", "30"))
    daily_hour = int(os.getenv("DAILY_HOUR", "9"))
    daily_minute = int(os.getenv("DAILY_MINUTE", "0"))

    if mode == "off":
        return scheduler

    if mode == "interval":
        trigger = IntervalTrigger(minutes=max(1, interval_minutes))
    elif mode == "hourly":
        trigger = CronTrigger(minute=0)
    else:
        trigger = CronTrigger(hour=daily_hour, minute=daily_minute)

    scheduler.add_job(job_func, trigger=trigger, id="crawl_predict_job", replace_existing=True)
    scheduler.start()
    return scheduler
