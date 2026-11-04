
import sys
import os
from unittest.mock import MagicMock
from datetime import datetime

# Setup path
sys.path.append(os.path.join(os.getcwd(), 'backend'))

from app.schemas.call import EmergencyCallSessionResponse, CallParticipantResponse

def test_mock_serialization():
    print("Testing Mock Serialization...")
    
    # Mock User
    mock_user = MagicMock()
    mock_user.id = 1
    mock_user.full_name = "Test User"
    
    # Mock Participant
    mock_p = MagicMock()
    mock_p.id = 10
    mock_p.user_id = 1
    mock_p.full_name = "Test User"
    mock_p.participant_role = "victim"
    mock_p.status = "PENDING"
    mock_p.invited_at = datetime.utcnow()
    mock_p.accepted_at = None
    mock_p.joined_at = None
    
    # Mock Session
    mock_session = MagicMock()
    mock_session.id = 100
    mock_session.accident_id = 999
    mock_session.victim_user_id = 1
    mock_session.livekit_room_name = "room_1"
    mock_session.status = "RINGING"
    mock_session.winning_contact_user_id = None
    mock_session.invitation_started_at = datetime.utcnow()
    mock_session.connected_at = None
    mock_session.ended_at = None
    mock_session.created_at = datetime.utcnow()
    mock_session.participants = [mock_p]
    
    try:
        # Test Pydantic v2 from_attributes
        resp = EmergencyCallSessionResponse.model_validate(mock_session, from_attributes=True)
        print("Success! Pydantic can read from mock objects.")
        print(f"Session ID: {resp.id}")
        print(f"Participant: {resp.participants[0].full_name}")
    except Exception as e:
        print(f"Failed: {e}")

if __name__ == "__main__":
    test_mock_serialization()
