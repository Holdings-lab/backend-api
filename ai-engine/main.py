from fastapi import FastAPI
import random
from datetime import datetime
import logging
import json
import os
import uvicorn
from typing import Dict, Any
from dotenv import load_dotenv
from fastapi.middleware.cors import CORSMiddleware

# 환경 변수 로드
load_dotenv()

from schemas import PolicyEvent
from webhook_notifier import send_webhook

# 새로 분리된 파이프라인 모듈 임포트
from crawler import run_crawling_pipeline
from predictor import run_predict_pipeline

# 로깅 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="AI Engine - Dummy Webhook Sender")

# CORS 설정 추가
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 모든 오리진 허용 (개발용)
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

TEST_DATA_FILE = os.getenv("TEST_DATA_FILE")


@app.get("/test-trigger")
async def test_trigger_webhook() -> Dict[str, Any]:
    """
    테스트용 엔드포인트: 가짜 데이터를 생성하여 JSON 파일에 저장하고 Spring Boot에 Webhook을 발송합니다.
    후에 스케쥴링으로 전환하여 주기적으로 실행
    """
    logger.info("=========================================")
    logger.info("🔗 [Pipeline Step 1] 크롤링 엔드포인트 트리거됨")
    
    # 1. 크롤링 파이프라인 실행
    crawled_data = await run_crawling_pipeline()
    
    logger.info("🔗 [Pipeline Step 2] AI 모델 예측 진행 중...")
    
    # 2. AI 예측 파이프라인 실행
    predicted_result = await run_predict_pipeline(crawled_data)
    
    logger.info("🔗 [Pipeline Step 3] 예측 완료. 공통 DB에 적재 진행...")
    
    # 3. 예측 결과를 바탕으로 PolicyEvent 모델 생성
    dummy_data = {
        "id": random.randint(1000, 9999), 
        "title": predicted_result["title"],
        "keyword": predicted_result["keyword"],
        "impact_score": predicted_result["impact_score"],
        "analysis_summary": predicted_result["analysis_summary"],
        "created_at": datetime.now()
    }

    event_model = PolicyEvent(**dummy_data)
    
    # JSON DB에 기록 ('test_data\events.json'에 덮어씌우기)
    event_dict = event_model.model_dump()
    event_dict['created_at'] = event_dict['created_at'].isoformat()
    events = [event_dict]  # 기존 데이터를 덮어씌움
    
    try:
        os.makedirs(os.path.dirname(TEST_DATA_FILE), exist_ok=True)
        with open(TEST_DATA_FILE, "w", encoding="utf-8") as f:
            json.dump(events, f, ensure_ascii=False, indent=2)
        logger.info(os.path.abspath(TEST_DATA_FILE))
        logger.info(f"✨ JSON 데이터 저장 완료: {event_model.title} (ID: {event_model.id})")
    except Exception as e:
        logger.error(f"❌ JSON 데이터 저장 실패: {e}")
        return {
            "message": "Failed to save event data to JSON.",
            "error": str(e)
        }

    logger.info("🔗 [Pipeline Step 4] 대상 API-Server로 Webhook 발송 중...")

    # 4. Webhook 발송
    extracted_event_id = event_model.id
    extracted_keyword = event_model.keyword
    
    webhook_result = await send_webhook(event_id=extracted_event_id, keyword=extracted_keyword)

    logger.info("🔗 [Pipeline 단계 종료]")
    logger.info("=========================================")

    return {
        "message": "Full AI Pipeline (Dummy) executed and Webhook triggered!",
        "crawling_result": crawled_data,
        "prediction_result": predicted_result,
        "saved_event": event_dict,
        "webhook_result": webhook_result
    }


@app.get("/events")
async def get_events() -> list:
    """
    저장된 이벤트 목록을 반환합니다.
    """
    if os.path.exists(TEST_DATA_FILE):
        try:
            with open(TEST_DATA_FILE, "r", encoding="utf-8") as f:
                events = json.load(f)
            return events
        except json.JSONDecodeError:
            return []
    return []

if __name__ == "__main__":
    host = os.getenv("HOST")
    port = int(os.getenv("PORT"))
    uvicorn.run("main:app", host=host, port=port, reload=True)