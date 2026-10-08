@echo off
title AccidentGuard - Backend Server
color 0A
echo ============================================================
echo    ACCIDENTGUARD BACKEND SERVER STARTUP
echo ============================================================
echo.

REM Step 1: Start MySQL via XAMPP if not running
echo [1/3] Checking MySQL status...
sc query mysql 2>nul | find "RUNNING" >nul 2>&1
if errorlevel 1 (
    echo      Starting MySQL (XAMPP)...
    net start mysql >nul 2>&1
    if errorlevel 1 (
        echo      [INFO] MySQL may already be running or managed by XAMPP.
    ) else (
        echo      MySQL started successfully.
    )
) else (
    echo      MySQL is already running.
)

echo.
echo [2/3] Starting FastAPI Backend Server...
echo      Binding on 0.0.0.0:8000 (accessible from phone on Wi-Fi)
echo.

REM Step 2: Launch the backend
cd /d "%~dp0backend"

if exist "venv\Scripts\uvicorn.exe" (
    echo ============================================================
    echo   Backend running at:
    echo     Local:  http://localhost:8000
    echo     Phone:  http://192.168.1.35:8000
    echo     Docs:   http://localhost:8000/docs
    echo ============================================================
    echo.
    echo   [3/3] Keep this window open while using the Android app!
    echo   Press Ctrl+C to stop the server.
    echo.
    venv\Scripts\uvicorn.exe app.main:app --host 0.0.0.0 --port 8000 --reload
) else (
    echo [ERROR] Virtual environment not found.
    echo         Please run: pip install -r requirements.txt
    pause
)
