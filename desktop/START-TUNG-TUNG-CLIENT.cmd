@echo off
setlocal
title Tung Tung Client
if exist "%~dp0Tung-Tung-Client.exe" (
  start "" "%~dp0Tung-Tung-Client.exe"
  exit /b 0
)
if not exist "%~dp0desktop\Launch.ps1" (
  echo Extract the complete ZIP before opening START-TUNG-TUNG-CLIENT.cmd.
  echo Keep the desktop and payload folders beside this file.
  pause
  exit /b 1
)
start "" "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -STA -WindowStyle Hidden -ExecutionPolicy Bypass -File "%~dp0desktop\Launch.ps1"
if errorlevel 1 (
  echo Windows PowerShell could not start. This app requires Windows 10 or 11.
  pause
  exit /b 1
)
exit /b 0
