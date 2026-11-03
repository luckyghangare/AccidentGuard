import pytest
from sqlalchemy import create_mock_engine
from sqlalchemy.orm import Session
from unittest.mock import MagicMock, patch

from app.models.accident import Accident
from app.models.call import EmergencyCallSession, CallParticipant
from app.models.user import User
from app.models.vehicle import Vehicle
from app.models.contact import EmergencyContact
from app.services.call_orchestration_service import CallOrchestrationService

@pytest.fixture
def mock_db():
    return MagicMock(spec=Session)

@patch("app.services.notification_service.NotificationService.send_fcm_notification")
def test_initiate_emergency_call_success(mock_send_fcm, mock_db):
    # Setup mock accident and victim
    victim = User(id=1, full_name="Victim", fcm_token="victim_token")
    vehicle = Vehicle(id=1, user_id=1, owner=victim)
    accident = Accident(id=10, vehicle_id=1, status="emergency_sent", vehicle=vehicle)
    
    # Setup mock contact
    contact_user = User(id=2, full_name="Responder", fcm_token="responder_token")
    contact = EmergencyContact(user_id=1, contact_user_id=2, status="ACCEPTED")
    
    # Mock DB queries
    def mock_query(model):
        q = MagicMock()
        if model == Accident:
            q.filter.return_value.first.return_value = accident
        elif model == EmergencyCallSession:
            q.filter.return_value.first.return_value = None
        elif model == EmergencyContact:
            q.filter.return_value.all.return_value = [contact]
        elif model == User:
            q.filter.return_value.first.return_value = contact_user
        return q
    
    mock_db.query.side_effect = mock_query
    mock_send_fcm.return_value = True

    # Run
    session = CallOrchestrationService.initiate_emergency_call(mock_db, 10)

    # Verify
    assert session is not None
    assert session.accident_id == 10
    assert session.status == "RINGING"
    assert mock_db.add.call_count >= 3 # Session, victim participant, responder participant
    assert mock_send_fcm.called

@patch("app.services.notification_service.NotificationService.send_fcm_notification")
def test_accept_call_atomic_winner(mock_send_fcm, mock_db):
    # Setup
    victim = User(id=1, full_name="Victim")
    session = EmergencyCallSession(id=100, accident_id=10, victim_user_id=1, status="RINGING", victim=victim)
    participant = CallParticipant(id=1, session_id=100, user_id=2, participant_role="responder", status="RINGING")
    
    # Mock locking and queries
    mock_db.query.return_value.filter.return_value.with_for_update.return_value.first.side_effect = [session, participant]
    
    # Run
    result = CallOrchestrationService.accept_call(mock_db, 100, 2)

    # Verify
    assert result.winning_contact_user_id == 2
    assert result.status == "CONNECTING"
    assert participant.status == "ACCEPTED"
    assert mock_db.commit.called

def test_accept_call_already_taken(mock_db):
    # Setup - session already has a winner
    session = EmergencyCallSession(id=100, winning_contact_user_id=3, status="CONNECTING")
    participant = CallParticipant(id=1, session_id=100, user_id=2, status="RINGING")
    
    mock_db.query.return_value.filter.return_value.with_for_update.return_value.first.side_effect = [session, participant]
    
    # Run & Verify
    with pytest.raises(ValueError, match="Another contact has already accepted"):
        CallOrchestrationService.accept_call(mock_db, 100, 2)
    
    assert participant.status == "ALREADY_CONNECTED"
    assert mock_db.commit.called
