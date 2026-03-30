from pydantic import BaseModel
from datetime import datetime
from typing import Optional

class PolicyEvent(BaseModel):
    id: Optional[int] = None
    title: str
    keyword: str
    impact_score: float
    analysis_summary: str
    created_at: datetime
