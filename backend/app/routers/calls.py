
from datetime import datetime
from typing import List

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, joinedload

from app.database import get_db
from app.config import settings
from app.dependencies import get_current_user
from app.models.user import User
from app.models.call import EmergencyCallSession, CallParticipant
from app.schemas.call import (
    EmergencyCallSessionResponse,
    CallAcceptResponse,
    CallActionRequest,
    CallTokenResponse,
)
from app.services.call_orchestration_service import CallOrchestrationService
from app.services.livekit_service import livekit_service
from app.services.notification_service import NotificationService
from app.utils.response import success_response, error_response


router = APIRouter(
    prefix="/api/calls",
    tags=["Emergency Calls"]
)


@router.get(
    "/active",
    response_model=List[EmergencyCallSessionResponse]
)
def get_active_calls(
        current_user: User = Depends(get_current_user),
        db: Session = Depends(get_db),
):
    """
    Returns active emergency call sessions where the current user
    is either the victim or an invited responder.
    """

    CallOrchestrationService.cleanup_stale_sessions(db)

    sessions = (
        db.query(EmergencyCallSession)
        .join(CallParticipant)
        .options(
            joinedload(
                EmergencyCallSession.participants
            ).joinedload(
                CallParticipant.user
            )
        )
        .filter(
            CallParticipant.user_id == current_user.id,
            EmergencyCallSession.status.in_(
                ["RINGING", "CONNECTING", "CONNECTED"]
            ),
            )
        .all()
    )

    return sessions


@router.get(
    "/{session_id}",
    response_model=EmergencyCallSessionResponse,
)
def get_call_details(
        session_id: int,
        current_user: User = Depends(get_current_user),
        db: Session = Depends(get_db),
):
    session = (
        db.query(EmergencyCallSession)
        .options(
            joinedload(
                EmergencyCallSession.participants
            ).joinedload(
                CallParticipant.user
            )
        )
        .filter(
            EmergencyCallSession.id == session_id
        )
        .first()
    )

    if not session:
        raise HTTPException(
            status_code=404,
            detail="Call session not found",
        )

    participant = (
        db.query(CallParticipant)
        .filter(
            CallParticipant.session_id == session_id,
            CallParticipant.user_id == current_user.id,
            )
        .first()
    )

    if not participant:
        raise HTTPException(
            status_code=403,
            detail="Not authorized to view this call",
        )

    return session


@router.post("/{session_id}/accept")
def accept_call(
        session_id: int,
        current_user: User = Depends(get_current_user),
        db: Session = Depends(get_db),
):
    try:
        session = CallOrchestrationService.accept_call(
            db,
            session_id,
            current_user.id,
        )

        if session is None:
            return error_response(
                "NOT_FOUND",
                "Call session not found",
                status_code=404,
            )

        token = livekit_service.generate_token(
            room_name=session.livekit_room_name,
            participant_identity=str(current_user.id),
            participant_name=current_user.full_name,
        )

        return success_response(
            data={
                "session": EmergencyCallSessionResponse.from_orm(
                    session
                ).dict(),
                "token": token,
                "livekit_url": settings.LIVEKIT_URL,
            },
            message="Call accepted successfully. Winner selected.",
        )

    except ValueError as e:
        return error_response(
            "CONFLICT",
            str(e),
            status_code=409,
        )

    except Exception as e:
        db.rollback()
        return error_response(
            "SERVER_ERROR",
            str(e),
            status_code=500,
        )


@router.post("/{session_id}/reject")
def reject_call(
        session_id: int,
        action: CallActionRequest,
        current_user: User = Depends(get_current_user),
        db: Session = Depends(get_db),
):
    CallOrchestrationService.reject_call(
        db,
        session_id,
        current_user.id,
        action.reason,
    )

    return success_response(
        message="Call invitation rejected"
    )


