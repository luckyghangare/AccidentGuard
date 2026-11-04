
import logging
from datetime import timedelta

from livekit import api

from app.config import settings

logger = logging.getLogger(__name__)


class LiveKitService:
    def __init__(self):
        self.api_key = settings.LIVEKIT_API_KEY
        self.api_secret = settings.LIVEKIT_API_SECRET
        self.token_ttl = settings.LIVEKIT_TOKEN_TTL

    def generate_token(
            self,
            room_name: str,
            participant_identity: str,
            participant_name: str = None
    ) -> str:
        """
        Generates a LiveKit Access Token for a participant.
        """
        try:
            grant = api.VideoGrants(
                room_join=True,
                room=room_name,
                can_publish=True,
                can_subscribe=True,
                can_publish_data=True
            )

            token = api.AccessToken(
                api_key=self.api_key,
                api_secret=self.api_secret
            )

            token.with_grants(grant)
            token.with_identity(participant_identity)

            if participant_name:
                token.with_name(participant_name)

            token.with_metadata("")
            token.with_ttl(timedelta(seconds=self.token_ttl))

            return token.to_jwt()

        except Exception:
            logger.exception("Error generating LiveKit token")
            raise

    async def remove_participant(
            self,
            room_name: str,
            participant_identity: str
    ) -> bool:
        """
        Removes a participant from a LiveKit room.

        participant_identity must match the identity used
        while generating that participant's LiveKit token.
        """
        if not room_name or not participant_identity:
            logger.error(
                "Cannot remove participant: room name or identity missing"
            )
            return False

        client = None

        try:
            livekit_url = settings.LIVEKIT_URL

            # Room Service API uses HTTP/HTTPS.
            if livekit_url.startswith("wss://"):
                livekit_url = livekit_url.replace(
                    "wss://", "https://", 1
                )
            elif livekit_url.startswith("ws://"):
                livekit_url = livekit_url.replace(
                    "ws://", "http://", 1
                )

            client = api.LiveKitAPI(
                url=livekit_url,
                api_key=self.api_key,
                api_secret=self.api_secret
            )

            await client.room.remove_participant(
                api.RoomParticipantIdentity(
                    room=room_name,
                    identity=str(participant_identity)
                )
            )

            logger.info(
                "LiveKit participant removed successfully: "
                "room=%s identity=%s",
                room_name,
                participant_identity
            )

            return True

        except Exception:
            logger.exception(
                "Failed to remove LiveKit participant: "
                "room=%s identity=%s",
                room_name,
                participant_identity
            )
            return False

        finally:
            if client is not None:
                await client.aclose()


livekit_service = LiveKitService()
