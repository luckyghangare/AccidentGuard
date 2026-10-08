from pydantic import BaseModel
from datetime import datetime

class SensorCreate(BaseModel):
    vehicle_id: int
    obstacle_distance: float
    acceleration_g: float
    gyroscope_rad_s: float
    status: str = "SAFE"

class SensorResponse(BaseModel):
    id: int
    vehicle_id: int
    obstacle_distance: float
    acceleration_g: float
    gyroscope_rad_s: float
    status: str
    timestamp: datetime

    class Config:
        from_attributes = True
