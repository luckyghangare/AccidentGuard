@echo off
REM ============================================================================
REM AccidentGuard - Android APK Build Script
REM ============================================================================
setlocal enabledelayedexpansion

REM Set JAVA_HOME to Android Studio bundled JDK if not already configured
if not defined JAVA_HOME (
    if exist "D:\Program Files\Android\Android Studio\jbr" (
        set "JAVA_HOME=D:\Program Files\Android\Android Studio\jbr"
    ) else if exist "C:\Program Files\Android\Android Studio\jbr" (
        set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    )
)

if defined JAVA_HOME (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
    echo [AccidentGuard] Using Java from: %JAVA_HOME%
)

echo [AccidentGuard] Building Android APK (Debug)...
cd /d "%~dp0frontend"

if exist gradlew.bat (
    call gradlew.bat assembleDebug
) else (
    echo Gradle wrapper not found. Generating wrapper...
    gradle wrapper
    call gradlew.bat assembleDebug
)

echo.
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo ============================================================================
    echo [SUCCESS] APK Generated Successfully!
    echo Location: %~dp0frontend\app\build\outputs\apk\debug\app-debug.apk
    echo ============================================================================
) else (
    echo [INFO] You can also open the 'frontend' folder in Android Studio and click:
    echo        Build -^> Build Bundle(s) / APK(s) -^> Build APK(s)
)
pause
