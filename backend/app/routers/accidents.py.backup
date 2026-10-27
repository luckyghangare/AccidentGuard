from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from datetime import datetime
from app.database import get_db
from app.models import Accident, Vehicle, EmergencyContact, Notification, User, Hospital, HospitalEmergencyAlert
from app.schemas.accident import AccidentCreate, AccidentResponse
from app.utils.response import success_response, error_response
from app.dependencies import get_current_user

router = APIRouter(prefix="/api/accidents", tags=["Accidents"])

@router.post("")
@router.post("/report")
def record_accident(accident_in: AccidentCreate, db: Session = Depends(get_db)):
    vehicle = db.query(Vehicle).filter(Vehicle.id == accident_in.vehicle_id).first()
    if not vehicle:
        return error_response("NOT_FOUND", "Vehicle not found", status_code=404)
    
    now = datetime.now()
    date_str = accident_in.date_str or now.strftime("%d %B %Y")
    time_str = accident_in.time_str or now.strftime("%I:%M %p")

    # Severity computation
    impact = accident_in.impact_g
    if impact >= 8.0:
        severity = "Critical / High Rollover Risk"
    elif impact >= 5.0:
        severity = "Severe Collision"
    elif impact >= 2.5:
        severity = "Moderate Impact"
    else:
        severity = "Minor Impact"

    accident = Accident(
        vehicle_id=accident_in.vehicle_id,
        date_str=date_str,
        time_str=time_str,
        latitude=accident_in.latitude,
        longitude=accident_in.longitude,
        impact_g=accident_in.impact_g,
        status="emergency_sent"
    )
    db.add(accident)
    db.commit()
    db.refresh(accident)

    # 1. Dispatch notifications to victim's emergency contacts
    contacts = db.query(EmergencyContact).filter(EmergencyContact.user_id == vehicle.user_id).all()
    for contact in contacts:
        msg = f"EMERGENCY ALERT: Crash detected on {vehicle.name} ({vehicle.vehicle_number}) at ({accident.latitude}, {accident.longitude}). Impact: {accident.impact_g}g ({severity})."
        notif = Notification(
            accident_id=accident.id,
            recipient_phone=contact.phone_number,
            message_body=msg,
            status="sent"
        )
        db.add(notif)

    # 2. Dispatch emergency alerts to registered hospitals
    hospitals = db.query(Hospital).filter(Hospital.is_active == True).all()
    for hosp in hospitals:
        alert = HospitalEmergencyAlert(
            hospital_id=hosp.id,
            accident_id=accident.id,
            status="PENDING",
            damage_severity=severity,
            notes=f"Auto-generated crash alert from Vehicle {vehicle.vehicle_number}."
        )
        db.add(alert)

    db.commit()

    data = AccidentResponse.model_validate(accident).model_dump()
    return success_response(data=data, message="Accident recorded, emergency contacts notified, and hospital alert dispatched!", status_code=201)


@router.get("")
def list_accidents(current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    # Retrieve accidents for user's vehicles
    user_vehicle_ids = [v.id for v in current_user.vehicles]
    if not user_vehicle_ids:
        return success_response(data=[], message="No accidents recorded")
        
    accidents = db.query(Accident).filter(Accident.vehicle_id.in_(user_vehicle_ids)).order_by(Accident.timestamp.desc()).all()
    
    data = [AccidentResponse.model_validate(acc).model_dump() for acc in accidents]
    return success_response(data=data, message="Accidents retrieved successfully")


@router.get("/{accident_id}")
def get_accident_detail(accident_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    accident = db.query(Accident).filter(Accident.id == accident_id).first()
    if not accident:
        return error_response("NOT_FOUND", "Accident record not found", status_code=404)
    
    data = AccidentResponse.model_validate(accident).model_dump()
    return success_response(data=data, message="Accident details retrieved successfully")

@router.put("/{accident_id}/cancel")
def cancel_accident_alert(accident_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    accident = db.query(Accident).filter(Accident.id == accident_id).first()
    if not accident:
        return error_response("NOT_FOUND", "Accident record not found", status_code=404)
    
    accident.status = "cancelled"
    db.commit()
    db.refresh(accident)
    
    data = AccidentResponse.model_validate(accident).model_dump()
    return success_response(data=data, message="Emergency alert cancelled successfully")
