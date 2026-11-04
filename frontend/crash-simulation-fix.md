# Crash Simulation Fix

## Goal
Fix the `CrashViewModel` to report crashes using the real `vehicle_id` and handle the build process within memory constraints.

## Tasks
- [x] Fix `CrashViewModel.kt` logic to fetch `vehicle_id` from `VehicleRepository` → Verify: Code uses `vehicleRepo.getVehicles(token)` instead of hardcoded `1L`.
- [x] Adjust Gradle memory settings in `gradle.properties` → Verify: `org.gradle.jvmargs` set to `-Xmx1024m`.
- [ ] Perform a clean build of the `:app` module → Verify: `.\gradlew.bat clean :app:assembleDebug` succeeds.
- [ ] Install the new APK and trigger crash simulation → Verify: `POST /api/accidents/report` uses correct `vehicle_id` in Logcat.
- [ ] Verify LiveKit session polling → Verify: `GET /api/calls/active` is called and "JOIN EMERGENCY CALL" appears if a session exists.

## Done When
- [ ] Application builds successfully.
- [ ] Crash simulation reports the correct `vehicle_id`.
- [ ] Call polling logic initiates correctly after crash reporting.
