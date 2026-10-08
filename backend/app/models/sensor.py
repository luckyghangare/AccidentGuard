from sqlalchemy import Column, Integer, Float, String, DateTime, ForeignKey
from sqlalchemy.orm import relationship
from datetime import datetime
from app.database import Base

class SensorReading(Base):
    __tablename__ = "sensor_readings"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    vehicle_id = Column(Integer, ForeignKey("vehicles.id", ondelete="CASCADE"), nullable=False, index=True)
    obstacle_distance = Column(Float, nullable=False)  # in cm
    acceleration_g = Column(Float, nullable=False)     # in g
    gyroscope_rad_s = Column(Float, nullable=False)   # in rad/s
    status = Column(String(30), nullable=False, default="SAFE")
    timestamp = Column(DateTime, default=datetime.utcnow, index=True)

    # Relationship
    vehicle = relationship("Vehicle", back_populates="sensor_readings")
