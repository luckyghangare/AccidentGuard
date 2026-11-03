from livekit.api import AccessToken, VideoGrants
from app.config import settings
from datetime import timedelta
import logging

logger = logging.getLogger(__name__)

class LiveKitService:
    def __init__(self):
        self.api_key = settings.LIVEKIT_API_KEY
        self.api_secret = settings.LIVEKIT_API_SECRET
        self.token_ttl = settings.LIVEKIT_TOKEN_TTL

    def generate_token(self, room_name: str, participant_identity: str, participant_name: str = None) -> str:
        """
        Generates a LiveKit Access Token for a participant to join a room.
        """
        try:
            grant = VideoGrants(
                room_join=True,
                room=room_name,
                can_publish=True,
                can_subscribe=True,
                can_publish_data=True
            )
            
            token = AccessToken(
                api_key=self.api_key,
                api_secret=self.api_secret
            )
            
            token.with_grants(grant)
            token.with_identity(participant_identity)
            
            if participant_name:
                token.with_name(participant_name)
            
            token.with_metadata("") # Can be used for extra data
            token.with_ttl(timedelta(seconds=self.token_ttl))
            
            return token.to_jwt()
        except Exception as e:
            logger.error(f"Error generating LiveKit token: {e}")
            raise e

livekit_service = LiveKitService()
