@echo off
chcp 65001 >nul
title TikTok Live Reader - Server + Akses Luar Rumah
cd /d "%~dp0"

rem ============================================================
rem  Engine TikTok tetap berjalan di KOMPUTER INI (IP rumah),
rem  jadi tidak diblokir TikTok. Tunnel hanya untuk HP masuk
rem  dari jaringan mana pun lewat HTTPS.
rem
rem  Ganti teks di bawah dengan API key rahasia Anda sendiri
rem  (huruf/angka, minimal 12 karakter). Kosongkan = tanpa key.
rem ============================================================
set TTL_API_KEY=ganti-dengan-kunci-rahasia-anda

set PY=venv312\Scripts\python.exe
if not exist "%PY%" set PY=python

if not exist "server\cloudflared.exe" (
  echo cloudflared.exe belum ada, mengunduh...
  powershell -NoProfile -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest 'https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe' -OutFile 'server\cloudflared.exe'"
)

"%PY%" -c "import fastapi, uvicorn, httpx, TikTokLive" 2>nul
if errorlevel 1 "%PY%" -m pip install -r requirements_server.txt

echo.
echo Server + tunnel dimulai. Tunggu baris "AKSES DARI LUAR RUMAH".
echo.
"%PY%" server_app.py

pause >nul
