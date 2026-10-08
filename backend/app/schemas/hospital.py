from pydantic import BaseModel, EmailStr
from typing import Optional, List
from datetime import datetime

class HospitalRegister(BaseModel):
    name: str
    email: EmailStr
    phone_number: str
    password: str
    address: str
    city: Optional[str] = "Nagpur"
    latitude: Optional[float] = 21.1458
    longitude: Optional[float] = 79.0882
    total_ambulances: Optional[int] = 5

class HospitalLogin(BaseModel):
    email: EmailStr
    password: str

class HospitalResponse(BaseModel):
    id: int
    name: str
    email: str
    phone_number: str
    address: str
    city: str
    latitude: float
    longitude: float
    total_ambulances: int
    available_ambulances: int
    is_active: bool
    created_at: datetime

    class Config:
        from_attributes = True

class AmbulanceDispatch(BaseModel):
    ambulance_number: Optional[str] = "AMB-01"
    driver_name: Optional[str] = "Emergency Unit"
    driver_phone: Optional[str] = "108"
    eta_minutes: Optional[int] = 8
    notes: Optional[str] = "Direct dispatch from hospital emergency response unit"

class HospitalFleetUpdate(BaseModel):
    total_ambulances: Optional[int] = None
    available_ambulances: Optional[int] = None

class VictimInfo(BaseModel):
    user_name: str
    user_phone: str
    user_email: str
    vehicle_name: str
    vehicle_number: str
    device_id: Optional[str] = None

class EmergencyAlertResponse(BaseModel):
    id: int
    hospital_id: int
    accident_id: int
    status: str
    damage_severity: str
    latitude: float
    longitude: float
    impact_g: float
    date_str: str
    time_str: str
    maps_url: str
    victim: VictimInfo
    ambulance_number: Optional[str] = None
    driver_name: Optional[str] = None
    driver_phone: Optional[str] = None
    eta_minutes: Optional[int] = None
    notes: Optional[str] = None
    dispatched_at: Optional[datetime] = None
    admitted_at: Optional[datetime] = None
    created_at: datetime

    class Config:
        from_attributes = True
