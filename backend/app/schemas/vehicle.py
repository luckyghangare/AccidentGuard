from pydantic import BaseModel
from typing import Optional
from datetime import datetime

class VehicleCreate(BaseModel):
    name: str
    vehicle_number: str
    device_id: Optional[str] = "ESP32_DEV_01"

class VehicleUpdate(BaseModel):
    name: Optional[str] = None
    vehicle_number: Optional[str] = None
    device_id: Optional[str] = None
    status: Optional[str] = None
    battery_level: Optional[int] = None

class VehicleResponse(BaseModel):
    id: int
    user_id: int
    name: str
    vehicle_number: str
    device_id: Optional[str]
    status: str
    battery_level: int
    is_active: bool
    created_at: datetime

    class Config:
        from_attributes = True
