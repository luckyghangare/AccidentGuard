from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.exceptions import RequestValidationError
from app.config import settings
from app.utils.response import success_response, error_response
from app.routers import auth, vehicles, gps, sensors, accidents, contacts, hospitals, hospital_web

app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description="Accident Avoidance System with Crash Detection and GPS Notification Backend API"
)

# Enable CORS for Android client & ESP32 hardware requests
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include Routers
app.include_router(auth.router)
app.include_router(vehicles.router)
app.include_router(gps.router)
app.include_router(sensors.router)
app.include_router(accidents.router)
app.include_router(contacts.router)
app.include_router(hospitals.router)
app.include_router(hospital_web.router)


# Custom Validation Exception Handler to match unified JSON API response standard
@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    errors = exc.errors()
    first_error = errors[0]["msg"] if errors else "Invalid request data"
    return error_response(
        code="VALIDATION_ERROR",
        message=first_error,
        status_code=422
    )

@app.get("/")
def read_root():
    return success_response(
        data={
            "app_name": settings.APP_NAME,
            "version": settings.APP_VERSION,
            "status": "online",
            "documentation": "/docs"
        },
        message="AccidentGuard Backend API is running successfully!"
    )
