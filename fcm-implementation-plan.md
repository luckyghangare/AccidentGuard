# FCM Notification System Implementation Plan

## Goal
Implement end-to-end emergency push notifications: Android token registration -> Backend storage -> Accident trigger -> Push delivery.

## Tasks
- [ ] **Backend: Firebase Initialization** → Update `app/main.py` or a dedicated lifecycle hook to initialize `firebase-admin` using `FIREBASE_SERVICE_ACCOUNT_PATH` from `.env`.
- [ ] **Backend: Robust Testing** → Fix `tests/test_notifications.py` to handle the actual response structure of the API (success/error wrappers) and ensure stable DB connections.
- [ ] **Android: Token Registration Logic** → Modify `AccidentGuardMessagingService.kt` to send the FCM token to the backend whenever `onNewToken` is called, provided the user is logged in.
- [ ] **Android: Login Integration** → Update the authentication flow (likely in a ViewModel) to fetch and send the current FCM token immediately after a successful login.
- [ ] **Android: Notification Handling** → Ensure `AccidentGuardMessagingService.kt` correctly handles high-priority data payloads and displays a local notification if needed (or if the system notification isn't sufficient).
- [ ] **Verification: Backend** → Run `pytest tests/test_notifications.py` and confirm all 3 tests pass.
- [ ] **Verification: End-to-End** → Deploy/Run backend, log in on the physical device, trigger a mock accident via API, and verify the push notification is received.

## Done When
- [ ] FCM token is saved in the MySQL `users` table for the logged-in user.
- [ ] Reporting an accident via `POST /api/accidents/report` triggers a mocked (in tests) or real FCM dispatch.
- [ ] Physical device receives a notification when a test accident is reported for its associated vehicle.
