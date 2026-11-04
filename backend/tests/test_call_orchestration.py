
import pytest
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


# ---------------------------------------------------------
# TEST 1: INITIATE EMERGENCY CALL SUCCESS
# ---------------------------------------------------------

@patch(
    "app.services.notification_service.NotificationService.send_fcm_notification"
)
def test_initiate_emergency_call_success(mock_send_fcm, mock_db):

    victim = User(
        id=1,
        full_name="Victim",
        fcm_token="victim_token",
    )

    vehicle = Vehicle(
        id=1,
        user_id=1,
        owner=victim,
    )

    accident = Accident(
        id=10,
        vehicle_id=1,
        status="emergency_sent",
        vehicle=vehicle,
    )

    contact_user = User(
        id=2,
        full_name="Responder",
        fcm_token="responder_token",
    )

    contact = EmergencyContact(
        user_id=1,
        contact_user_id=2,
        status="ACCEPTED",
    )

    def mock_query(model):
        query = MagicMock()

        if model == Accident:
            query.filter.return_value.first.return_value = accident

        elif model == EmergencyCallSession:
            query.filter.return_value.first.return_value = None

        elif model == EmergencyContact:
            query.filter.return_value.all.return_value = [contact]

        elif model == User:
            query.filter.return_value.first.return_value = contact_user

        return query

    mock_db.query.side_effect = mock_query
    mock_send_fcm.return_value = True

    session = CallOrchestrationService.initiate_emergency_call(
        mock_db,
        10,
    )

    assert session is not None
    assert session.accident_id == 10
    assert session.status == "RINGING"
    assert mock_db.add.call_count >= 3
    assert mock_send_fcm.called


# ---------------------------------------------------------
# TEST 2: ACCEPT CALL - ATOMIC WINNER SELECTION
# ---------------------------------------------------------

@patch(
    "app.services.notification_service.NotificationService.send_fcm_notification"
)
def test_accept_call_atomic_winner(mock_send_fcm, mock_db):

    victim = User(
        id=1,
        full_name="Victim",
    )

    session = EmergencyCallSession(
        id=100,
        accident_id=10,
        victim_user_id=1,
        status="RINGING",
        victim=victim,
    )

    participant = CallParticipant(
        id=1,
        session_id=100,
        user_id=2,
        participant_role="responder",
        status="RINGING",
    )

    # Query 1: Retrieve and lock emergency session
    session_query = MagicMock()
    session_query.filter.return_value.with_for_update.return_value.first.return_value = (
        session
    )

    # Query 2: Retrieve and lock accepting participant
    participant_query = MagicMock()
    participant_query.filter.return_value.with_for_update.return_value.first.return_value = (
        participant
    )

    # Query 3: Update other pending responders
    update_query = MagicMock()
    update_query.filter.return_value.update.return_value = 1

    # Query 4: Retrieve other responders for notifications
    notification_query = MagicMock()
    notification_query.filter.return_value.all.return_value = []

    # Configure all four queries in execution order
    mock_db.query.side_effect = [
        session_query,
        participant_query,
        update_query,
        notification_query,
    ]

    result = CallOrchestrationService.accept_call(
        mock_db,
        100,
        2,
    )

    # Verify returned session
    assert result is session

    # Verify winner selection
    assert result.winning_contact_user_id == 2
    assert result.status == "CONNECTING"

    # Verify accepting participant
    assert participant.status == "ACCEPTED"

    # Verify other responders update
    update_query.filter.return_value.update.assert_called_once()

    # Verify notification query
    notification_query.filter.return_value.all.assert_called_once()

    # Verify database operations
    assert mock_db.commit.called
    assert mock_db.refresh.called


# ---------------------------------------------------------
# TEST 3: REJECT ACCEPTANCE WHEN ANOTHER CONTACT HAS WON
# ---------------------------------------------------------

def test_accept_call_already_taken(mock_db):

    session = EmergencyCallSession(
        id=100,
        winning_contact_user_id=3,
        status="CONNECTING",
    )

    participant = CallParticipant(
        id=1,
        session_id=100,
        user_id=2,
        status="RINGING",
    )

    # Query 1: Retrieve and lock session
    session_query = MagicMock()
    session_query.filter.return_value.with_for_update.return_value.first.return_value = (
        session
    )

    # Query 2: Find the other responder
    participant_query = MagicMock()
    participant_query.filter.return_value.with_for_update.return_value.first.return_value = participant

    mock_db.query.side_effect = [
        session_query,
        participant_query,
    ]

    with pytest.raises(
            ValueError,
            match="Another contact has already accepted",
    ):
        CallOrchestrationService.accept_call(
            mock_db,
            100,
            2,
        )

    assert participant.status == "ALREADY_CONNECTED"
    assert mock_db.commit.called
