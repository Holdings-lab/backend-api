from pydantic import BaseModel
from datetime import datetime

class PolicyEvent(BaseModel):
    id: int
    title: str
    keyword: str
    impact_score: float
    analysis_summary: str
    created_at: datetime
