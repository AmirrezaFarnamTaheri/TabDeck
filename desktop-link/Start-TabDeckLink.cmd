@echo off
setlocal
where pwsh.exe >nul 2>nul
if errorlevel 1 (
  echo PowerShell 7.2 or newer is required. Install it from Microsoft, then run this launcher again.
  pause
  exit /b 1
)
pwsh.exe -NoProfile -ExecutionPolicy Bypass -Sta -File "%~dp0TabDeckLink.ps1"
