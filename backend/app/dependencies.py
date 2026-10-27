
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from jose import JWTError, jwt
from sqlalchemy.orm import Session

from app.config import settings
from app.database import get_db
from app.models import User, Hospital


user_bearer = HTTPBearer(
    auto_error=False,
    scheme_name="UserBearer",
    description="Paste the access token returned by POST /api/auth/login",
)

hospital_bearer = HTTPBearer(
    auto_error=False,
    scheme_name="HospitalBearer",
    description="Paste the access token returned by POST /api/hospitals/login",
)


def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(user_bearer),
    db: Session = Depends(get_db),
) -> User:
    unauthorized = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate user credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )

    if credentials is None:
        raise unauthorized

    try:
        payload = jwt.decode(
            credentials.credentials,
            settings.SECRET_KEY,
            algorithms=[settings.ALGORITHM],
        )
        email = payload.get("sub")

        # Hospital tokens must not authenticate as normal users.
        if not email or payload.get("role") == "hospital":
            raise unauthorized

    except JWTError:
        raise unauthorized

    user = db.query(User).filter(User.email == email).first()

    if user is None or not user.is_active:
        raise unauthorized

    return user


def get_current_hospital(
    credentials: HTTPAuthorizationCredentials = Depends(hospital_bearer),
    db: Session = Depends(get_db),
) -> Hospital:
    unauthorized = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate hospital credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )

    if credentials is None:
        raise unauthorized

    try:
        payload = jwt.decode(
            credentials.credentials,
            settings.SECRET_KEY,
            algorithms=[settings.ALGORITHM],
        )
        email = payload.get("sub")

        if not email or payload.get("role") != "hospital":
            raise unauthorized

    except JWTError:
        raise unauthorized

    hospital = db.query(Hospital).filter(Hospital.email == email).first()

    if hospital is None or not hospital.is_active:
        raise unauthorized

    return hospital
