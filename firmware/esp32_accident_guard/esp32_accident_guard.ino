/*
 * ============================================================================
 * AccidentGuard - ESP32 Hardware Firmware
 * Accident Avoidance System with Crash Detection and GPS Notification
 * ============================================================================
 * 
 * Target Hardware:
 *   - ESP32 Dev Module (NodeMCU-32S / ESP32-WROOM-32)
 *   - Ultrasonic Sensor: HC-SR04 (Distance Measurement)
 *   - Accelerometer / Gyro: MPU6050 / ADXL345 (Impact Force & Vibration)
 *   - GPS Module: NEO-6M / GT-U7 (Live Latitude & Longitude)
 *   - Buzzer: Active 5V Buzzer (Audible Warning)
 *   - LEDs: Green (Safe), Yellow (Warning), Red (Danger/Crash)
 *
 * REST API Backend:
 *   - FastAPI Backend running at: http://<SERVER_IP>:8000/
 *   - Telemetry Ingestion: POST /api/telemetry
 *   - Crash Trigger: POST /api/accidents/report
 * ============================================================================
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <Wire.h>
#include <TinyGPSPlus.h>
#include <HardwareSerial.h>

// ==================== CONFIGURATION ====================
// Wi-Fi Credentials
const char* WIFI_SSID = "YOUR_WIFI_SSID";
const char* WIFI_PASS = "YOUR_WIFI_PASSWORD";

// FastAPI Server URL (Replace with your computer's local IP on the same Wi-Fi, e.g. 192.168.1.100)
const char* SERVER_BASE_URL = "http://192.168.1.100:8000";
const int VEHICLE_ID = 1;

// Thresholds
const float CRASH_G_THRESHOLD = 4.0;      // Impact force in Gs that triggers accident
const float DANGER_DISTANCE_CM = 30.0;    // Danger distance in cm
const float WARNING_DISTANCE_CM = 80.0;   // Warning distance in cm

// ==================== PIN DEFINITIONS ====================
// Ultrasonic HC-SR04
#define TRIG_PIN 5
#define ECHO_PIN 18

// Buzzer and LEDs
#define BUZZER_PIN 25
#define LED_GREEN 26
#define LED_YELLOW 27
#define LED_RED 14

// GPS Serial Pins (Serial2 on ESP32)
#define GPS_RX_PIN 16
#define GPS_TX_PIN 17

// ==================== OBJECTS & GLOBALS ====================
TinyGPSPlus gps;
HardwareSerial gpsSerial(2);

unsigned long lastTelemetryTime = 0;
const unsigned long TELEMETRY_INTERVAL_MS = 2000; // Send telemetry every 2 seconds

bool alertSent = false;

// MPU6050 I2C Address
const int MPU_ADDR = 0x68;

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n[AccidentGuard] System Initializing...");

  // Pin modes
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(LED_GREEN, OUTPUT);
  pinMode(LED_YELLOW, OUTPUT);
  pinMode(LED_RED, OUTPUT);

  digitalWrite(LED_GREEN, HIGH);

  // Initialize I2C for MPU6050
  Wire.begin(21, 22);
  Wire.beginTransmission(MPU_ADDR);
  Wire.write(0x6B); // PWR_MGMT_1 register
  Wire.write(0);    // Wake up MPU-6050
  Wire.endTransmission(true);
  Serial.println("[AccidentGuard] MPU6050 Initialized.");

  // Initialize GPS Serial
  gpsSerial.begin(9600, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);
  Serial.println("[AccidentGuard] GPS Module Initialized.");

  // Connect to Wi-Fi
  connectToWiFi();
}

void loop() {
  // 1. Process GPS Data
  while (gpsSerial.available() > 0) {
    gps.encode(gpsSerial.read());
  }

  // 2. Read Ultrasonic Distance
  float distanceCm = readUltrasonicDistance();

  // 3. Read Accelerometer Data (Impact / G-Force)
  float impactG = readMPU6050Impact();

  // 4. Update Indicators & Safety State
  updateSafetyIndicators(distanceCm, impactG);

  // 5. Crash Detection Check
  if (impactG >= CRASH_G_THRESHOLD && !alertSent) {
    Serial.printf("[ALERT] CRASH DETECTED! Impact Force: %.2f g\n", impactG);
    triggerCrashAlert(impactG, distanceCm);
    alertSent = true;
  } else if (impactG < 1.5) {
    alertSent = false;
  }

  // 6. Periodic Telemetry Upload to FastAPI
  if (millis() - lastTelemetryTime >= TELEMETRY_INTERVAL_MS) {
    lastTelemetryTime = millis();
    sendTelemetry(distanceCm, impactG);
  }

  delay(50);
}

// ==================== HELPER FUNCTIONS ====================

void connectToWiFi() {
  Serial.printf("[WiFi] Connecting to %s", WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  int retry = 0;
  while (WiFi.status() != WL_CONNECTED && retry < 20) {
    delay(500);
    Serial.print(".");
    retry++;
  }
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n[WiFi] Connected successfully!");
    Serial.printf("[WiFi] ESP32 IP Address: %s\n", WiFi.localIP().toString().c_str());
  } else {
    Serial.println("\n[WiFi] Connection failed. Telemetry will run in offline mode.");
  }
}

float readUltrasonicDistance() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  long duration = pulseIn(ECHO_PIN, HIGH, 30000); // 30ms timeout (~5m)
  if (duration == 0) return 400.0; // Out of range safe default
  float distance = (duration * 0.0343) / 2.0;
  return distance;
}

float readMPU6050Impact() {
  Wire.beginTransmission(MPU_ADDR);
  Wire.write(0x3B); // Starting register for Accel data
  Wire.endTransmission(false);
  Wire.requestFrom(MPU_ADDR, 6, true);

  if (Wire.available() < 6) return 1.0;

  int16_t AcX = Wire.read() << 8 | Wire.read();
  int16_t AcY = Wire.read() << 8 | Wire.read();
  int16_t AcZ = Wire.read() << 8 | Wire.read();

  // Convert raw value to Gs (Sensitivity scale factor: 16384 for +/-2g)
  float ax = (float)AcX / 16384.0;
  float ay = (float)AcY / 16384.0;
  float az = (float)AcZ / 16384.0;

  // Vector magnitude
  float totalG = sqrt(ax * ax + ay * ay + az * az);
  return totalG;
}

void updateSafetyIndicators(float distance, float impactG) {
  if (impactG >= CRASH_G_THRESHOLD || distance < DANGER_DISTANCE_CM) {
    // CRITICAL / DANGER
    digitalWrite(LED_RED, HIGH);
    digitalWrite(LED_YELLOW, LOW);
    digitalWrite(LED_GREEN, LOW);
    tone(BUZZER_PIN, 2000, 100);
  } else if (distance < WARNING_DISTANCE_CM) {
    // WARNING
    digitalWrite(LED_RED, LOW);
    digitalWrite(LED_YELLOW, HIGH);
    digitalWrite(LED_GREEN, LOW);
    noTone(BUZZER_PIN);
  } else {
    // SAFE
    digitalWrite(LED_RED, LOW);
    digitalWrite(LED_YELLOW, LOW);
    digitalWrite(LED_GREEN, HIGH);
    noTone(BUZZER_PIN);
  }
}

void sendTelemetry(float distance, float impactG) {
  if (WiFi.status() != WL_CONNECTED) return;

  HTTPClient http;
  String url = String(SERVER_BASE_URL) + "/api/vehicles/" + String(VEHICLE_ID) + "/sensors";
  http.begin(url);
  http.addHeader("Content-Type", "application/json");

  // Get GPS coords (fallback to simulated if indoor/no fix)
  double lat = gps.location.isValid() ? gps.location.lat() : 21.1458;
  double lng = gps.location.isValid() ? gps.location.lng() : 79.0882;
  float speedKmh = gps.speed.isValid() ? gps.speed.kmph() : 45.0;

  StaticJsonDocument<256> doc;
  doc["distance"] = distance;
  doc["impact_g"] = impactG;
  doc["vibration_level"] = impactG * 0.8;
  doc["tilt_angle"] = 2.5;
  doc["latitude"] = lat;
  doc["longitude"] = lng;
  doc["speed"] = speedKmh;

  String requestBody;
  serializeJson(doc, requestBody);

  int httpCode = http.POST(requestBody);
  if (httpCode > 0) {
    Serial.printf("[Telemetry] Uploaded: Dist=%.1fcm, Impact=%.2fg, GPS=(%.4f, %.4f) -> Code: %d\n",
                  distance, impactG, lat, lng, httpCode);
  } else {
    Serial.printf("[Telemetry] Failed to send: %s\n", http.errorToString(httpCode).c_str());
  }
  http.end();
}

void triggerCrashAlert(float impactG, float distance) {
  if (WiFi.status() != WL_CONNECTED) return;

  HTTPClient http;
  String url = String(SERVER_BASE_URL) + "/api/accidents/report";
  http.begin(url);
  http.addHeader("Content-Type", "application/json");

  double lat = gps.location.isValid() ? gps.location.lat() : 21.1458;
  double lng = gps.location.isValid() ? gps.location.lng() : 79.0882;

  StaticJsonDocument<256> doc;
  doc["vehicle_id"] = VEHICLE_ID;
  doc["impact_g"] = impactG;
  doc["latitude"] = lat;
  doc["longitude"] = lng;
  doc["status"] = "pending_countdown";

  String requestBody;
  serializeJson(doc, requestBody);

  int httpCode = http.POST(requestBody);
  Serial.printf("[Crash Report] Response Code: %d\n", httpCode);
  http.end();
}
