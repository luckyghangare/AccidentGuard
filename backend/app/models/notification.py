from sqlalchemy import Column, Integer, String, Text, DateTime, ForeignKey
from sqlalchemy.orm import relationship
from datetime import datetime
from app.database import Base

class Notification(Base):
    __tablename__ = "notifications"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    accident_id = Column(Integer, ForeignKey("accidents.id", ondelete="CASCADE"), nullable=False, index=True)
    recipient_phone = Column(String(20), nullable=False)
    message_body = Column(Text, nullable=False)
    status = Column(String(30), nullable=False, default="sent")
    sent_at = Column(DateTime, default=datetime.utcnow)

    # Relationship
    accident = relationship("Accident", back_populates="notifications")