@router.post(
    "/{session_id}/token",
    response_model=CallTokenResponse,
)
def get_call_token(
        session_id: int,
        current_user: User = Depends(get_current_user),
        db: Session = Depends(get_db),
):
    session = (
        db.query(EmergencyCallSession)
        .filter(
            EmergencyCallSession.id == session_id
        )
        .first()
    )

    if not session:
        raise HTTPException(
            status_code=404,
            detail="Call session not found",
        )

    if session.status in [
        "ENDED",
        "CANCELLED",
        "FAILED",
        "EXPIRED",
    ]:
        raise HTTPException(
            status_code=400,
            detail=f"Call session is in a terminal state: {session.status}",
        )

    is_victim = (
            session.victim_user_id == current_user.id
    )

    is_winner = (
            session.winning_contact_user_id == current_user.id
    )

    if not (is_victim or is_winner):
        raise HTTPException(
            status_code=403,
            detail="Not authorized to join this call",
        )

    token = livekit_service.generate_token(
        room_name=session.livekit_room_name,
        participant_identity=str(current_user.id),
        participant_name=current_user.full_name,
    )

    return {
        "token": token,
        "room_name": session.livekit_room_name,
        "participant_identity": str(current_user.id),
        "livekit_url": settings.LIVEKIT_URL,
    }


# ---------------------------------------------------------
# END EMERGENCY CALL
# ---------------------------------------------------------
@router.post("/{session_id}/end")
async def end_call(
        session_id: int,
        current_user: User = Depends(get_current_user),
        db: Session = Depends(get_db),
):
    try:
        # 1. Lock session to prevent concurrent database updates.
        session = (
            db.query(EmergencyCallSession)
            .filter(
                EmergencyCallSession.id == session_id
            )
            .with_for_update()
            .first()
        )

        if not session:
            raise HTTPException(
                status_code=404,
                detail="Call session not found",
            )

        # 2. Only victim or selected responder can end the call.
        if current_user.id not in [
            session.victim_user_id,
            session.winning_contact_user_id,
        ]:
            raise HTTPException(
                status_code=403,
                detail="Not authorized to end this call",
            )

        # 3. Identify the remote participant.
        if current_user.id == session.victim_user_id:
            remote_user_id = session.winning_contact_user_id
        else:
            remote_user_id = session.victim_user_id

        room_name = session.livekit_room_name
        victim_user_id = session.victim_user_id

        # 4. Get remote user and victim details before commit.
        remote_user = None
        if remote_user_id is not None:
            remote_user = (
                db.query(User)
                .filter(User.id == remote_user_id)
                .first()
            )

        victim_user = (
            db.query(User)
            .filter(User.id == victim_user_id)
            .first()
        )

        victim_name = (
            victim_user.full_name
            if victim_user and victim_user.full_name
            else "Someone"
        )

        remote_fcm_token = (
            remote_user.fcm_token
            if remote_user
            else None
        )

        # 5. Update database only if not already ended.
        already_ended = session.status == "ENDED"

        if not already_ended:
            ended_time = datetime.utcnow()

            participants = (
                db.query(CallParticipant)
                .filter(
                    CallParticipant.session_id == session_id
                )
                .with_for_update()
                .all()
            )

            for participant in participants:

                if participant.status in [
                    "ACCEPTED",
                    "CONNECTED",
                    "PENDING",
                ]:
                    participant.status = "DISCONNECTED"
                    participant.left_at = ended_time

                elif participant.status == "RINGING":
                    participant.status = "MISSED"

            session.status = "ENDED"
            session.ended_at = ended_time

            db.commit()

        # 6. Remove remote participant from LiveKit.
        livekit_removed = False

        if remote_user_id is not None and room_name:
            livekit_removed = await livekit_service.remove_participant(
                room_name=room_name,
                participant_identity=str(remote_user_id),
            )

        # 7. Send data-only FCM event to remote participant.
        notification_sent = False

        if remote_fcm_token:
            notification_sent = (
                NotificationService.send_call_ended_notification(
                    db=db,
                    token=remote_fcm_token,
                    session_id=session_id,
                    victim_name=victim_name,
                )
            )

        # 8. Return result.
        return success_response(
            data={
                "session_id": session_id,
                "status": "ENDED",
                "already_ended": already_ended,
                "livekit_removed": livekit_removed,
                "remote_notification_sent": notification_sent,
            },
            message="Call ended successfully",
        )

    except HTTPException:
        db.rollback()
        raise

    except Exception as e:
        db.rollback()

        return error_response(
            "SERVER_ERROR",
            str(e),
            status_code=500,
        )
