from sqlalchemy import Column, Integer, String, Boolean, DateTime, ForeignKey
from sqlalchemy.orm import relationship
from datetime import datetime
from app.database import Base

class Vehicle(Base):
    __tablename__ = "vehicles"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    name = Column(String(100), nullable=False, default="Car 01")
    vehicle_number = Column(String(50), nullable=False)
    device_id = Column(String(50), nullable=True, default="ESP32_DEV_01")
    status = Column(String(30), nullable=False, default="connected")
    battery_level = Column(Integer, default=82)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)

    # Relationships
    owner = relationship("User", back_populates="vehicles")
    gps_locations = relationship("GpsLocation", back_populates="vehicle", cascade="all, delete-orphan")
    sensor_readings = relationship("SensorReading", back_populates="vehicle", cascade="all, delete-orphan")
    accidents = relationship("Accident", back_populates="vehicle", cascade="all, delete-orphan")
