from fastapi import APIRouter, FastAPI, status
from datetime import datetime
import logging
import os
import uvicorn
from typing import Dict, Any
from dotenv import load_dotenv
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

# 환경 변수 로드
load_dotenv()

from schemas import PolicyEvent
from webhook_notifier import send_webhook
from db_client import save_policy_event, fetch_policy_events

# 새로 분리된 파이프라인 모듈 임포트
from crawler import run_crawling_pipeline
from predictor import run_predict_pipeline

# 로깅 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="AI Engine - Dummy Webhook Sender")
router = APIRouter(prefix="/ai", tags=["ai-engine"])

# CORS 설정 추가
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 모든 오리진 허용 (개발용)
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

def success_response(result: Any = None, message: str = "요청에 성공했습니다.", code: str = "SUCCESS-200") -> Dict[str, Any]:
    return {
        "isSuccess": True,
        "code": code,
        "message": message,
        "result": {} if result is None else result,
    }


def error_response(
    message: str = "요청에 실패했습니다.",
    code: str = "FAIL-001",
    status_code: int = status.HTTP_500_INTERNAL_SERVER_ERROR,
) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content={
            "isSuccess": False,
            "code": code,
            "message": message,
        },
    )


def remove_message_fields(value: Any) -> Any:
    if isinstance(value, dict):
        return {k: remove_message_fields(v) for k, v in value.items() if k != "message"}
    if isinstance(value, list):
        return [remove_message_fields(item) for item in value]
    return value


@router.get("/health")
async def health() -> Dict[str, Any]:
    return success_response(
        {
            "service": "ai-engine",
            "timestamp": datetime.utcnow().isoformat() + "Z",
        },
        message="AI 엔진 헬스체크에 성공했습니다.",
    )


@router.get("/test-trigger")
async def test_trigger_webhook() -> Dict[str, Any]:
    """
    테스트용 엔드포인트: 가짜 데이터를 생성하고 api-server에 전송하여 PostgreSQL에 저장합니다.
    후에 스케쥴링으로 전환하여 주기적으로 실행
    """
    try:
        logger.info("=========================================")
        logger.info("🔗 [Pipeline Step 1] 크롤링 엔드포인트 트리거됨")

        crawled_data = await run_crawling_pipeline()

        logger.info("🔗 [Pipeline Step 2] AI 모델 예측 진행 중...")
        predicted_result = await run_predict_pipeline(crawled_data)

        logger.info("🔗 [Pipeline Step 3] 예측 완료. 공통 DB에 적재 진행...")

        dummy_data = {
            "title": predicted_result["title"],
            "keyword": predicted_result["keyword"],
            "impact_score": predicted_result["impact_score"],
            "analysis_summary": predicted_result["analysis_summary"],
            "created_at": datetime.now(),
        }

        event_model = PolicyEvent(**dummy_data)

        event_dict = event_model.model_dump()
        event_dict["created_at"] = event_dict["created_at"].isoformat()
        saved_event = save_policy_event(event_dict)
        logger.info(f"✨ DB 저장 완료: eventId={saved_event.get('id')}, title={event_model.title}")

        logger.info("🔗 [Pipeline Step 4] 대상 API-Server로 Webhook 발송 중...")

        webhook_result = await send_webhook(
            event_id=saved_event.get("id"),
            keyword=saved_event.get("keyword", event_model.keyword),
        )

        logger.info("🔗 [Pipeline 단계 종료]")
        logger.info("=========================================")

        return success_response(
            remove_message_fields(
                {
                    "crawling_result": crawled_data,
                    "prediction_result": predicted_result,
                    "saved_event": saved_event,
                    "webhook_result": webhook_result,
                }
            ),
            message="AI 파이프라인 테스트 실행에 성공했습니다.",
        )
    except Exception as error:
        logger.exception("❌ AI 파이프라인 실행 실패: %s", error)
        return error_response()


@router.get("/events")
async def get_events() -> list:
    """
    PostgreSQL에 저장된 이벤트 목록을 직접 조회해 반환합니다.
    """
    try:
        events = fetch_policy_events(limit=100)
        return success_response(
            remove_message_fields(events),
            message="AI 엔진 이벤트 목록 조회에 성공했습니다.",
        )
    except Exception as error:
        logger.error(f"❌ 이벤트 조회 실패: {error}")
        return error_response()


app.include_router(router)

if __name__ == "__main__":
    host = os.getenv("HOST")
    port = int(os.getenv("PORT"))
    uvicorn.run("main:app", host=host, port=port, reload=True)