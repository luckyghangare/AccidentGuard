from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def test_api_endpoints():
    print("Testing Backend API Endpoints...")

    # 1. Test Root
    res_root = client.get("/")
    assert res_root.status_code == 200
    print("[OK] Root Endpoint (/): PASS")

    # 2. Test Login
    login_payload = {
        "email": "demo@accidentguard.com",
        "password": "Demo@123"
    }
    res_login = client.post("/api/auth/login", json=login_payload)
    assert res_login.status_code == 200
    login_data = res_login.json()
    assert login_data["success"] is True
    token = login_data["data"]["access_token"]
    headers = {"Authorization": f"Bearer {token}"}
    print(f"[OK] Auth Login (/api/auth/login): PASS - Token: {token[:15]}...")

    # 3. Test Me
    res_me = client.get("/api/auth/me", headers=headers)
    assert res_me.status_code == 200
    print(f"[OK] Auth Me (/api/auth/me): PASS - User: {res_me.json()['data']['full_name']}")

    # 4. Test Vehicles
    res_vehicles = client.get("/api/vehicles", headers=headers)
    assert res_vehicles.status_code == 200
    print(f"[OK] Vehicles List (/api/vehicles): PASS - Found {len(res_vehicles.json()['data'])} vehicles")

    # 5. Test GPS Location
    res_gps = client.get("/api/vehicles/1/location", headers=headers)
    assert res_gps.status_code == 200
    print(f"[OK] GPS Location (/api/vehicles/1/location): PASS - Lat/Lng: {res_gps.json()['data']['latitude']}, {res_gps.json()['data']['longitude']}")

    # 6. Test Sensors Latest
    res_sensor = client.get("/api/vehicles/1/sensors/latest", headers=headers)
    assert res_sensor.status_code == 200
    print(f"[OK] Sensor Latest (/api/vehicles/1/sensors/latest): PASS - Obstacle: {res_sensor.json()['data']['obstacle_distance']} cm")

    # 7. Test Accidents List
    res_accidents = client.get("/api/accidents", headers=headers)
    assert res_accidents.status_code == 200
    print(f"[OK] Accidents List (/api/accidents): PASS - Found {len(res_accidents.json()['data'])} accident records")

    # 8. Test Emergency Contacts List
    res_contacts = client.get("/api/contacts", headers=headers)
    assert res_contacts.status_code == 200
    print(f"[OK] Emergency Contacts List (/api/contacts): PASS - Found {len(res_contacts.json()['data'])} contacts")

    print("\nALL API ENDPOINT TESTS PASSED SUCCESSFULLY!")

if __name__ == "__main__":
    test_api_endpoints()
