@echo off
chcp 65001 >nul
title TikTok Live Reader - Python Engine
cd /d "%~dp0"

set PY=venv312\Scripts\python.exe
if not exist "%PY%" (
  echo venv312 tidak ditemukan, memakai python global...
  set PY=python
)

echo Memeriksa library server...
"%PY%" -c "import fastapi, uvicorn, httpx, TikTokLive" 2>nul
if errorlevel 1 (
  echo Memasang library yang kurang...
  "%PY%" -m pip install -r requirements_server.txt
)

rem ============================================================
rem  Port 8000 sudah dipakai? Bisa server lama yang masih jalan,
rem  atau program lain. Script ini: cek server lama, tawarkan matikan.
rem ============================================================
set TTL_PORT=8000
"%PY%" -c "import socket,sys;s=socket.socket();r=s.connect_ex(('127.0.0.1',8000));s.close();sys.exit(0 if r==0 else 1)"
if errorlevel 1 goto run

echo.
echo Port 8000 sudah dipakai proses lain.
"%PY%" -c "import urllib.request,json;d=json.load(urllib.request.urlopen('http://127.0.0.1:8000/api/ping',timeout=3));print('  -> Terdeteksi server TikTok Live Reader yang masih berjalan (v'+d.get('version','?')+')')" 2>nul
if not errorlevel 1 (
  echo  Server lama akan ditutup otomatis supaya port bisa dipakai.
  for /f "tokens=5" %%p in ('netstat -ano ^| findstr /r /c:":8000 .*LISTENING"') do (taskkill /f /pid %%p >nul 2>&1 & taskkill /f /t /pid %%p >nul 2>&1)
  timeout /t 2 >nul
) else (
  echo  Bukan server TikTok Live Reader. Server ini akan memakai port 8001.
  set TTL_PORT=8001
)

:run
echo.
"%PY%" server_app.py

echo.
echo Server berhenti. Tekan tombol apa saja untuk menutup jendela.
pause >nul
