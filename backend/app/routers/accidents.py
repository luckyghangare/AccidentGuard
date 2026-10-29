
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from datetime import datetime

from app.database import get_db
from app.models import (
    Accident,
    Vehicle,
    EmergencyContact,
    Notification,
    User,
    Hospital,
    HospitalEmergencyAlert,
)
from app.schemas.accident import AccidentCreate, AccidentResponse
from app.utils.response import success_response, error_response
from app.dependencies import get_current_user
from app.services.notification_service import NotificationService

router = APIRouter(prefix="/api/accidents", tags=["Accidents"])


def get_user_accident(
        accident_id: int,
        current_user: User,
        db: Session,
):
    """Return an accident only when it belongs to the current user's vehicle."""
    return (
        db.query(Accident)
        .join(Vehicle, Accident.vehicle_id == Vehicle.id)
        .filter(
            Accident.id == accident_id,
            Vehicle.user_id == current_user.id,
            )
        .first()
    )


@router.post("")
@router.post("/report")
def record_accident(
        accident_in: AccidentCreate,
        current_user: User = Depends(get_current_user),
        db: Session = Depends(get_db),
):
    vehicle = (
        db.query(Vehicle)
        .filter(
            Vehicle.id == accident_in.vehicle_id,
            Vehicle.user_id == current_user.id,
        )
        .first()
    )

    if not vehicle:
        return error_response(
            "NOT_FOUND",
            "Vehicle not found",
            status_code=404,
        )

    now = datetime.now()
    date_str = accident_in.date_str or now.strftime("%d %B %Y")
    time_str = accident_in.time_str or now.strftime("%I:%M %p")

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
        vehicle_id=vehicle.id,
        date_str=date_str,
        time_str=time_str,
        latitude=accident_in.latitude,
        longitude=accident_in.longitude,
        impact_g=impact,
        status="emergency_sent",
    )

    try:
        db.add(accident)
        db.flush()

        contacts = (
            db.query(EmergencyContact)
            .filter(EmergencyContact.user_id == vehicle.user_id)
            .all()
        )

        for contact in contacts:
            msg = (
                f"EMERGENCY ALERT: Crash detected on {vehicle.name} "
                f"({vehicle.vehicle_number}) at "
                f"({accident.latitude}, {accident.longitude}). "
                f"Impact: {impact}g ({severity})."
            )

            db.add(
                Notification(
                    accident_id=accident.id,
                    recipient_phone=contact.phone_number,
                    message_body=msg,
                    status="sent",
                )
            )

        hospitals = (
            db.query(Hospital)
            .filter(Hospital.is_active.is_(True))
            .all()
        )

        for hospital in hospitals:
            db.add(
                HospitalEmergencyAlert(
                    hospital_id=hospital.id,
                    accident_id=accident.id,
                    status="PENDING",
                    damage_severity=severity,
                    notes=(
                        "Auto-generated crash alert from Vehicle "
                        f"{vehicle.vehicle_number}."
                    ),
                )
            )

        db.commit()
        db.refresh(accident)

        # Send accident notifications
        owner = current_user
        victim_name = owner.full_name

        # 1. Notify the owner/victim themselves
        if owner.fcm_token:
            NotificationService.send_accident_alert(
                db=db,
                token=owner.fcm_token,
                accident_id=accident.id,
                location_text=f"{accident.latitude}, {accident.longitude}",
                victim_name="You"
            )

        # 2. Notify all ACCEPTED emergency contacts who are registered users
        accepted_contacts = (
            db.query(EmergencyContact)
            .filter(
                EmergencyContact.user_id == owner.id,
                EmergencyContact.status == "ACCEPTED",
                EmergencyContact.contact_user_id.isnot(None)
            )
            .all()
        )

        for contact in accepted_contacts:
            contact_user = db.query(User).filter(User.id == contact.contact_user_id).first()
            if contact_user and contact_user.fcm_token:
                NotificationService.send_accident_alert(
                    db=db,
                    token=contact_user.fcm_token,
                    accident_id=accident.id,
                    location_text=f"{accident.latitude}, {accident.longitude}",
                    victim_name=victim_name
                )

    except Exception:
        db.rollback()
        raise

    data = AccidentResponse.model_validate(accident).model_dump()

    return success_response(
        data=data,
        message=(
            "Accident recorded and notification/hospital alert "
            "records created."
        ),
        status_code=201,
    )


