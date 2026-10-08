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
    if contact_in.is_primary:
        # Unset previous primary contacts
        db.query(EmergencyContact).filter(EmergencyContact.user_id == current_user.id).update({"is_primary": False})
        db.commit()

    contact = EmergencyContact(
        user_id=current_user.id,
        name=contact_in.name,
        phone_number=contact_in.phone_number,
        relationship_type=contact_in.relationship_type,
        is_primary=contact_in.is_primary
    )
    db.add(contact)
    db.commit()
    db.refresh(contact)
    
    data = ContactResponse.model_validate(contact).model_dump()
    return success_response(data=data, message="Emergency contact added successfully", status_code=201)

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
    
    if contact_in.is_primary:
        db.query(EmergencyContact).filter(EmergencyContact.user_id == current_user.id).update({"is_primary": False})
        db.commit()

    if contact_in.name is not None:
        contact.name = contact_in.name
    if contact_in.phone_number is not None:
        contact.phone_number = contact_in.phone_number
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
