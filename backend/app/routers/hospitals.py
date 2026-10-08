from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from datetime import datetime
from typing import List, Optional
from app.database import get_db
from app.models import Hospital, HospitalEmergencyAlert, Accident, Vehicle, User, Notification
from app.schemas.hospital import (
    HospitalRegister, HospitalLogin, HospitalResponse,
    AmbulanceDispatch, HospitalFleetUpdate, EmergencyAlertResponse, VictimInfo
)
from app.utils.security import hash_password, verify_password, create_access_token
from app.utils.response import success_response, error_response
from app.dependencies import get_current_hospital

router = APIRouter(prefix="/api/hospitals", tags=["Hospital Operations"])


def format_alert_response(alert: HospitalEmergencyAlert, db: Session) -> dict:
    accident = alert.accident
    vehicle = db.query(Vehicle).filter(Vehicle.id == accident.vehicle_id).first()
    owner = db.query(User).filter(User.id == vehicle.user_id).first() if vehicle else None

    # Calculate damage severity text if not set
    impact = accident.impact_g
    if impact >= 8.0:
        severity = "Critical / High Rollover Risk"
    elif impact >= 5.0:
        severity = "Severe Collision"
    elif impact >= 2.5:
        severity = "Moderate Impact"
    else:
        severity = "Minor Impact"

    victim_data = {
        "user_name": owner.full_name if owner else "Unknown Driver",
        "user_phone": owner.phone_number if owner else "N/A",
        "user_email": owner.email if owner else "N/A",
        "vehicle_name": vehicle.name if vehicle else "Unknown Vehicle",
        "vehicle_number": vehicle.vehicle_number if vehicle else "N/A",
        "device_id": vehicle.device_id if vehicle else "N/A"
    }

    return {
        "id": alert.id,
        "hospital_id": alert.hospital_id,
        "accident_id": alert.accident_id,
        "status": alert.status,
        "damage_severity": alert.damage_severity or severity,
        "latitude": accident.latitude,
        "longitude": accident.longitude,
        "impact_g": accident.impact_g,
        "date_str": accident.date_str,
        "time_str": accident.time_str,
        "maps_url": f"https://www.google.com/maps/search/?api=1&query={accident.latitude},{accident.longitude}",
        "victim": victim_data,
        "ambulance_number": alert.ambulance_number,
        "driver_name": alert.driver_name,
        "driver_phone": alert.driver_phone,
        "eta_minutes": alert.eta_minutes,
        "notes": alert.notes,
        "dispatched_at": alert.dispatched_at.isoformat() if alert.dispatched_at else None,
        "admitted_at": alert.admitted_at.isoformat() if alert.admitted_at else None,
        "created_at": alert.created_at.isoformat() if alert.created_at else None
    }


@router.post("/register")
def register_hospital(hospital_in: HospitalRegister, db: Session = Depends(get_db)):
    existing = db.query(Hospital).filter(Hospital.email == hospital_in.email).first()
    if existing:
        return error_response("EMAIL_EXISTS", "Hospital with this email is already registered", status_code=400)

    hospital = Hospital(
        name=hospital_in.name,
        email=hospital_in.email,
        phone_number=hospital_in.phone_number,
        password_hash=hash_password(hospital_in.password),
        address=hospital_in.address,
        city=hospital_in.city or "Nagpur",
        latitude=hospital_in.latitude or 21.1458,
        longitude=hospital_in.longitude or 79.0882,
        total_ambulances=hospital_in.total_ambulances or 5,
        available_ambulances=hospital_in.total_ambulances or 5,
        is_active=True
    )
    db.add(hospital)
    db.commit()
    db.refresh(hospital)

    token = create_access_token(data={"sub": hospital.email, "role": "hospital", "hospital_id": hospital.id})
    hospital_data = HospitalResponse.model_validate(hospital).model_dump()

    return success_response(
        data={"access_token": token, "token_type": "bearer", "hospital": hospital_data},
        message="Hospital registered successfully",
        status_code=201
    )


@router.post("/login")
def login_hospital(credentials: HospitalLogin, db: Session = Depends(get_db)):
    hospital = db.query(Hospital).filter(Hospital.email == credentials.email).first()
    if not hospital or not verify_password(credentials.password, hospital.password_hash):
        return error_response("INVALID_CREDENTIALS", "Invalid hospital email or password", status_code=401)

    token = create_access_token(data={"sub": hospital.email, "role": "hospital", "hospital_id": hospital.id})
    hospital_data = HospitalResponse.model_validate(hospital).model_dump()

    return success_response(
        data={"access_token": token, "token_type": "bearer", "hospital": hospital_data},
        message="Hospital login successful"
    )


@router.get("/me")
def get_hospital_profile(current_hospital: Hospital = Depends(get_current_hospital)):
    data = HospitalResponse.model_validate(current_hospital).model_dump()
    return success_response(data=data, message="Hospital details fetched")


@router.get("/alerts")
def get_emergency_alerts(current_hospital: Hospital = Depends(get_current_hospital), db: Session = Depends(get_db)):
    alerts = db.query(HospitalEmergencyAlert).filter(
        HospitalEmergencyAlert.hospital_id == current_hospital.id
    ).order_by(HospitalEmergencyAlert.created_at.desc()).all()

    formatted_alerts = [format_alert_response(a, db) for a in alerts]
    return success_response(data=formatted_alerts, message="Emergency alerts retrieved successfully")


