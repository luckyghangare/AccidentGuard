from pydantic import BaseModel
from typing import Optional
from datetime import datetime

class ContactCreate(BaseModel):
    name: str
    phone_number: str
    relationship_type: str = "Family"
    is_primary: bool = False

class ContactUpdate(BaseModel):
    name: Optional[str] = None
    phone_number: Optional[str] = None
    relationship_type: Optional[str] = None
    is_primary: Optional[bool] = None

class ContactResponse(BaseModel):
    id: int
    user_id: int
    name: str
    phone_number: str
    relationship_type: str
    is_primary: bool
    created_at: datetime

    class Config:
        from_attributes = True
