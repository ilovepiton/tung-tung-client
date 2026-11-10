@echo off
setlocal
if not exist "%~dp0ui\index.html" (
  echo Extract the complete ZIP before starting Tung Tung Client.
  pause
  exit /b 1
)
start "" "%~dp0Tung-Tung-Client.exe"
