from sqlalchemy import Column, Integer, String, DateTime, ForeignKey, Text
from sqlalchemy.orm import relationship
from datetime import datetime
from app.database import Base

class EmergencyCallSession(Base):
    __tablename__ = "emergency_call_sessions"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    accident_id = Column(Integer, ForeignKey("accidents.id", ondelete="CASCADE"), nullable=False, index=True, unique=True)
    victim_user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    livekit_room_name = Column(String(100), nullable=False, unique=True)
    status = Column(String(50), nullable=False, default="CREATED") # CREATED, INITIATING, RINGING, CONNECTED, ENDED, CANCELLED, FAILED
    winning_contact_user_id = Column(Integer, ForeignKey("users.id"), nullable=True)
    
    invitation_started_at = Column(DateTime, nullable=True)
    connected_at = Column(DateTime, nullable=True)
    ended_at = Column(DateTime, nullable=True)
    
    cancellation_reason = Column(String(255), nullable=True)
    failure_reason = Column(Text, nullable=True)
    
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    accident = relationship("Accident")
    victim = relationship("User", foreign_keys=[victim_user_id])
    winner = relationship("User", foreign_keys=[winning_contact_user_id])
    participants = relationship("CallParticipant", back_populates="session", cascade="all, delete-orphan")

class CallParticipant(Base):
    __tablename__ = "call_participants"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    session_id = Column(Integer, ForeignKey("emergency_call_sessions.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    participant_role = Column(String(50), nullable=False) # victim, responder
    status = Column(String(50), nullable=False, default="PENDING") # PENDING, RINGING, ACCEPTED, REJECTED, MISSED, CONNECTED, DISCONNECTED, ALREADY_CONNECTED, FAILED
    
    invited_at = Column(DateTime, default=datetime.utcnow)
    accepted_at = Column(DateTime, nullable=True)
    joined_at = Column(DateTime, nullable=True)
    left_at = Column(DateTime, nullable=True)
    
    rejection_reason = Column(String(255), nullable=True)
    failure_reason = Column(Text, nullable=True)
    
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    session = relationship("EmergencyCallSession", back_populates="participants")
    user = relationship("User")

    @property
    def full_name(self):
        return self.user.full_name if self.user else ""
