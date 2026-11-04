
import sys
import os
from sqlalchemy import create_all
from sqlalchemy.orm import Session
from datetime import datetime

# Mocking app context
sys.path.append(os.path.join(os.getcwd(), 'backend'))

from app.database import SessionLocal, engine, Base
from app.models.call import EmergencyCallSession, CallParticipant
from app.models.user import User
from app.services.call_orchestration_service import CallOrchestrationService
from app.schemas.call import EmergencyCallSessionResponse

def verify_serialization():
    db = SessionLocal()
    try:
        # 1. Create a mock session
        session = EmergencyCallSession(
            accident_id=999,
            victim_user_id=1,
            livekit_room_name="test_room_123",
            status="RINGING",
            created_at=datetime.utcnow()
        )
        db.add(session)
        db.flush()
        
        # 2. Add participants
        p1 = CallParticipant(session_id=session.id, user_id=1, participant_role="victim", status="PENDING")
        p2 = CallParticipant(session_id=session.id, user_id=2, participant_role="responder", status="RINGING")
        db.add(p1)
        db.add(p2)
        db.commit()
        db.refresh(session)
        
        print(f"Testing serialization for session {session.id}")
        
        # 3. Test Pydantic serialization
        # Manually load participants to avoid lazy loading issues in test
        from sqlalchemy.orm import joinedload
        session_loaded = db.query(EmergencyCallSession).options(
            joinedload(EmergencyCallSession.participants).joinedload(CallParticipant.user)
        ).filter(EmergencyCallSession.id == session.id).first()
        
        resp = EmergencyCallSessionResponse.from_orm(session_loaded)
        print("Serialization Successful!")
        print(f"Participants count: {len(resp.participants)}")
        for p in resp.participants:
            print(f" - {p.full_name} ({p.participant_role}): {p.status}")
            
    except Exception as e:
        print(f"Serialization Failed: {e}")
        import traceback
        traceback.print_exc()
    finally:
        db.close()

if __name__ == "__main__":
    verify_serialization()
