from sqlalchemy import Column, Integer, String, Boolean, DateTime, ForeignKey
from sqlalchemy.orm import relationship
from datetime import datetime
from app.database import Base

class EmergencyContact(Base):
    __tablename__ = "emergency_contacts"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    name = Column(String(100), nullable=False)
    phone_number = Column(String(20), nullable=False)
    relationship_type = Column(String(50), nullable=False, default="Family")
    is_primary = Column(Boolean, default=False)
    status = Column(String(20), nullable=False, default="PENDING")  # PENDING, ACCEPTED, REJECTED, REVOKED
    contact_user_id = Column(Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    user = relationship("User", foreign_keys=[user_id], back_populates="emergency_contacts")
    contact_user = relationship("User", foreign_keys=[contact_user_id])
