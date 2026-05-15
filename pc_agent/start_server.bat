@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo Remote Trackpad — запуск сервера на ПК...
python -m pip install -r requirements.txt -q 2>nul
python server.py
echo.
pause
