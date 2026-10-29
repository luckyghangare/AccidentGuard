import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.database import SessionLocal
from app.models.user import User
from app.models.contact import EmergencyContact
from unittest.mock import patch
import random
import string

client = TestClient(app)

def random_string(length=10):
    return ''.join(random.choices(string.ascii_lowercase + string.digits, k=length))

def test_consent_flow():
    # 1. Register two users
    user1_email = f"user1_{random_string()}@test.com"
    user2_email = f"user2_{random_string()}@test.com"
    user2_phone = f"+91{random.randint(7000000000, 9999999999)}"

    res1 = client.post("/api/auth/register", json={
        "full_name": "User One",
        "email": user1_email,
        "phone_number": "+919999999999",
        "password": "Password@123"
    })
    assert res1.status_code == 201

    res2 = client.post("/api/auth/register", json={
        "full_name": "User Two",
        "email": user2_email,
        "phone_number": user2_phone,
        "password": "Password@123"
    })
    assert res2.status_code == 201

    # Login User 1
    login1 = client.post("/api/auth/login", json={"email": user1_email, "password": "Password@123"})
    token1 = login1.json()["data"]["access_token"]
    headers1 = {"Authorization": f"Bearer {token1}"}

    # Login User 2
    login2 = client.post("/api/auth/login", json={"email": user2_email, "password": "Password@123"})
    token2 = login2.json()["data"]["access_token"]
    headers2 = {"Authorization": f"Bearer {token2}"}
    client.post("/api/auth/fcm-token", json={"fcm_token": "token-user-2"}, headers=headers2)

    # 2. User 1 invites User 2
    invite_res = client.post("/api/contacts", json={
        "name": "Friend Two",
        "phone_number": user2_phone,
        "relationship_type": "Friend"
    }, headers=headers1)
    assert invite_res.status_code == 201
    invitation = invite_res.json()["data"]
    assert invitation["status"] == "PENDING"
    invitation_id = invitation["id"]

    # 3. User 2 views received invitations
    received_res = client.get("/api/contacts/invitations/received", headers=headers2)
    assert received_res.status_code == 200
    assert any(i["id"] == invitation_id for i in received_res.json()["data"])

    # 4. User 2 accepts invitation
    accept_res = client.post(f"/api/contacts/invitations/{invitation_id}/accept", headers=headers2)
    assert accept_res.status_code == 200
    assert accept_res.json()["data"]["status"] == "ACCEPTED"

    # 5. Verify accident notification includes User 2
    # Create a vehicle for User 1
    db = SessionLocal()
    u1 = db.query(User).filter(User.email == user1_email).first()
    from app.models.vehicle import Vehicle
    vehicle = Vehicle(user_id=u1.id, name="Test Car", vehicle_number="TS01AB1234")
    db.add(vehicle)
    db.commit()
    vehicle_id = vehicle.id

    with patch("firebase_admin.messaging.send") as mock_send:
        mock_send.return_value = "msg-id"

        accident_payload = {
            "vehicle_id": vehicle_id,
            "latitude": 10.0,
            "longitude": 20.0,
            "impact_g": 5.0
        }

        report_res = client.post("/api/accidents/report", json=accident_payload, headers=headers1)
        assert report_res.status_code == 201

        # Should be called at least twice (one for owner, one for User 2)
        # Actually in this test owner doesn't have FCM token set, so just User 2
        assert mock_send.call_count >= 1

        tokens_sent = [call.args[0].token for call in mock_send.call_args_list]
        assert "token-user-2" in tokens_sent

    # 5.1 Verify self-invite prevention
    res_self = client.post("/api/contacts", json={
        "name": "Me",
        "phone_number": "+919999999999",
        "relationship_type": "Myself"
    }, headers=headers1)
    assert res_self.status_code == 400
    assert "cannot invite yourself" in res_self.json()["error"]["message"]

    # 5.2 Verify duplicate prevention
    res_dup = client.post("/api/contacts", json={
        "name": "Friend Two Again",
        "phone_number": user2_phone,
        "relationship_type": "Friend"
    }, headers=headers1)
    assert res_dup.status_code == 400
    assert "already exists" in res_dup.json()["error"]["message"]

    # 5.3 Verify unauthorized accident reporting
    # Create another user and vehicle
    user3_email = f"user3_{random_string()}@test.com"
    client.post("/api/auth/register", json={
        "full_name": "User Three",
        "email": user3_email,
        "phone_number": "+918888888888",
        "password": "Password@123"
    })
    login3 = client.post("/api/auth/login", json={"email": user3_email, "password": "Password@123"})
    token3 = login3.json()["data"]["access_token"]
    headers3 = {"Authorization": f"Bearer {token3}"}

    res_unauth = client.post("/api/accidents/report", json={
        "vehicle_id": vehicle_id, # User 1's vehicle
        "latitude": 10.0,
        "longitude": 20.0,
        "impact_g": 5.0
    }, headers=headers3)
    assert res_unauth.status_code == 404
    assert "Vehicle not found" in res_unauth.json()["error"]["message"]

    # 6. Revoke consent (User 1 deletes contact)
    delete_res = client.delete(f"/api/contacts/{invitation_id}", headers=headers1)
    assert delete_res.status_code == 200

    # 7. Verify no notification sent after revocation
    with patch("firebase_admin.messaging.send") as mock_send:
        client.post("/api/accidents/report", json=accident_payload)
        tokens_sent = [call.args[0].token for call in mock_send.call_args_list]
        assert "token-user-2" not in tokens_sent

    print("\nEmergency consent flow test passed!")

if __name__ == "__main__":
    test_consent_flow()
