@echo off
rem BlindSight one-click starter for Windows.
rem Double-click this file. First run creates a private Python environment
rem and installs the requirements; every run opens the app in your browser.
title BlindSight gateway
cd /d "%~dp0"

where python >nul 2>nul
if errorlevel 1 (
  echo Python was not found. Install Python 3.9 or newer from python.org
  echo and tick "Add python.exe to PATH" during setup, then run me again.
  pause
  exit /b 1
)

if not exist ".venv\Scripts\python.exe" (
  echo First run: creating a private Python environment, please wait...
  python -m venv .venv || goto :err
)

echo Installing or updating the requirements...
".venv\Scripts\python.exe" -m pip install -q -r edge_server\requirements.txt || goto :err

echo.
echo   BlindSight is starting on  http://localhost:5000
echo   Use Chrome or Edge on this computer for the full voice experience.
echo   Voice input works on localhost. For PHONE voice use, run the gateway on
echo   the Pi (its starter enables HTTPS) or search Chrome flags for
echo   "insecure origins" and allow this computer's address.
echo   Your phone, on the same Wi-Fi, can use the same address with
echo   this computer's IP instead of localhost.
echo   Keep this window open while using the app. Close it to stop.
echo.
start "" /b cmd /c "ping -n 4 127.0.0.1 >nul & start http://localhost:5000"
".venv\Scripts\python.exe" edge_server\server.py
goto :eof

:err
echo.
echo Setup failed. Re-run this file; if it keeps failing, copy the whole
echo window text and start a new Arena session with it.
pause
exit /b 1
