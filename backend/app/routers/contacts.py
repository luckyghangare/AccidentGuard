from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from app.database import get_db
from app.models import EmergencyContact, User
from app.schemas.contact import ContactCreate, ContactUpdate, ContactResponse
from app.utils.response import success_response, error_response
from app.dependencies import get_current_user

router = APIRouter(prefix="/api/contacts", tags=["Emergency Contacts"])

@router.post("")
def add_contact(contact_in: ContactCreate, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    # Check if contact already exists for this user
    existing = db.query(EmergencyContact).filter(
        EmergencyContact.user_id == current_user.id,
        EmergencyContact.phone_number == contact_in.phone_number
    ).first()

    if existing:
        return error_response("CONFLICT", "Emergency contact with this phone number already exists", status_code=400)

    # Check if the invited person is already a registered user
    contact_user = db.query(User).filter(User.phone_number == contact_in.phone_number).first()
    contact_user_id = contact_user.id if contact_user else None

    if contact_user_id == current_user.id or contact_in.phone_number == current_user.phone_number:
        return error_response("VALIDATION_ERROR", "You cannot invite yourself", status_code=400)

    if contact_in.is_primary:
        # Unset previous primary contacts
        db.query(EmergencyContact).filter(EmergencyContact.user_id == current_user.id).update({"is_primary": False})

    contact = EmergencyContact(
        user_id=current_user.id,
        contact_user_id=contact_user_id,
        name=contact_in.name,
        phone_number=contact_in.phone_number,
        relationship_type=contact_in.relationship_type,
        is_primary=contact_in.is_primary,
        status="PENDING"  # Always start as PENDING for consent-based policy
    )
    db.add(contact)
    db.commit()
    db.refresh(contact)

    data = ContactResponse.model_validate(contact).model_dump()
    return success_response(data=data, message="Invitation sent successfully", status_code=201)

@router.get("/invitations/received")
def list_received_invitations(current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    invitations = db.query(EmergencyContact).filter(
        EmergencyContact.contact_user_id == current_user.id,
        EmergencyContact.status == "PENDING"
    ).all()
    data = [ContactResponse.model_validate(i).model_dump() for i in invitations]
    return success_response(data=data, message="Received invitations retrieved successfully")

@router.post("/invitations/{invitation_id}/accept")
def accept_invitation(invitation_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    invitation = db.query(EmergencyContact).filter(
        EmergencyContact.id == invitation_id,
        EmergencyContact.contact_user_id == current_user.id
    ).first()

    if not invitation:
        return error_response("NOT_FOUND", "Invitation not found", status_code=404)

    if invitation.status != "PENDING":
        return error_response("INVALID_STATE", f"Invitation is already {invitation.status}", status_code=400)

    invitation.status = "ACCEPTED"
    db.commit()
    db.refresh(invitation)

    data = ContactResponse.model_validate(invitation).model_dump()
    return success_response(data=data, message="Invitation accepted successfully")

@router.post("/invitations/{invitation_id}/reject")
def reject_invitation(invitation_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    invitation = db.query(EmergencyContact).filter(
        EmergencyContact.id == invitation_id,
        EmergencyContact.contact_user_id == current_user.id
    ).first()

    if not invitation:
        return error_response("NOT_FOUND", "Invitation not found", status_code=404)

    invitation.status = "REJECTED"
    db.commit()

    return success_response(message="Invitation rejected successfully")

@router.post("/invitations/{invitation_id}/cancel")
def cancel_invitation(invitation_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    invitation = db.query(EmergencyContact).filter(
        EmergencyContact.id == invitation_id,
        EmergencyContact.user_id == current_user.id
    ).first()

    if not invitation:
        return error_response("NOT_FOUND", "Invitation not found", status_code=404)

    if invitation.status != "PENDING":
        return error_response("INVALID_STATE", "Only pending invitations can be cancelled", status_code=400)

    db.delete(invitation)
    db.commit()

    return success_response(message="Invitation cancelled successfully")

@router.get("")
def list_contacts(current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    contacts = db.query(EmergencyContact).filter(EmergencyContact.user_id == current_user.id).all()
    data = [ContactResponse.model_validate(c).model_dump() for c in contacts]
    return success_response(data=data, message="Emergency contacts retrieved successfully")

@router.put("/{contact_id}")
def update_contact(contact_id: int, contact_in: ContactUpdate, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    contact = db.query(EmergencyContact).filter(EmergencyContact.id == contact_id, EmergencyContact.user_id == current_user.id).first()
    if not contact:
        return error_response("NOT_FOUND", "Emergency contact not found", status_code=404)

    if contact_in.phone_number and contact_in.phone_number != contact.phone_number:
        # Check if new phone number is user's own number
        if contact_in.phone_number == current_user.phone_number:
            return error_response("VALIDATION_ERROR", "You cannot invite yourself", status_code=400)

        # Check for duplicates
        existing = db.query(EmergencyContact).filter(
            EmergencyContact.user_id == current_user.id,
            EmergencyContact.phone_number == contact_in.phone_number
        ).first()
        if existing:
            return error_response("CONFLICT", "Another contact with this phone number already exists", status_code=400)

        # Reset consent for new phone number
        contact.phone_number = contact_in.phone_number
        contact_user = db.query(User).filter(User.phone_number == contact_in.phone_number).first()
        contact.contact_user_id = contact_user.id if contact_user else None
        contact.status = "PENDING"

    if contact_in.is_primary:
        db.query(EmergencyContact).filter(EmergencyContact.user_id == current_user.id).update({"is_primary": False})

    if contact_in.name is not None:
        contact.name = contact_in.name
    if contact_in.relationship_type is not None:
        contact.relationship_type = contact_in.relationship_type
    if contact_in.is_primary is not None:
        contact.is_primary = contact_in.is_primary

    db.commit()
    db.refresh(contact)

    data = ContactResponse.model_validate(contact).model_dump()
    return success_response(data=data, message="Emergency contact updated successfully")

@router.delete("/{contact_id}")
def delete_contact(contact_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    contact = db.query(EmergencyContact).filter(EmergencyContact.id == contact_id, EmergencyContact.user_id == current_user.id).first()
    if not contact:
        return error_response("NOT_FOUND", "Emergency contact not found", status_code=404)

    db.delete(contact)
    db.commit()
    return success_response(message="Emergency contact deleted successfully")
