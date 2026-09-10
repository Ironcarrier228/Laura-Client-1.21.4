@echo off
setlocal
title Laura Client Launcher
rem ============================================================
rem  Laura Client - launcher wrapper (Windows)
rem  Runs the PowerShell launcher, keeps the console open on error.
rem ============================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0LauraLauncher.ps1" %*
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" (
    echo.
    echo [Laura] Launcher failed with code %RC%. Check the messages above.
    pause
)
endlocal
