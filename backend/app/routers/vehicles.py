from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from typing import List
from app.database import get_db
from app.models import User, Vehicle
from app.schemas.vehicle import VehicleCreate, VehicleUpdate, VehicleResponse
from app.utils.response import success_response, error_response
from app.dependencies import get_current_user

router = APIRouter(prefix="/api/vehicles", tags=["Vehicles"])

@router.post("")
def create_vehicle(vehicle_in: VehicleCreate, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    vehicle = Vehicle(
        user_id=current_user.id,
        name=vehicle_in.name,
        vehicle_number=vehicle_in.vehicle_number,
        device_id=vehicle_in.device_id,
        status="connected",
        battery_level=100
    )
    db.add(vehicle)
    db.commit()
    db.refresh(vehicle)
    
    data = VehicleResponse.model_validate(vehicle).model_dump()
    return success_response(data=data, message="Vehicle registered successfully", status_code=201)

@router.get("")
def list_vehicles(current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    vehicles = db.query(Vehicle).filter(Vehicle.user_id == current_user.id).all()
    data = [VehicleResponse.model_validate(v).model_dump() for v in vehicles]
    return success_response(data=data, message="Vehicles retrieved successfully")

@router.get("/{vehicle_id}")
def get_vehicle(vehicle_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    vehicle = db.query(Vehicle).filter(Vehicle.id == vehicle_id, Vehicle.user_id == current_user.id).first()
    if not vehicle:
        return error_response("NOT_FOUND", "Vehicle not found", status_code=404)
    
    data = VehicleResponse.model_validate(vehicle).model_dump()
    return success_response(data=data, message="Vehicle retrieved successfully")

@router.put("/{vehicle_id}")
def update_vehicle(vehicle_id: int, vehicle_in: VehicleUpdate, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    vehicle = db.query(Vehicle).filter(Vehicle.id == vehicle_id, Vehicle.user_id == current_user.id).first()
    if not vehicle:
        return error_response("NOT_FOUND", "Vehicle not found", status_code=404)
    
    if vehicle_in.name is not None:
        vehicle.name = vehicle_in.name
    if vehicle_in.vehicle_number is not None:
        vehicle.vehicle_number = vehicle_in.vehicle_number
    if vehicle_in.device_id is not None:
        vehicle.device_id = vehicle_in.device_id
    if vehicle_in.status is not None:
        vehicle.status = vehicle_in.status
    if vehicle_in.battery_level is not None:
        vehicle.battery_level = vehicle_in.battery_level
        
    db.commit()
    db.refresh(vehicle)
    
    data = VehicleResponse.model_validate(vehicle).model_dump()
    return success_response(data=data, message="Vehicle updated successfully")

@router.delete("/{vehicle_id}")
def delete_vehicle(vehicle_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    vehicle = db.query(Vehicle).filter(Vehicle.id == vehicle_id, Vehicle.user_id == current_user.id).first()
    if not vehicle:
        return error_response("NOT_FOUND", "Vehicle not found", status_code=404)
    
    db.delete(vehicle)
    db.commit()
    return success_response(message="Vehicle deleted successfully")
