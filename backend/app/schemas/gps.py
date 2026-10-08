from pydantic import BaseModel
from datetime import datetime

class GpsCreate(BaseModel):
    vehicle_id: int
    latitude: float
    longitude: float
    speed: float

class GpsResponse(BaseModel):
    id: int
    vehicle_id: int
    latitude: float
    longitude: float
    speed: float
    timestamp: datetime

    class Config:
        from_attributes = True
