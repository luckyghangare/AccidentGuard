# Emergency Call Integration Plan (Phase 3)

## Goal
Integrate backend emergency call invitations with the Android application using FCM to create a reliable incoming call experience.

## Tasks
- [x] Task 1: Update `MainActivity.kt` to handle Intent extras for deep-linking notification taps to `IncomingCallScreen`. → Verify: App navigates to `IncomingCallScreen` when intent contains `navigate_to = "incoming_call"`.
- [x] Task 2: Enhance `IncomingCallScreen.kt` UI to display victim name if provided via Intent or session details. → Verify: Victim name is visible on the screen.
- [x] Task 3: Implement conflict handling (HTTP 409) in `CallViewModel.acceptCall`. → Verify: UI displays a specific message like "Call already accepted by another contact" when 409 is received.
- [x] Task 4: Ensure `AccidentGuardMessagingService` and `NotificationHelper` are correctly passing all necessary data in the Intent. → Verify: `session_id` and `victim_name` are present in the `PendingIntent` extras.
- [x] Task 5: Final verification of the end-to-end flow: Invitation FCM -> Notification -> Tapping Notification -> `IncomingCallScreen` -> Accept -> API State Sync.

## Done When
- [x] Emergency call notifications reliably trigger the `IncomingCallScreen`.
- [x] The responder can see the victim's name (if available).
- [x] Accepting a call correctly syncs with the backend and transitions to the active call state.
- [x] Conflicts (multiple responders) are handled gracefully.
