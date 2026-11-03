import logging
import uuid
from datetime import datetime, timedelta
from typing import List, Optional

from sqlalchemy.orm import Session
from sqlalchemy import and_, or_

from app.models.accident import Accident
from app.models.call import EmergencyCallSession, CallParticipant
from app.models.contact import EmergencyContact
from app.models.user import User
from app.services.notification_service import NotificationService
from app.services.livekit_service import livekit_service

logger = logging.getLogger(__name__)

class CallOrchestrationService:
    @staticmethod
    def initiate_emergency_call(db: Session, accident_id: int) -> Optional[EmergencyCallSession]:
        """
        Creates an emergency call session and invites eligible contacts.
        """
        # 1. Verify accident
        accident = db.query(Accident).filter(Accident.id == accident_id).first()
        if not accident or accident.status == "cancelled":
            logger.warning(f"Accident {accident_id} not found or cancelled. Skipping call orchestration.")
            return None

        # 2. Check for existing session
        existing_session = db.query(EmergencyCallSession).filter(
            EmergencyCallSession.accident_id == accident_id
        ).first()
        if existing_session:
            logger.info(f"Call session already exists for accident {accident_id}.")
            return existing_session

        # 3. Get victim and vehicle details
        vehicle = accident.vehicle
        victim = vehicle.owner

        # 4. Get eligible contacts
        accepted_contacts = db.query(EmergencyContact).filter(
            EmergencyContact.user_id == victim.id,
            EmergencyContact.status == "ACCEPTED",
            EmergencyContact.contact_user_id.isnot(None)
        ).all()

        eligible_participants = []
        for contact in accepted_contacts:
            contact_user = db.query(User).filter(User.id == contact.contact_user_id).first()
            if contact_user and contact_user.fcm_token:
                eligible_participants.append(contact_user)

        # 5. Create Session
        room_name = f"emergency_{accident_id}_{uuid.uuid4().hex[:8]}"
        session = EmergencyCallSession(
            accident_id=accident_id,
            victim_user_id=victim.id,
            livekit_room_name=room_name,
            status="INITIATING",
            invitation_started_at=datetime.utcnow()
        )
        db.add(session)
        db.flush()

        # 6. Add victim as a participant
        victim_participant = CallParticipant(
            session_id=session.id,
            user_id=victim.id,
            participant_role="victim",
            status="PENDING"
        )
        db.add(victim_participant)

        # 7. Add eligible contacts as participants
        if not eligible_participants:
            logger.warning(f"No eligible contacts for accident {accident_id}.")
            session.status = "ALL_CONTACTS_UNREACHABLE"
            session.failure_reason = "No emergency contacts with active FCM tokens found."
            db.commit()
            return session

        participants_created = []
        for contact_user in eligible_participants:
            participant = CallParticipant(
                session_id=session.id,
                user_id=contact_user.id,
                participant_role="responder",
                status="RINGING"
            )
            db.add(participant)
            participants_created.append((participant, contact_user))

        db.commit()
        db.refresh(session)

        # 8. Send simultaneous invitations
        session.status = "RINGING"
        db.commit()

        for participant, contact_user in participants_created:
            success = NotificationService.send_fcm_notification(
                token=contact_user.fcm_token,
                title="EMERGENCY VOICE CALL",
                body=f"Emergency: {victim.full_name} needs help! Tap to join the call.",
                data={
                    "type": "EMERGENCY_CALL_INVITATION",
                    "session_id": str(session.id),
                    "accident_id": str(accident_id),
                    "victim_name": victim.full_name,
                    "room_name": room_name
                },
                db=db
            )
            if not success:
                participant.status = "UNREACHABLE"
                db.commit()

        return session

    @staticmethod
    def accept_call(db: Session, session_id: int, user_id: int) -> Optional[EmergencyCallSession]:
        """
        Atomically selects the first responder to accept.
        """
        # Start transaction with locking
        session = db.query(EmergencyCallSession).filter(
            EmergencyCallSession.id == session_id
        ).with_for_update().first()

        if not session:
            return None

        # Check for 30-second timeout
        if session.status == "RINGING" and session.invitation_started_at:
            if datetime.utcnow() > session.invitation_started_at + timedelta(seconds=35): # 5s buffer
                session.status = "EXPIRED"
                db.commit()
                raise ValueError("Emergency call invitation has expired.")

        if session.status in ["ENDED", "CANCELLED", "EXPIRED"]:
            db.commit() # Release lock
            raise ValueError(f"Call session has already {session.status.lower()}.")

        if session.winning_contact_user_id is not None:
            # If the current user IS the winner, just return the session
            if session.winning_contact_user_id == user_id:
                db.commit() # Release lock
                return session

            # Another contact already won
            participant = db.query(CallParticipant).filter(
                CallParticipant.session_id == session_id,
                CallParticipant.user_id == user_id
            ).first()
            if participant and participant.status == "RINGING":
                participant.status = "ALREADY_CONNECTED"
                db.commit()
            else:
                db.commit() # Ensure lock is released if no update needed
            raise ValueError("Another contact has already accepted this emergency call.")

        # Check if user is a valid participant for this session
        participant = db.query(CallParticipant).filter(
            CallParticipant.session_id == session_id,
            CallParticipant.user_id == user_id,
            CallParticipant.participant_role == "responder"
        ).with_for_update().first()

        if not participant:
            raise ValueError("You are not an invited participant for this call.")

        if participant.status != "RINGING":
            raise ValueError(f"Invalid participant status: {participant.status}")

        # ATOMIC WINNER SELECTION
        session.winning_contact_user_id = user_id
        session.status = "CONNECTING"
        session.connected_at = datetime.utcnow()

        participant.status = "ACCEPTED"
        participant.accepted_at = datetime.utcnow()

        # Mark other pending responders as ALREADY_CONNECTED
        db.query(CallParticipant).filter(
            CallParticipant.session_id == session_id,
            CallParticipant.user_id != user_id,
            CallParticipant.participant_role == "responder",
            CallParticipant.status == "RINGING"
        ).update({"status": "ALREADY_CONNECTED"}, synchronize_session=False)

        db.commit()
        db.refresh(session)

        # Notify other participants that the call was picked up
        other_participants = db.query(CallParticipant).filter(
            CallParticipant.session_id == session_id,
            CallParticipant.user_id != user_id,
            CallParticipant.participant_role == "responder"
        ).all()

        for p in other_participants:
            if p.user.fcm_token:
                NotificationService.send_fcm_notification(
                    token=p.user.fcm_token,
                    title="Call Answered",
                    body=f"Another contact has joined the emergency call for {session.victim.full_name}.",
                    data={
                        "type": "EMERGENCY_CALL_ALREADY_CONNECTED",
                        "session_id": str(session.id),
                        "accident_id": str(session.accident_id)
                    },
                    db=db
                )

        return session

    @staticmethod
    def reject_call(db: Session, session_id: int, user_id: int, reason: str = None):
        """
        Handles call rejection by a participant atomically.
        """
        # Lock participant and session
        participant = db.query(CallParticipant).filter(
            CallParticipant.session_id == session_id,
            CallParticipant.user_id == user_id,
            CallParticipant.participant_role == "responder"
        ).with_for_update().first()

        if not participant:
            db.commit()
            return

        if participant.status != "RINGING":
            db.commit()
            return

        participant.status = "REJECTED"
        participant.rejection_reason = reason
        
        # Check if all participants have rejected
        remaining_ringing = db.query(CallParticipant).filter(
            CallParticipant.session_id == session_id,
            CallParticipant.status == "RINGING",
            CallParticipant.participant_role == "responder",
            CallParticipant.user_id != user_id
        ).count()

        if remaining_ringing == 0:
            session = db.query(EmergencyCallSession).filter(
                EmergencyCallSession.id == session_id
            ).with_for_update().first()
            
            if session and session.winning_contact_user_id is None:
                session.status = "FAILED"
                session.failure_reason = "All emergency contacts rejected the invitation."
        
        db.commit()

    @staticmethod
    def cancel_call_session(db: Session, accident_id: int, reason: str = "Accident alert cancelled"):
        """
        Cancels an active or pending call session.
        """
        session = db.query(EmergencyCallSession).filter(
            EmergencyCallSession.accident_id == accident_id
        ).first()

        if not session or session.status in ["ENDED", "CANCELLED"]:
            return

        session.status = "CANCELLED"
        session.cancellation_reason = reason
        session.ended_at = datetime.utcnow()
        db.commit()

        # Notify participants
        participants = db.query(CallParticipant).filter(
            CallParticipant.session_id == session.id
        ).all()

        for p in participants:
            if p.user.fcm_token:
                NotificationService.send_fcm_notification(
                    token=p.user.fcm_token,
                    title="Emergency Call Cancelled",
                    body=f"The emergency call for {session.victim.full_name} has been cancelled.",
                    data={
                        "type": "EMERGENCY_CALL_CANCELLED",
                        "session_id": str(session.id),
                        "accident_id": str(accident_id)
                    },
                    db=db
                )

    @staticmethod
    def cleanup_stale_sessions(db: Session):
        """
        Cleans up call sessions that have been in a non-terminal state for too long.
        """
        now = datetime.utcnow()
        
        # 1. Cleanup RINGING sessions that have timed out (e.g. 60 seconds)
        ringing_limit = now - timedelta(seconds=60)
        expired_sessions = db.query(EmergencyCallSession).filter(
            EmergencyCallSession.status == "RINGING",
            EmergencyCallSession.invitation_started_at < ringing_limit
        ).all()
        
        for session in expired_sessions:
            session.status = "EXPIRED"
            session.failure_reason = "No responders accepted the call in time."
            db.query(CallParticipant).filter(
                CallParticipant.session_id == session.id,
                CallParticipant.status == "RINGING"
            ).update({"status": "MISSED"}, synchronize_session=False)

        # 2. Cleanup CONNECTING/CONNECTED sessions that have been active for too long (e.g., 2 hours)
        abandoned_limit = now - timedelta(hours=2)
        abandoned_sessions = db.query(EmergencyCallSession).filter(
            EmergencyCallSession.status.in_(["CONNECTING", "CONNECTED"]),
            or_(
                EmergencyCallSession.connected_at < abandoned_limit,
                and_(EmergencyCallSession.connected_at.is_(None), EmergencyCallSession.created_at < abandoned_limit)
            )
        ).all()
        
        for session in abandoned_sessions:
            session.status = "ENDED"
            session.ended_at = now
            session.failure_reason = "Session automatically ended due to duration limit."

        if expired_sessions or abandoned_sessions:
            db.commit()
            logger.info(f"Cleaned up {len(expired_sessions)} expired and {len(abandoned_sessions)} abandoned call sessions.")
        
        return len(expired_sessions) + len(abandoned_sessions)
