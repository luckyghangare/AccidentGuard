import logging
from typing import Dict, Optional
from firebase_admin import messaging
from firebase_admin.exceptions import FirebaseError
from sqlalchemy.orm import Session
from app.models.user import User

logger = logging.getLogger(__name__)

class NotificationService:
    @staticmethod
    def send_fcm_notification(
        token: str,
        title: str,
        body: str,
        data: Optional[Dict[str, str]] = None,
        db: Optional[Session] = None
    ) -> bool:
        """
        Sends a high-priority FCM notification and handles token cleanup.
        """
        if not token:
            return False

        message = messaging.Message(
            notification=messaging.Notification(title=title, body=body),
            data=data or {},
            token=token,
            android=messaging.AndroidConfig(
                priority="high",
                notification=messaging.AndroidNotification(
                    channel_id="accident_alerts_channel",
                    priority="high",
                    default_vibrate_timings=True,
                    default_sound=True
                ),
            ),
        )

        try:
            messaging.send(message)
            return True
        except messaging.UnregisteredError:
            logger.error(f"FCM token is unregistered. Clearing from DB: {token}")
            if db:
                db.query(User).filter(User.fcm_token == token).update({"fcm_token": None})
                db.commit()
            return False
        except Exception as e:
            logger.error(f"FCM Error: {e}")
            return False

    @staticmethod
    def send_accident_alert(db: Session, token: str, accident_id: int, location_text: str = "Unknown", victim_name: str = "Someone"):
        return NotificationService.send_fcm_notification(
            token=token,
            title="AccidentGuard Emergency Alert",
            body=f"EMERGENCY: {victim_name} may have been in an accident near {location_text}.",
            data={
                "accident_id": str(accident_id),
                "type": "ACCIDENT",
                "victim_name": victim_name,
                "location": location_text
            },
            db=db
        )

    @staticmethod
    def send_accident_cancellation(db: Session, token: str, accident_id: int, victim_name: str = "Someone"):
        return NotificationService.send_fcm_notification(
            token=token,
            title="Accident Alert Cancelled",
            body=f"The accident alert for {victim_name} has been cancelled.",
            data={
                "accident_id": str(accident_id),
                "type": "ACCIDENT_CANCELLED",
                "victim_name": victim_name
            },
            db=db
        )

    @staticmethod
    def send_multi_accident_alert(db: Session, tokens: list[str], accident_id: int, location_text: str = "Unknown", victim_name: str = "Someone"):
        """Sends accident alerts to multiple recipients."""
        success_count = 0
        for token in tokens:
            if NotificationService.send_accident_alert(db, token, accident_id, location_text, victim_name):
                success_count += 1
        return success_count
