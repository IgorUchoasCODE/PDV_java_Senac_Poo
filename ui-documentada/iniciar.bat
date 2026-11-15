@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0iniciar_ui_documentada.ps1"
if errorlevel 1 pause
