
import logging
from typing import Dict, Optional, List

from firebase_admin import messaging
from sqlalchemy.orm import Session

from app.models.user import User

logger = logging.getLogger(__name__)


class NotificationService:

    # ---------------------------------------------------------
    # INTERNAL: Remove invalid FCM token from database
    # ---------------------------------------------------------
    @staticmethod
    def _clear_invalid_token(token: str, db: Optional[Session] = None) -> None:
        if db is None:
            return

        try:
            db.query(User).filter(
                User.fcm_token == token
            ).update(
                {"fcm_token": None},
                synchronize_session=False
            )
            db.commit()

            logger.info("Invalid FCM token cleared from database.")

        except Exception:
            db.rollback()
            logger.exception("Failed to clear invalid FCM token.")

    # ---------------------------------------------------------
    # 1. STANDARD FCM NOTIFICATION
    # Used for accident alerts and other visible notifications
    # ---------------------------------------------------------
    @staticmethod
    def send_fcm_notification(
            token: str,
            title: str,
            body: str,
            data: Optional[Dict[str, str]] = None,
            db: Optional[Session] = None
    ) -> bool:

        if not token:
            logger.warning("FCM notification skipped: missing token.")
            return False

        try:
            message = messaging.Message(
                notification=messaging.Notification(
                    title=title,
                    body=body
                ),
                data={
                    str(key): str(value)
                    for key, value in (data or {}).items()
                },
                token=token,
                android=messaging.AndroidConfig(
                    priority="high",
                    notification=messaging.AndroidNotification(
                        channel_id="accident_alerts_channel",
                        priority="high",
                        default_vibrate_timings=True,
                        default_sound=True
                    )
                )
            )

            messaging.send(message)

            logger.info("FCM notification sent successfully.")
            return True

        except messaging.UnregisteredError:
            logger.warning(
                "FCM token is unregistered. Clearing it from database."
            )
            NotificationService._clear_invalid_token(token, db)
            return False

        except Exception:
            logger.exception("FCM notification sending failed.")
            return False

    # ---------------------------------------------------------
    # 2. DATA-ONLY FCM MESSAGE
    # Used for remote call control events
    # No notification payload is included
    # ---------------------------------------------------------
    @staticmethod
    def send_data_fcm_message(
            token: str,
            data: Dict[str, str],
            db: Optional[Session] = None
    ) -> bool:

        if not token:
            logger.warning("Data FCM message skipped: missing token.")
            return False

        try:
            message = messaging.Message(
                data={
                    str(key): str(value)
                    for key, value in data.items()
                },
                token=token,
                android=messaging.AndroidConfig(
                    priority="high"
                )
            )

            messaging.send(message)

            logger.info(
                "Data-only FCM message sent successfully. Type: %s",
                data.get("type", "UNKNOWN")
            )
            return True

        except messaging.UnregisteredError:
            logger.warning(
                "FCM token is unregistered. Clearing it from database."
            )
            NotificationService._clear_invalid_token(token, db)
            return False

        except Exception:
            logger.exception("Data-only FCM message sending failed.")
            return False

    # ---------------------------------------------------------
    # 3. EMERGENCY CALL ENDED EVENT
    # Sent to the other participant when a call ends
    # ---------------------------------------------------------
    @staticmethod
    def send_call_ended_notification(
            db: Session,
            token: str,
            session_id: int,
            victim_name: str = "Someone"
    ) -> bool:

        return NotificationService.send_data_fcm_message(
            token=token,
            data={
                "type": "EMERGENCY_CALL_ENDED",
                "session_id": str(session_id),
                "victim_name": victim_name or "Someone"
            },
            db=db
        )

    # ---------------------------------------------------------
    # 4. ACCIDENT ALERT
    # ---------------------------------------------------------
    @staticmethod
    def send_accident_alert(
            db: Session,
            token: str,
            accident_id: int,
            location_text: str = "Unknown",
            victim_name: str = "Someone"
    ) -> bool:

        return NotificationService.send_fcm_notification(
            token=token,
            title="AccidentGuard Emergency Alert",
            body=(
                f"EMERGENCY: {victim_name} may have been "
                f"in an accident near {location_text}."
            ),
            data={
                "accident_id": str(accident_id),
                "type": "ACCIDENT",
                "victim_name": victim_name,
                "location": location_text
            },
            db=db
        )

    # ---------------------------------------------------------
    # 5. ACCIDENT ALERT CANCELLATION
    # ---------------------------------------------------------
    @staticmethod
    def send_accident_cancellation(
            db: Session,
            token: str,
            accident_id: int,
            victim_name: str = "Someone"
    ) -> bool:

        return NotificationService.send_fcm_notification(
            token=token,
            title="Accident Alert Cancelled",
            body=(
                f"The accident alert for {victim_name} "
                "has been cancelled."
            ),
            data={
                "accident_id": str(accident_id),
                "type": "ACCIDENT_CANCELLED",
                "victim_name": victim_name
            },
            db=db
        )

    # ---------------------------------------------------------
    # 6. MULTIPLE RECIPIENT ACCIDENT ALERT
    # ---------------------------------------------------------
    @staticmethod
    def send_multi_accident_alert(
            db: Session,
            tokens: List[str],
            accident_id: int,
            location_text: str = "Unknown",
            victim_name: str = "Someone"
    ) -> int:

        success_count = 0

        for token in tokens:
            if NotificationService.send_accident_alert(
                    db=db,
                    token=token,
                    accident_id=accident_id,
                    location_text=location_text,
                    victim_name=victim_name
            ):
                success_count += 1

        logger.info(
            "Multi accident alert completed. Successful: %s/%s",
            success_count,
            len(tokens)
        )

        return success_count