@router.get("/alerts/{alert_id}")
def get_alert_detail(alert_id: int, current_hospital: Hospital = Depends(get_current_hospital), db: Session = Depends(get_db)):
    alert = db.query(HospitalEmergencyAlert).filter(
        HospitalEmergencyAlert.id == alert_id,
        HospitalEmergencyAlert.hospital_id == current_hospital.id
    ).first()
    if not alert:
        return error_response("NOT_FOUND", "Emergency alert not found", status_code=404)

    data = format_alert_response(alert, db)
    return success_response(data=data, message="Alert details retrieved")


@router.post("/alerts/{alert_id}/dispatch")
def dispatch_ambulance(
    alert_id: int,
    dispatch_in: Optional[AmbulanceDispatch] = None,
    current_hospital: Hospital = Depends(get_current_hospital),
    db: Session = Depends(get_db)
):
    alert = db.query(HospitalEmergencyAlert).filter(
        HospitalEmergencyAlert.id == alert_id,
        HospitalEmergencyAlert.hospital_id == current_hospital.id
    ).first()
    if not alert:
        return error_response("NOT_FOUND", "Emergency alert not found", status_code=404)

    amb_num = (dispatch_in.ambulance_number if dispatch_in and dispatch_in.ambulance_number else None) or f"AMB-{(alert.id % 15) + 1:02d}"
    driver = (dispatch_in.driver_name if dispatch_in and dispatch_in.driver_name else None) or "Emergency Response Team"
    phone = (dispatch_in.driver_phone if dispatch_in and dispatch_in.driver_phone else None) or (current_hospital.phone_number or "108")
    eta = (dispatch_in.eta_minutes if dispatch_in and dispatch_in.eta_minutes else None) or 8
    notes_text = (dispatch_in.notes if dispatch_in and dispatch_in.notes else None) or "Direct emergency dispatch from hospital dashboard"

    alert.status = "DISPATCHED"
    alert.ambulance_number = amb_num
    alert.driver_name = driver
    alert.driver_phone = phone
    alert.eta_minutes = eta
    alert.notes = notes_text
    alert.dispatched_at = datetime.utcnow()

    # Update available ambulance count
    if current_hospital.available_ambulances > 0:
        current_hospital.available_ambulances -= 1

    # Update accident record
    accident = alert.accident
    if accident:
        accident.status = f"ambulance_dispatched ({amb_num})"

    db.commit()
    db.refresh(alert)

    data = format_alert_response(alert, db)
    return success_response(data=data, message=f"Ambulance {amb_num} dispatched directly! ETA: {eta} mins")


@router.put("/fleet")
@router.patch("/fleet")
def update_hospital_fleet(
    fleet_in: HospitalFleetUpdate,
    current_hospital: Hospital = Depends(get_current_hospital),
    db: Session = Depends(get_db)
):
    if fleet_in.total_ambulances is not None:
        current_hospital.total_ambulances = max(1, fleet_in.total_ambulances)
    if fleet_in.available_ambulances is not None:
        current_hospital.available_ambulances = max(0, min(fleet_in.available_ambulances, current_hospital.total_ambulances))

    db.commit()
    db.refresh(current_hospital)
    data = HospitalResponse.model_validate(current_hospital).model_dump()
    return success_response(data=data, message="Ambulance fleet status updated successfully")


@router.post("/alerts/{alert_id}/admit")
def admit_patient(
    alert_id: int,
    notes: Optional[dict] = None,
    current_hospital: Hospital = Depends(get_current_hospital),
    db: Session = Depends(get_db)
):
    alert = db.query(HospitalEmergencyAlert).filter(
        HospitalEmergencyAlert.id == alert_id,
        HospitalEmergencyAlert.hospital_id == current_hospital.id
    ).first()
    if not alert:
        return error_response("NOT_FOUND", "Emergency alert not found", status_code=404)

    alert.status = "ADMITTED"
    alert.admitted_at = datetime.utcnow()
    if notes and "notes" in notes:
        alert.notes = notes["notes"]

    # Free up ambulance
    if current_hospital.available_ambulances < current_hospital.total_ambulances:
        current_hospital.available_ambulances += 1

    accident = alert.accident
    if accident:
        accident.status = "patient_admitted"

    db.commit()
    db.refresh(alert)

    data = format_alert_response(alert, db)
    return success_response(data=data, message="Patient successfully marked as Admitted/Treated")


@router.get("/stats")
def get_hospital_stats(current_hospital: Hospital = Depends(get_current_hospital), db: Session = Depends(get_db)):
    total = db.query(HospitalEmergencyAlert).filter(HospitalEmergencyAlert.hospital_id == current_hospital.id).count()
    pending = db.query(HospitalEmergencyAlert).filter(
        HospitalEmergencyAlert.hospital_id == current_hospital.id,
        HospitalEmergencyAlert.status == "PENDING"
    ).count()
    dispatched = db.query(HospitalEmergencyAlert).filter(
        HospitalEmergencyAlert.hospital_id == current_hospital.id,
        HospitalEmergencyAlert.status == "DISPATCHED"
    ).count()
    admitted = db.query(HospitalEmergencyAlert).filter(
        HospitalEmergencyAlert.hospital_id == current_hospital.id,
        HospitalEmergencyAlert.status == "ADMITTED"
    ).count()

    stats = {
        "hospital_name": current_hospital.name,
        "total_emergencies": total,
        "pending_emergencies": pending,
        "active_dispatches": dispatched,
        "admitted_patients": admitted,
        "total_ambulances": current_hospital.total_ambulances,
        "available_ambulances": current_hospital.available_ambulances
    }
    return success_response(data=stats, message="Hospital stats fetched")
