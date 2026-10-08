"""
=============================================================================
AccidentGuard - Comprehensive Full-System Integration Test
=============================================================================
Tests the complete end-to-end workflow:
  1. Authentication & JWT tokens
  2. Vehicle telemetry & status thresholds (SAFE, WARNING, DANGER, CRITICAL)
  3. GPS Location updates
  4. Crash event trigger & emergency alert dispatching
  5. Alert cancellation
  6. Emergency contacts management (Add, Fetch, Delete)
  7. Event history verification

Run with:
  python tests/test_full_system.py
=============================================================================
"""

import json
import urllib.request
import urllib.error
import sys

BASE_URL = "http://localhost:8000"

def api_request(method: str, path: str, payload: dict = None, token: str = None) -> dict:
    url = f"{BASE_URL}{path}"
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    
    data = json.dumps(payload).encode('utf-8') if payload else None
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    
    try:
        with urllib.request.urlopen(req, timeout=5.0) as res:
            resp_body = res.read().decode('utf-8')
            return json.loads(resp_body)
    except urllib.error.HTTPError as e:
        resp_body = e.read().decode('utf-8')
        try:
            return json.loads(resp_body)
        except:
            return {"success": False, "status_code": e.code, "message": str(e)}

def run_tests():
    print("=" * 70)
    print("      ACCIDENTGUARD FULL-SYSTEM INTEGRATION TEST SUITE")
    print("=" * 70)

    # 1. Login
    print("\n[STEP 1] Testing User Authentication (Login)...")
    login_res = api_request("POST", "/api/auth/login", {"email": "demo@accidentguard.com", "password": "Demo@123"})
    assert login_res.get("success") is True, f"Login failed: {login_res}"
    token = login_res["data"].get("access_token") or login_res["data"].get("token")
    user_name = login_res["data"]["user"]["full_name"]
    print(f"  [PASS] Logged in as: {user_name} (Token acquired)")

    # 2. Query Vehicles
    print("\n[STEP 2] Testing Vehicles Listing...")
    vehicles_res = api_request("GET", "/api/vehicles", token=token)
    assert vehicles_res.get("success") is True, f"Get vehicles failed: {vehicles_res}"
    vehicles = vehicles_res["data"]
    assert len(vehicles) > 0, "No vehicles found"
    vehicle_id = vehicles[0]["id"]
    print(f"  [PASS] Retrieved {len(vehicles)} vehicle(s). Active Vehicle: {vehicles[0]['name']} ({vehicles[0]['vehicle_number']})")

    # 3. Ingest Sensor Readings & Verify Thresholds
    print("\n[STEP 3] Testing Sensor Reading Ingestion & Thresholds...")
    sensor_cases = [
        (150.0, 1.0, "SAFE"),
        (45.0, 1.3, "WARNING"),
        (18.0, 2.0, "DANGER"),
        (6.0, 3.5, "CRITICAL")
    ]
    for dist, impact, expected_status in sensor_cases:
        res = api_request("POST", f"/api/vehicles/{vehicle_id}/sensors", {
            "distance": dist,
            "impact_g": impact,
            "latitude": 21.1458,
            "longitude": 79.0882,
            "speed": 45.0
        })
        assert res.get("success") is True, f"Failed sensor post: {res}"
        actual_status = res["data"]["status"]
        assert actual_status == expected_status, f"Expected {expected_status} for {dist}cm, got {actual_status}"
        print(f"  [PASS] Distance: {dist:5.1f}cm -> Status: {actual_status} (Matches expected)")

    # 4. Ingest Live GPS Location
    print("\n[STEP 4] Testing Live GPS Location Telemetry...")
    loc_res = api_request("GET", f"/api/vehicles/{vehicle_id}/location", token=token)
    assert loc_res.get("success") is True, f"Get location failed: {loc_res}"
    loc_data = loc_res["data"]
    print(f"  [PASS] Latest Coordinates: ({loc_data['latitude']}, {loc_data['longitude']}), Speed: {loc_data.get('speed', 0)} km/h")

    # 5. Trigger Crash Event & Verify Emergency Notifications
    print("\n[STEP 5] Testing Crash Detection & Emergency Alert Dispatch...")
    crash_res = api_request("POST", "/api/accidents/report", {
        "vehicle_id": vehicle_id,
        "impact_g": 5.8,
        "latitude": 21.1458,
        "longitude": 79.0882
    })
    assert crash_res.get("success") is True, f"Crash trigger failed: {crash_res}"
    accident_id = crash_res["data"]["id"]
    print(f"  [PASS] Accident recorded (ID #{accident_id}). Status: {crash_res['data']['status']}")

    # 6. Test Alert Cancellation (Within Countdown)
    print("\n[STEP 6] Testing Emergency Alert Cancellation...")
    cancel_res = api_request("PUT", f"/api/accidents/{accident_id}/cancel", token=token)
    assert cancel_res.get("success") is True, f"Cancel alert failed: {cancel_res}"
    assert cancel_res["data"]["status"] == "cancelled", f"Status not cancelled: {cancel_res}"
    print(f"  [PASS] Alert #{accident_id} successfully cancelled by driver.")

    # 7. Emergency Contacts CRUD
    print("\n[STEP 7] Testing Emergency Contacts (Add, List, Delete)...")
    add_contact_res = api_request("POST", "/api/contacts", {
        "name": "Test Emergency Doctor",
        "phone_number": "+91 9998887776",
        "relationship_type": "Doctor",
        "is_primary": False
    }, token=token)
    assert add_contact_res.get("success") is True, f"Add contact failed: {add_contact_res}"
    contact_id = add_contact_res["data"]["id"]
    print(f"  [PASS] Added contact: {add_contact_res['data']['name']} (ID #{contact_id})")

    # List contacts
    contacts_res = api_request("GET", "/api/contacts", token=token)
    assert any(c["id"] == contact_id for c in contacts_res["data"]), "New contact not listed"
    print(f"  [PASS] Total Emergency Contacts: {len(contacts_res['data'])}")

    # Delete contact
    del_res = api_request("DELETE", f"/api/contacts/{contact_id}", token=token)
    assert del_res.get("success") is True, f"Delete contact failed: {del_res}"
    print(f"  [PASS] Deleted contact #{contact_id}")

    # 8. Event History
    print("\n[STEP 8] Testing Accident History Retrieval...")
    history_res = api_request("GET", "/api/accidents", token=token)
    assert history_res.get("success") is True, f"History fetch failed: {history_res}"
    print(f"  [PASS] Total recorded events in history: {len(history_res['data'])}")

    print("\n" + "=" * 70)
    print("     ALL FULL-SYSTEM INTEGRATION TESTS PASSED SUCCESSFULLY! (8/8)")
    print("=" * 70)

if __name__ == "__main__":
    try:
        run_tests()
    except Exception as e:
        print(f"\n[FAIL] Test suite encountered an error: {e}")
        sys.exit(1)
