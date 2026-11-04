# Phase 1: LiveKit Backend Foundation

## Goal
Implement the core backend infrastructure for LiveKit integration, including configuration, database models, and token generation service.

## Tasks
- [x] **Task 1: Update Configuration** → Add LiveKit settings to `backend/app/config.py`.
- [x] **Task 2: Create Database Models** → Implement `EmergencyCallSession` and `CallParticipant` in `backend/app/models/call.py`.
- [x] **Task 3: Register Models** → Add new models to `backend/app/models/__init__.py`.
- [x] **Task 4: Database Migration** → Create and run a migration script `update_db_v3_calls.py`.
- [x] **Task 5: Install Dependencies** → Add `livekit-api` to `backend/requirements.txt`.
- [x] **Task 6: Implement LiveKit Service** → Create `backend/app/services/livekit_service.py` for token generation.
- [x] **Task 7: Verification Tests** → Create `backend/tests/test_livekit_token.py` to verify token logic.

## Done When
- [ ] LiveKit configuration is loaded from `.env`.
- [ ] Database tables `emergency_call_sessions` and `call_participants` exist.
- [ ] LiveKit service can generate valid JWT tokens for victims and responders.
- [ ] All existing backend tests pass.