@router.get("")
def list_accidents(
        current_user: User = Depends(get_current_user),
        db: Session = Depends(get_db),
):
    user_vehicle_ids = [
        vehicle.id for vehicle in current_user.vehicles
    ]

    if not user_vehicle_ids:
        return success_response(
            data=[],
            message="No accidents recorded",
        )

    accidents = (
        db.query(Accident)
        .filter(Accident.vehicle_id.in_(user_vehicle_ids))
        .order_by(Accident.timestamp.desc())
        .all()
    )

    data = [
        AccidentResponse.model_validate(accident).model_dump()
        for accident in accidents
    ]

    return success_response(
        data=data,
        message="Accidents retrieved successfully",
    )


@router.get("/{accident_id}")
def get_accident_detail(
        accident_id: int,
        current_user: User = Depends(get_current_user),
        db: Session = Depends(get_db),
):
    accident = get_user_accident(accident_id, current_user, db)

    if not accident:
        return error_response(
            "NOT_FOUND",
            "Accident record not found",
            status_code=404,
        )

    data = AccidentResponse.model_validate(accident).model_dump()

    return success_response(
        data=data,
        message="Accident details retrieved successfully",
    )


@router.put("/{accident_id}/cancel")
def cancel_accident_alert(
        accident_id: int,
        current_user: User = Depends(get_current_user),
        db: Session = Depends(get_db),
):
    accident = get_user_accident(accident_id, current_user, db)

    if not accident:
        return error_response(
            "NOT_FOUND",
            "Accident record not found",
            status_code=404,
        )

    if accident.status == "cancelled":
        data = AccidentResponse.model_validate(accident).model_dump()
        return success_response(
            data=data,
            message="Accident alert is already cancelled",
        )

    try:
        accident.status = "cancelled"

        # Only pending hospital alerts can be cancelled safely here.
        (
            db.query(HospitalEmergencyAlert)
            .filter(
                HospitalEmergencyAlert.accident_id == accident.id,
                HospitalEmergencyAlert.status == "PENDING",
                )
            .update(
                {"status": "CANCELLED"},
                synchronize_session=False,
            )
        )

        db.commit()
        db.refresh(accident)

        # Send accident notifications
        owner = current_user
        victim_name = owner.full_name

        # 1. Notify the owner/victim themselves
        if owner.fcm_token:
            NotificationService.send_accident_alert(
                db=db,
                token=owner.fcm_token,
                accident_id=accident.id,
                location_text=f"{accident.latitude}, {accident.longitude}",
                victim_name="You"
            )

        # 2. Notify all ACCEPTED emergency contacts who are registered users
        accepted_contacts = (
            db.query(EmergencyContact)
            .filter(
                EmergencyContact.user_id == owner.id,
                EmergencyContact.status == "ACCEPTED",
                EmergencyContact.contact_user_id.isnot(None)
            )
            .all()
        )

        for contact in accepted_contacts:
            contact_user = db.query(User).filter(User.id == contact.contact_user_id).first()
            if contact_user and contact_user.fcm_token:
                NotificationService.send_accident_alert(
                    db=db,
                    token=contact_user.fcm_token,
                    accident_id=accident.id,
                    location_text=f"{accident.latitude}, {accident.longitude}",
                    victim_name=victim_name
                )

    except Exception:
        db.rollback()
        raise

    data = AccidentResponse.model_validate(accident).model_dump()

    return success_response(
        data=data,
        message=(
            "Accident marked as cancelled. Pending hospital alerts "
            "were cancelled where applicable."
        ),
    )
