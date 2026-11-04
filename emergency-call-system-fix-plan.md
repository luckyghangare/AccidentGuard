# Emergency Call System Fix Plan

## 1. Frontend Refactoring

### 1.1 LiveKitManager.kt
- [ ] Make `disconnect()` a `suspend` function with proper cleanup and mutex protection.
- [ ] Refactor Audio Focus handling:
    - [ ] Do not disconnect on `AUDIOFOCUS_LOSS`.
    - [ ] track `userMutePreference` separately.
    - [ ] Restore microphone only if `userMutePreference` is false.
- [ ] Add granular connection states (Connecting, Connected, Reconnecting, etc.).
- [ ] Ensure `Room` release is handled correctly and only once.

### 1.2 CallService.kt
- [ ] Remove automatic `stopSelf()` on LiveKit error/disconnect.
- [ ] Implement `ACTION_START_CALL` idempotency (don't reconnect if already connected to same session).
- [ ] Ensure Foreground Notification persists correctly.
- [ ] Handle `onTaskRemoved` safely.

### 1.3 CallViewModel.kt
- [ ] Expand `CallUiState` to map LiveKit connection states.
- [ ] Refactor `acceptCall` to start service and wait for connection before moving to "Active" state if desired, or let the UI handle the transition based on Service state.
- [ ] Improve Service binding logic.

### 1.4 UI Screens
- [ ] Update `IncomingCallScreen` to handle loading and errors better.
- [ ] Update `ActiveCallScreen` to show granular states (Waiting for participant, Audio active, etc.).
- [ ] Fix potential duplicate `joinCall` triggers.

## 2. Backend Verification
- [ ] Audit `accept_call` for idempotency and atomicity.
- [ ] Verify `generate_token` uses correct TTL and grants.

## 3. Verification & Testing
- [ ] Build and install APK.
- [ ] Test Answer/Decline flows.
- [ ] Test Audio Focus (GSM interruption).
- [ ] Test Network Reconnection.
