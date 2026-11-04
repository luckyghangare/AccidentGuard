from pydantic import BaseModel
from datetime import datetime
from typing import Optional, List

class CallParticipantResponse(BaseModel):
    id: int
    user_id: int
    full_name: str
    participant_role: str
    status: str
    invited_at: datetime
    accepted_at: Optional[datetime] = None
    joined_at: Optional[datetime] = None

    class Config:
        from_attributes = True
        orm_mode = True

class EmergencyCallSessionResponse(BaseModel):
    id: int
    accident_id: int
    victim_user_id: int
    livekit_room_name: str
    status: str
    winning_contact_user_id: Optional[int] = None
    invitation_started_at: Optional[datetime] = None
    connected_at: Optional[datetime] = None
    ended_at: Optional[datetime] = None
    created_at: datetime
    participants: List[CallParticipantResponse] = []

    class Config:
        from_attributes = True
        orm_mode = True

class CallTokenResponse(BaseModel):
    token: str
    room_name: str
    participant_identity: str
    livekit_url: str

class CallAcceptResponse(BaseModel):
    session: EmergencyCallSessionResponse
    token: str
    livekit_url: str

class CallActionRequest(BaseModel):
    reason: Optional[str] = None
