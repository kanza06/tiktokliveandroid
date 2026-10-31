@echo off
chcp 65001 >nul
title Build APK - TikTok Live Reader
cd /d "%~dp0android"

set JAVA_HOME=C:\Program Files\Java\jdk-17
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo JDK 17 tidak ditemukan di %JAVA_HOME%
  echo Ubah JAVA_HOME di file ini sesuai lokasi JDK Anda.
  pause
  exit /b 1
)

call gradlew.bat assembleDebug
if errorlevel 1 (
  echo.
  echo BUILD GAGAL. Lihat pesan di atas.
  pause
  exit /b 1
)

echo.
echo Selesai! APK ada di:
echo   %cd%\app\build\outputs\apk\debug\app-debug.apk
pause
