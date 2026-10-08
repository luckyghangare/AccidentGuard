from sqlalchemy import Column, Integer, Float, String, DateTime, ForeignKey
from sqlalchemy.orm import relationship
from datetime import datetime
from app.database import Base

class Accident(Base):
    __tablename__ = "accidents"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    vehicle_id = Column(Integer, ForeignKey("vehicles.id", ondelete="CASCADE"), nullable=False, index=True)
    date_str = Column(String(30), nullable=False)
    time_str = Column(String(30), nullable=False)
    latitude = Column(Float, nullable=False)
    longitude = Column(Float, nullable=False)
    impact_g = Column(Float, nullable=False)
    status = Column(String(50), nullable=False, default="emergency_sent")
    timestamp = Column(DateTime, default=datetime.utcnow, index=True)

    # Relationships
    vehicle = relationship("Vehicle", back_populates="accidents")
    notifications = relationship("Notification", back_populates="accident", cascade="all, delete-orphan")
