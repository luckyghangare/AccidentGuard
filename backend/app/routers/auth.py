from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session
from sqlalchemy.exc import IntegrityError
from app.database import get_db
from app.models import User
from app.schemas.auth import UserRegister, UserLogin, UserResponse, TokenResponse, FCMTokenUpdate
from app.utils.security import hash_password, verify_password, create_access_token
from app.utils.response import success_response, error_response
from app.dependencies import get_current_user

router = APIRouter(prefix="/api/auth", tags=["Authentication"])

@router.post("/register")
def register_user(user_in: UserRegister, db: Session = Depends(get_db)):
    # Check for existing user before attempting insertion
    existing = db.query(User).filter(User.email == user_in.email).first()
    if existing:
        return error_response("EMAIL_EXISTS", "User with this email already exists", status_code=status.HTTP_400_BAD_REQUEST)
    
    try:
        new_user = User(
            full_name=user_in.full_name,
            email=user_in.email,
            phone_number=user_in.phone_number,
            password_hash=hash_password(user_in.password),
            is_active=True
        )
        db.add(new_user)
        db.commit()
        db.refresh(new_user)
        
        user_data = UserResponse.model_validate(new_user).model_dump()
        return success_response(data=user_data, message="Registration successful", status_code=status.HTTP_201_CREATED)
    except IntegrityError:
        db.rollback()
        return error_response("EMAIL_EXISTS", "A user with this email already exists.", status_code=status.HTTP_400_BAD_REQUEST)
    except Exception:
        db.rollback()
        return error_response("SERVER_ERROR", "An unexpected error occurred during registration.", status_code=status.HTTP_500_INTERNAL_SERVER_ERROR)

@router.post("/login")
def login_user(credentials: UserLogin, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.email == credentials.email).first()
    
    # 1. Verify existence and password
    if not user or not verify_password(credentials.password, user.password_hash):
        return error_response("INVALID_CREDENTIALS", "Invalid email or password", status_code=status.HTTP_401_UNAUTHORIZED)
    
    # 2. Reject inactive users
    if not user.is_active:
        return error_response("ACCOUNT_INACTIVE", "This account has been deactivated. Please contact support.", status_code=status.HTTP_403_FORBIDDEN)
    
    token = create_access_token(data={"sub": user.email, "user_id": user.id, "role": "user"})
    user_data = UserResponse.model_validate(user).model_dump()
    
    return success_response(
        data={
            "access_token": token,
            "token_type": "bearer",
            "user": user_data
        },
        message="Login successful"
    )

@router.post("/logout")
def logout_user(current_user: User = Depends(get_current_user)):
    return success_response(message="Logged out successfully")

@router.get("/me")
def get_me(current_user: User = Depends(get_current_user)):
    user_data = UserResponse.model_validate(current_user).model_dump()
    return success_response(data=user_data, message="User details fetched successfully")

@router.put("/profile")
def update_profile(update_data: dict, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    if "full_name" in update_data and update_data["full_name"]:
        current_user.full_name = update_data["full_name"]
    if "phone_number" in update_data and update_data["phone_number"]:
        current_user.phone_number = update_data["phone_number"]
    db.commit()
    db.refresh(current_user)
    user_data = UserResponse.model_validate(current_user).model_dump()
    return success_response(data=user_data, message="Profile updated successfully")

@router.post("/fcm-token")
def update_fcm_token(token_data: FCMTokenUpdate, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    token = token_data.fcm_token.strip() if token_data.fcm_token else ""
    
    # Allow empty string to clear the token, but reject whitespace-only values
    if token_data.fcm_token and not token:
        return error_response("INVALID_TOKEN", "FCM token cannot be empty or whitespace only", status_code=status.HTTP_400_BAD_REQUEST)
        
    current_user.fcm_token = token if token else None
    db.commit()
    db.refresh(current_user)
    return success_response(message="FCM token updated successfully")
