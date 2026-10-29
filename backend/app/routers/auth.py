from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from app.database import get_db
from app.models import User
from app.schemas.auth import UserRegister, UserLogin, UserResponse, TokenResponse, FCMTokenUpdate
from app.utils.security import hash_password, verify_password, create_access_token
from app.utils.response import success_response, error_response
from app.dependencies import get_current_user

router = APIRouter(prefix="/api/auth", tags=["Authentication"])

@router.post("/register")
def register_user(user_in: UserRegister, db: Session = Depends(get_db)):
    existing = db.query(User).filter(User.email == user_in.email).first()
    if existing:
        return error_response("EMAIL_EXISTS", "User with this email already exists", status_code=400)
    
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
    return success_response(data=user_data, message="Registration successful", status_code=201)

@router.post("/login")
def login_user(credentials: UserLogin, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.email == credentials.email).first()
    if not user or not verify_password(credentials.password, user.password_hash):
        return error_response("INVALID_CREDENTIALS", "Invalid email or password", status_code=401)
    
    token = create_access_token(data={"sub": user.email, "user_id": user.id})
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
    current_user.fcm_token = token_data.fcm_token
    db.commit()
    db.refresh(current_user)
    return success_response(message="FCM token updated successfully")
