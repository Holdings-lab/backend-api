from __future__ import annotations

import os
from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger


def build_scheduler(job_func) -> BackgroundScheduler:
    scheduler = BackgroundScheduler(timezone="America/New_York")

    # 기본: 미국 동부 시간대에서 매일 자정(00:00)에 1회 실행
    if os.getenv("SCHEDULE_MODE", "daily").lower() == "off":
        return scheduler

    trigger = CronTrigger(hour=0, minute=0, timezone="America/New_York")

    scheduler.add_job(job_func, trigger=trigger, id="crawl_predict_job", replace_existing=True)
    scheduler.start()
    return scheduler
