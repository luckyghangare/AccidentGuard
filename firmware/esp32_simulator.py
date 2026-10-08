"""
=============================================================================
AccidentGuard - ESP32 Hardware Telemetry Simulator
=============================================================================
Simulates the ESP32 vehicle hardware transmitting sensor & GPS telemetry
to the FastAPI backend in real-time.

Usage:
    python firmware/esp32_simulator.py [--mode safe|warning|crash|interactive]
=============================================================================
"""

import time
import math
import random
import argparse
import json
import urllib.request

SERVER_URL = "http://localhost:8000"
VEHICLE_ID = 1

# Base GPS Coordinate (Nagpur, India)
BASE_LAT = 21.1458
BASE_LNG = 79.0882

def send_sensor_reading(distance: float, impact_g: float, speed: float, lat: float, lng: float):
    payload = {
        "distance": round(distance, 1),
        "impact_g": round(impact_g, 2),
        "vibration_level": round(random.uniform(0.1, 0.4), 2),
        "tilt_angle": round(random.uniform(0.0, 3.0), 1),
        "latitude": round(lat, 6),
        "longitude": round(lng, 6),
        "speed": round(speed, 1)
    }
    try:
        data_bytes = json.dumps(payload).encode('utf-8')
        req = urllib.request.Request(
            f"{SERVER_URL}/api/vehicles/{VEHICLE_ID}/sensors",
            data=data_bytes,
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req, timeout=3.0) as res:
            status_tag = "[SAFE]" if distance > 80 else ("[WARN]" if distance > 30 else "[DANGER]")
            print(f"{status_tag} [ESP32 Telemetry] Dist: {distance:5.1f}cm | Impact: {impact_g:4.2f}g | Speed: {speed:4.1f}km/h | Loc: ({lat:.4f}, {lng:.4f}) | HTTP {res.status}")
    except Exception as e:
        print(f"[ERROR] [ESP32 Telemetry] Error sending to backend: {e}")

def trigger_crash(impact_g: float, lat: float, lng: float):
    payload = {
        "vehicle_id": VEHICLE_ID,
        "impact_g": round(impact_g, 2),
        "latitude": round(lat, 6),
        "longitude": round(lng, 6),
        "status": "pending_countdown"
    }
    try:
        data_bytes = json.dumps(payload).encode('utf-8')
        req = urllib.request.Request(
            f"{SERVER_URL}/api/accidents/report",
            data=data_bytes,
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req, timeout=3.0) as res:
            print(f"\n[ALERT] [CRASH TRIGGERED] Impact: {impact_g:.2f}g at ({lat:.4f}, {lng:.4f}) -> Response: {res.status}\n")
    except Exception as e:
        print(f"[ERROR] [CRASH TRIGGER] Error: {e}")

def run_simulation(mode: str = "interactive"):
    print("=" * 70)
    print("   ACCIDENTGUARD - ESP32 TELEMETRY SIMULATOR")
    print(f"   Connecting to Backend: {SERVER_URL}")
    print(f"   Simulating Vehicle ID: {VEHICLE_ID}")
    print(f"   Simulation Mode:       {mode.upper()}")
    print("=" * 70)

    step = 0

    try:
        while True:
            step += 1
            # Simulate GPS movement in a circular path
            angle = (step * 5) % 360
            rad = math.radians(angle)
            current_lat = BASE_LAT + 0.005 * math.sin(rad)
            current_lng = BASE_LNG + 0.005 * math.cos(rad)

            if mode == "safe":
                distance = random.uniform(120.0, 250.0)
                impact_g = random.uniform(0.95, 1.15)
                speed = random.uniform(40.0, 60.0)

            elif mode == "warning":
                distance = random.uniform(35.0, 75.0)
                impact_g = random.uniform(1.2, 1.8)
                speed = random.uniform(25.0, 40.0)

            elif mode == "crash":
                distance = random.uniform(5.0, 15.0)
                impact_g = random.uniform(4.5, 7.5)
                speed = 0.0
                send_sensor_reading(distance, impact_g, speed, current_lat, current_lng)
                trigger_crash(impact_g, current_lat, current_lng)
                time.sleep(3)
                continue

            else:  # interactive / cycle
                cycle_step = step % 30
                if cycle_step < 18:
                    # Safe driving
                    distance = random.uniform(110.0, 220.0)
                    impact_g = random.uniform(0.95, 1.10)
                    speed = random.uniform(45.0, 65.0)
                elif cycle_step < 25:
                    # Approaching obstacle (Warning)
                    distance = random.uniform(35.0, 65.0)
                    impact_g = random.uniform(1.2, 1.7)
                    speed = random.uniform(20.0, 35.0)
                elif cycle_step == 26:
                    # Danger close
                    distance = random.uniform(10.0, 25.0)
                    impact_g = random.uniform(2.5, 3.8)
                    speed = 15.0
                elif cycle_step == 27:
                    # Impact / Crash simulation
                    distance = 5.0
                    impact_g = 5.4
                    speed = 0.0
                    send_sensor_reading(distance, impact_g, speed, current_lat, current_lng)
                    trigger_crash(impact_g, current_lat, current_lng)
                    time.sleep(2)
                    continue
                else:
                    # Post impact stopped
                    distance = 8.0
                    impact_g = 1.0
                    speed = 0.0

            send_sensor_reading(distance, impact_g, speed, current_lat, current_lng)
            time.sleep(1.5)

    except KeyboardInterrupt:
        print("\n[Simulator] Stopped by user.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="AccidentGuard ESP32 Telemetry Simulator")
    parser.add_argument("--mode", choices=["safe", "warning", "crash", "interactive"], default="interactive",
                        help="Simulation mode: safe, warning, crash, or interactive (default)")
    args = parser.parse_args()
    run_simulation(args.mode)
