import pytest
from app.services.livekit_service import livekit_service
from app.config import settings
from livekit.api import AccessToken
import jwt

def test_generate_token_success():
    room_name = "accident_123"
    participant_identity = "user_456"
    participant_name = "John Doe"
    
    token = livekit_service.generate_token(room_name, participant_identity, participant_name)
    
    assert token is not None
    assert isinstance(token, str)
    
    # Decode token to verify contents (using the secret)
    decoded = jwt.decode(token, settings.LIVEKIT_API_SECRET, algorithms=["HS256"])
    
    assert decoded["iss"] == settings.LIVEKIT_API_KEY
    assert decoded["sub"] == participant_identity
    assert decoded["video"]["room"] == room_name
    assert decoded["video"]["roomJoin"] is True

def test_token_ttl():
    room_name = "test_room"
    participant_identity = "test_user"
    
    token = livekit_service.generate_token(room_name, participant_identity)
    
    decoded = jwt.decode(token, settings.LIVEKIT_API_SECRET, algorithms=["HS256"])
    
    # Verify exp is set
    assert "exp" in decoded
    assert "nbf" in decoded
    assert decoded["exp"] > decoded["nbf"]

if __name__ == "__main__":
    pytest.main([__file__])
