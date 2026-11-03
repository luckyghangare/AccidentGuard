from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import List

from app.database import get_db
from app.config import settings
from app.dependencies import get_current_user
from app.models.user import User
from app.models.call import EmergencyCallSession, CallParticipant
from app.schemas.call import (
    EmergencyCallSessionResponse, 
    CallAcceptResponse, 
    CallActionRequest,
    CallTokenResponse
)
from app.services.call_orchestration_service import CallOrchestrationService
from app.services.livekit_service import livekit_service
from app.utils.response import success_response, error_response

router = APIRouter(prefix="/api/calls", tags=["Emergency Calls"])

@router.get("/active", response_model=List[EmergencyCallSessionResponse])
def get_active_calls(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Returns active emergency call sessions where the current user is either the victim or an invited responder.
    """
    # Proactive cleanup of stale sessions
    CallOrchestrationService.cleanup_stale_sessions(db)

    # Find sessions where user is a participant
    from sqlalchemy.orm import joinedload
    sessions = db.query(EmergencyCallSession).join(CallParticipant).options(
        joinedload(EmergencyCallSession.participants).joinedload(CallParticipant.user)
    ).filter(
        CallParticipant.user_id == current_user.id,
        EmergencyCallSession.status.in_(["RINGING", "CONNECTING", "CONNECTED"])
    ).all()
    
    return sessions

@router.get("/{session_id}", response_model=EmergencyCallSessionResponse)
def get_call_details(
    session_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    from sqlalchemy.orm import joinedload
    session = db.query(EmergencyCallSession).options(
        joinedload(EmergencyCallSession.participants).joinedload(CallParticipant.user)
    ).filter(EmergencyCallSession.id == session_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="Call session not found")
    
    # Check authorization
    participant = db.query(CallParticipant).filter(
        CallParticipant.session_id == session_id,
        CallParticipant.user_id == current_user.id
    ).first()
    
    if not participant:
        raise HTTPException(status_code=403, detail="Not authorized to view this call")
    
    return session

@router.post("/{session_id}/accept")
def accept_call(
    session_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    try:
        session = CallOrchestrationService.accept_call(db, session_id, current_user.id)
        
        # Generate token for the winner
        token = livekit_service.generate_token(
            room_name=session.livekit_room_name,
            participant_identity=str(current_user.id),
            participant_name=current_user.full_name
        )
        
        return success_response(
            data={
                "session": EmergencyCallSessionResponse.from_orm(session).dict(),
                "token": token,
                "livekit_url": settings.LIVEKIT_URL
            },
            message="Call accepted successfully. Winner selected."
        )
    except ValueError as e:
        return error_response("CONFLICT", str(e), status_code=409)
    except Exception as e:
        return error_response("SERVER_ERROR", str(e), status_code=500)

@router.post("/{session_id}/reject")
def reject_call(
    session_id: int,
    action: CallActionRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    CallOrchestrationService.reject_call(db, session_id, current_user.id, action.reason)
    return success_response(message="Call invitation rejected")

@router.post("/{session_id}/token", response_model=CallTokenResponse)
def get_call_token(
    session_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    session = db.query(EmergencyCallSession).filter(EmergencyCallSession.id == session_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="Call session not found")
    
    if session.status in ["ENDED", "CANCELLED", "FAILED"]:
        raise HTTPException(status_code=400, detail=f"Call session is in a terminal state: {session.status}")

    # Authorization Check:
    # 1. Victim is always allowed if session is active
    # 2. Responders are only allowed if they are the WINNER
    
    is_victim = session.victim_user_id == current_user.id
    is_winner = session.winning_contact_user_id == current_user.id
    
    if not (is_victim or is_winner):
        raise HTTPException(status_code=403, detail="Not authorized to join this call")
    
    token = livekit_service.generate_token(
        room_name=session.livekit_room_name,
        participant_identity=str(current_user.id),
        participant_name=current_user.full_name
    )
    
    return {
        "token": token,
        "room_name": session.livekit_room_name,
        "participant_identity": str(current_user.id),
        "livekit_url": settings.LIVEKIT_URL
    }

@router.post("/{session_id}/end")
def end_call(
    session_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    session = db.query(EmergencyCallSession).filter(EmergencyCallSession.id == session_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="Call session not found")
    
    # Only victim or winner can end the call
    if current_user.id not in [session.victim_user_id, session.winning_contact_user_id]:
        raise HTTPException(status_code=403, detail="Not authorized to end this call")
    
    session.status = "ENDED"
    session.ended_at = datetime.utcnow()
    db.commit()
    
    return success_response(message="Call ended")
