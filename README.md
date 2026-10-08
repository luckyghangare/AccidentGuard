# AccidentGuard
### Accident Avoidance System with Crash Detection and GPS Notification

**AccidentGuard** is an end-to-end, full-stack vehicle safety and crash-response IoT platform. It connects an **Android Mobile Application (Kotlin Jetpack Compose)**, a high-performance **FastAPI REST API Server**, a **MySQL Database**, and an **ESP32 Hardware Microcontroller** (Ultrasonic + Accelerometer/Gyro + GPS) to provide real-time distance proximity warning, crash impact detection, live vehicle mapping, 10-second false alarm cancellation countdown, and automated emergency notification dispatch.

---

## 🏛️ System Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                          ACCIDENTGUARD SYSTEM                          │
└────────────────────────────────────────────────────────────────────────┘
                                   │
         ┌─────────────────────────┴─────────────────────────┐
         ▼                                                   ▼
┌─────────────────────────────────┐         ┌─────────────────────────────────┐
│       Android Kotlin App        │         │   ESP32 Vehicle Hardware /      │
│     (Jetpack Compose + MVVM)    │         │      Telemetry Simulator        │
│                                 │         │                                 │
│  • Dashboard & Telemetry        │         │  • Ultrasonic Sensor (HC-SR04)  │
│  • Proximity Alerts             │         │  • Accelerometer (MPU6050/ADXL) │
│  • Maps Compose Live GPS        │         │  • GPS Module (NEO-6M)          │
│  • 10s Crash Countdown Alert    │         │  • Buzzer & Tri-Color LEDs      │
│  • Event History & Contacts     │         │  • HTTP Client Telemetry Stream │
└────────────────┬────────────────┘         └────────────────┬────────────────┘
                 │                                           │
                 │              HTTP REST APIs               │
                 └───────────────────┬───────────────────────┘
                                     ▼
                     ┌───────────────────────────────┐
                     │    FastAPI Python Backend     │
                     │       (Uvicorn Engine)        │
                     │                               │
                     │  • JWT Authentication         │
                     │  • Vehicle State Monitoring   │
                     │  • Crash Dispatcher           │
                     │  • Proximity Classifier       │
                     └───────────────┬───────────────┘
                                     ▼
                     ┌───────────────────────────────┐
                     │        MySQL Database         │
                     │          (SQLAlchemy)         │
                     │                               │
                     │  • users & vehicles           │
                     │  • sensor_readings            │
                     │  • gps_locations              │
                     │  • accidents & notifications  │
                     │  • emergency_contacts         │
                     └───────────────────────────────┘
```

---

## 📱 Android Application (Kotlin + Jetpack Compose)

### Features & UI Screens
1. **Splash & Welcome Screen**: Branded animated entry screen.
2. **Authentication Screen**: Secure login and register with JWT token persistence.
3. **Telemetry Dashboard**:
   - Live proximity status card (`SAFE`, `WARNING`, `DANGER`, `CRITICAL`).
   - Today's driving overview: Total Trips (3), Distance (42.8 km), Top Speed (65 km/h), Safety Score (98%).
   - Interactive Quick-Action buttons (Vehicle Monitoring, Live Map, Accident History, Emergency Contacts).
4. **Vehicle Monitoring Screen**:
   - Distance to nearest obstacle gauge with reactive color badge.
   - Sensor diagnostic parameters: Acceleration (G-force), Vibration level, Tilt angle.
5. **Live Location Screen**:
   - Interactive Google Map with live vehicle marker.
   - Bottom sheet displaying current street address, GPS coordinates, vehicle speed, and live tracking status.
6. **Crash Alert & Emergency Countdown Screen**:
   - Emergency banner triggered upon high-G impact.
   - Visual 10-second countdown timer.
   - **"I Am Safe - Cancel Alert"** button to abort false alarms before dispatch.
7. **Accident History Screen**:
   - Detailed list of historical safety events with status flags (`Emergency Alert Sent`, `Cancelled`, `False Alarm`).
   - Modal dialog displaying date, time, impact force in Gs, and GPS coordinate snapshot.
8. **Emergency Contacts Management Screen**:
   - Primary & Secondary contact cards with relationship categories (Family, Friend, Doctor).
   - Add new contact modal and delete actions.
9. **Profile & Settings Screen**:
   - Driver profile header, account details, vehicle registration number, notification toggles, and secure logout.

---

## 🚀 Quick Start Guide

### 1. Database & Backend Setup
1. Ensure MySQL is running on port `3306` (e.g., via XAMPP).
2. Open terminal in `AccidentGuard/backend`:
   ```bash
   pip install -r requirements.txt
   uvicorn app.main:app --reload --port 8000
   ```
3. Interactive API documentation is available at:
   - **Swagger UI**: [http://localhost:8000/docs](http://localhost:8000/docs)
   - **ReDoc**: [http://localhost:8000/redoc](http://localhost:8000/redoc)

### 2. Demo User Credentials
- **Email**: `demo@accidentguard.com`
- **Password**: `Demo@123`

---

### 3. ESP32 Hardware Simulator
Test real-time sensor streams and crash triggers without physical hardware:
```bash
python firmware/esp32_simulator.py --mode interactive
```
*Options:* `--mode safe`, `--mode warning`, `--mode crash`, `--mode interactive`.

---

### 4. ESP32 Physical Microcontroller Firmware
1. Open `firmware/esp32_accident_guard/esp32_accident_guard.ino` in Arduino IDE.
2. Install required libraries via Library Manager:
   - `ArduinoJson` (v6.x)
   - `TinyGPSPlus`
3. Update `WIFI_SSID`, `WIFI_PASS`, and your computer's local IP address (`SERVER_BASE_URL`).
4. Select board **ESP32 Dev Module** and upload.

---

### 5. Android Studio / APK Build
1. Open the `AccidentGuard/frontend` folder in **Android Studio**.
2. Sync Gradle files.
3. Replace your Google Maps API key in `AndroidManifest.xml` (if needed for live tiles).
4. Run on an Android Emulator or physical phone.
5. To build APK from terminal:
   ```cmd
   build_apk.bat
   ```

---

## 🧪 Integration Testing
Run the complete 8-step full-system integration test suite:
```bash
cd backend
python tests/test_full_system.py
```
*Tests all endpoints: Auth -> Vehicle listing -> Distance thresholds -> Live GPS -> Crash report -> Alert cancellation -> Contacts CRUD -> History listing.*
