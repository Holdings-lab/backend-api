from __future__ import annotations

import os
from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger


def build_scheduler(job_func) -> BackgroundScheduler:
    scheduler = BackgroundScheduler(timezone="Asia/Seoul")

    mode = os.getenv("SCHEDULE_MODE", "hourly").lower()  # hourly | daily | off
    daily_hour = int(os.getenv("DAILY_HOUR", "9"))
    daily_minute = int(os.getenv("DAILY_MINUTE", "0"))

    if mode == "off":
        return scheduler

    trigger = CronTrigger(minute=0) if mode == "hourly" else CronTrigger(hour=daily_hour, minute=daily_minute)
    scheduler.add_job(job_func, trigger=trigger, id="crawl_predict_job", replace_existing=True)
    scheduler.start()
    return scheduler
