import httpx
import logging
import os
from dotenv import load_dotenv

load_dotenv()

logger = logging.getLogger(__name__)

# Spring Boot Webhook Endpoint
WEBHOOK_URL = os.getenv("WEBHOOK_URL")

# Webhook 자격 증명
WEBHOOK_SECRET = os.getenv("WEBHOOK_SECRET")

async def send_webhook(event_id: int, keyword: str) -> dict:
    # 2. 전송할 데이터 구조 
    # FastAPI에서는 id와 keyword만 추출하여 전송합니다.
    payload = {
        "event_id": event_id,
        "keyword": keyword
    }
    
    # Header 설정 (X-Webhook-Secret 필수로 포함)
    headers = {
        "X-Webhook-Secret": WEBHOOK_SECRET,
        "Content-Type": "application/json"
    }

    try:
        # 3. httpx를 사용해 비동기 POST 요청 발송
        async with httpx.AsyncClient() as client:
            response = await client.post(WEBHOOK_URL, json=payload, headers=headers)
            
            # 응답 상태 확인
            response.raise_for_status()
            
            # 5. 통신 성공 콘솔 출력
            logger.info(f"[Webhook 전송 성공] Event ID: {event_id}, HTTP Status: {response.status_code}")
            
            return {
                "success": True, 
                "status_code": response.status_code, 
                "response_text": response.text
            }
            
    except httpx.HTTPStatusError as exc:
        # 인증 실패 등 HTTP 에러
        logger.error(f"[Webhook 전송 실패] 상태 코드: {exc.response.status_code}, 대상: {exc.request.url}")
        return {
            "success": False, 
            "error_type": "HTTPStatusError",
            "status_code": exc.response.status_code,
            "details": str(exc)
        }
    except Exception as exc:
        # 연결 실패 등 네트워크 에러
        logger.error(f"[Webhook 연결 실패] Error: {exc}")
        return {
            "success": False,
            "error_type": type(exc).__name__,
            "details": str(exc)
        }
