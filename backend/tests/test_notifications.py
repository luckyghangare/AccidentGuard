from unittest.mock import patch, MagicMock
from fastapi.testclient import TestClient
from app.main import app
from app.services.notification_service import NotificationService
from app.database import SessionLocal
from app.models.user import User
from app.models.vehicle import Vehicle
from firebase_admin import messaging
import pytest

client = TestClient(app)

def test_send_fcm_notification_success():
    with patch("firebase_admin.messaging.send") as mock_send:
        mock_send.return_value = "projects/my-project/messages/123"

        result = NotificationService.send_fcm_notification(
            token="test-token",
            title="Test Title",
            body="Test Body"
        )

        assert result is True
        mock_send.assert_called_once()
        # Verify message construction
        args, kwargs = mock_send.call_args
        message = args[0]
        assert message.token == "test-token"
        assert message.notification.title == "Test Title"
        assert message.notification.body == "Test Body"
        assert message.android.priority == "high"

def test_send_fcm_notification_unregistered():
    db = SessionLocal()
    # Ensure we have a test user with this token
    test_user = db.query(User).filter(User.email == "demo@accidentguard.com").first()
    if test_user:
        test_user.fcm_token = "stale-token"
        db.commit()

    with patch("firebase_admin.messaging.send") as mock_send:
        mock_send.side_effect = messaging.UnregisteredError("Token unregistered")

        result = NotificationService.send_fcm_notification(
            token="stale-token",
            title="Test Title",
            body="Test Body",
            db=db
        )

        assert result is False
        # The token should be cleared in the DB
        db.refresh(test_user)
        assert test_user.fcm_token is None

def test_accident_report_triggers_notification():
    # 1. Login to get token
    login_payload = {
        "email": "demo@accidentguard.com",
        "password": "Demo@123"
    }
    res_login = client.post("/api/auth/login", json=login_payload)
    token = res_login.json()["data"]["access_token"]
    headers = {"Authorization": f"Bearer {token}"}

    # 2. Update FCM token for the user
    client.post("/api/auth/fcm-token", json={"fcm_token": "valid-device-token"}, headers=headers)

    # NEW: Get a valid vehicle ID for this user
    db = SessionLocal()
    user = db.query(User).filter(User.email == "demo@accidentguard.com").first()
    vehicle = db.query(Vehicle).filter(Vehicle.user_id == user.id).first()
    if not vehicle:
        vehicle = Vehicle(user_id=user.id, name="Test Car", vehicle_number="TEST-123")
        db.add(vehicle)
        db.commit()
        db.refresh(vehicle)
    vehicle_id = vehicle.id
    db.close()

    # 3. Report accident and check if notification was sent
    with patch("firebase_admin.messaging.send") as mock_send:
        mock_send.return_value = "msg-id"

        accident_payload = {
            "vehicle_id": vehicle_id,
            "latitude": 12.34,
            "longitude": 56.78,
            "impact_g": 9.5
        }

        response = client.post("/api/accidents/report", json=accident_payload, headers=headers)
        assert response.status_code == 201

        # Verify notification was triggered
        mock_send.assert_called_once()
        args, kwargs = mock_send.call_args
        message = args[0]
        assert message.token == "valid-device-token"
        assert "AccidentGuard Emergency Alert" in message.notification.title
        assert "9.5" not in message.notification.body # Body has location, data has accident_id
        assert message.data["type"] == "ACCIDENT"

if __name__ == "__main__":
    # If running manually
    try:
        test_send_fcm_notification_success()
        print("test_send_fcm_notification_success: PASS")
        test_send_fcm_notification_unregistered()
        print("test_send_fcm_notification_unregistered: PASS")
        test_accident_report_triggers_notification()
        print("test_accident_report_triggers_notification: PASS")
        print("\nAll notification tests passed!")
    except Exception as e:
        print(f"Test failed: {e}")
        import traceback
        traceback.print_exc()
