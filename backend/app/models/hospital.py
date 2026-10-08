from sqlalchemy import Column, Integer, String, Float, Boolean, DateTime, ForeignKey, Text
from sqlalchemy.orm import relationship
from datetime import datetime
from app.database import Base

class Hospital(Base):
    __tablename__ = "hospitals"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    name = Column(String(150), nullable=False)
    email = Column(String(120), unique=True, index=True, nullable=False)
    phone_number = Column(String(30), nullable=False)
    password_hash = Column(String(255), nullable=False)
    address = Column(String(255), nullable=False)
    city = Column(String(100), default="Nagpur")
    latitude = Column(Float, default=21.1458)
    longitude = Column(Float, default=79.0882)
    total_ambulances = Column(Integer, default=5)
    available_ambulances = Column(Integer, default=5)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)

    # Relationships
    alerts = relationship("HospitalEmergencyAlert", back_populates="hospital", cascade="all, delete-orphan")


class HospitalEmergencyAlert(Base):
    __tablename__ = "hospital_emergency_alerts"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    hospital_id = Column(Integer, ForeignKey("hospitals.id", ondelete="CASCADE"), nullable=False, index=True)
    accident_id = Column(Integer, ForeignKey("accidents.id", ondelete="CASCADE"), nullable=False, index=True)
    status = Column(String(50), nullable=False, default="PENDING")  # PENDING, DISPATCHED, ADMITTED, CANCELLED
    damage_severity = Column(String(50), nullable=False, default="Severe Impact")
    ambulance_number = Column(String(50), nullable=True)
    driver_name = Column(String(100), nullable=True)
    driver_phone = Column(String(30), nullable=True)
    eta_minutes = Column(Integer, default=10)
    notes = Column(Text, nullable=True)
    dispatched_at = Column(DateTime, nullable=True)
    admitted_at = Column(DateTime, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)

    # Relationships
    hospital = relationship("Hospital", back_populates="alerts")
    accident = relationship("Accident")
