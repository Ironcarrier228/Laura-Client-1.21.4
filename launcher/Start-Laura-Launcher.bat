@echo off
setlocal
title Laura Launcher
rem ============================================================
rem  Laura Launcher - one-click starter for Windows.
rem  Installs dependencies on first run, then starts Electron.
rem  Keep this file ASCII-only + CRLF, otherwise cmd.exe breaks.
rem ============================================================
cd /d "%~dp0"

where node >nul 2>nul
if errorlevel 1 (
    echo [Laura] Node.js not found. Install Node.js 18+ LTS from https://nodejs.org/ and retry.
    pause
    exit /b 1
)

if not exist "node_modules\electron" (
    echo [Laura] First run: installing dependencies, please wait...
    call npm install
    if errorlevel 1 (
        echo [Laura] npm install failed. Check your internet connection and retry.
        pause
        exit /b 1
    )
)

call npm start
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" (
    echo.
    echo [Laura] Launcher exited with code %RC%. See the messages above.
    pause
)
endlocal
