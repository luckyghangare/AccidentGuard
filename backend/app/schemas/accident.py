from pydantic import BaseModel
from datetime import datetime
from typing import Optional

class AccidentCreate(BaseModel):
    vehicle_id: int
    latitude: float
    longitude: float
    impact_g: float
    date_str: Optional[str] = None
    time_str: Optional[str] = None

class AccidentResponse(BaseModel):
    id: int
    vehicle_id: int
    date_str: str
    time_str: str
    latitude: float
    longitude: float
    impact_g: float
    status: str
    timestamp: datetime

    class Config:
        from_attributes = True
