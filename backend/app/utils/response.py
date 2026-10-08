from typing import Any
from fastapi.responses import JSONResponse
from fastapi.encoders import jsonable_encoder

def success_response(data: Any = None, message: str = "Operation successful", status_code: int = 200):
    serializable_data = jsonable_encoder(data if data is not None else {})
    return JSONResponse(
        status_code=status_code,
        content={
            "success": True,
            "data": serializable_data,
            "message": message
        }
    )

def error_response(code: str, message: str, status_code: int = 400):
    return JSONResponse(
        status_code=status_code,
        content={
            "success": False,
            "error": {
                "code": code,
                "message": message
            }
        }
    )
