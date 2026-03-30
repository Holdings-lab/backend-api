from __future__ import annotations

from datetime import datetime


def run_crawl_now() -> dict:
    """Crawler placeholder.

    현재 backend-api/data-ml/crawler 경로에 실제 크롤러 구현이 없어서
    호출 시 no-op 결과를 반환한다.
    """
    return {
        "status": "skipped",
        "message": "crawler 폴더에 실행 가능한 크롤러가 없어 크롤링을 건너뜁니다.",
        "executed_at": datetime.utcnow().isoformat() + "Z",
    }
