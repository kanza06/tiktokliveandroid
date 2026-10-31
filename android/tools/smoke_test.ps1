# Smoke test aplikasi Android di emulator/perangkat yang tersambung:
#  1. aktifkan mode demo di server   2. pasang APK
#  3. arahkan app ke IP server lokal (lewat adb reverse: 127.0.0.1:8000)
#  4. screenshot lalu tarik ke folder android/screenshots
param(
    [string]$Adb = "C:\Android\Sdk\platform-tools\adb.exe",
    [string]$ServerUrl = "http://10.0.2.2:8000",
    [int]$Port = 8000
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path "$PSScriptRoot\..\..").Path
$apk = Join-Path $root "android\app\build\outputs\apk\debug\app-debug.apk"
$shots = Join-Path $root "android\screenshots"
New-Item -ItemType Directory -Force -Path $shots | Out-Null

Write-Host "Aktifkan mode demo..."
try { Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:$Port/api/demo?on=1" | Out-Null } catch {
    Write-Warning "Server belum jalan, lewati mode demo"
}

Write-Host "Pasang APK..."
& $Adb install -r $apk

Write-Host "Buka aplikasi..."
& $Adb shell am force-stop com.ombaharbangkit.tiktoklivereader
& $Adb shell am start -n com.ombaharbangkit.tiktoklivereader/.SplashActivity
Start-Sleep -Seconds 6

Write-Host "Screenshot splash/dashboard..."
& $Adb exec-out screencap -p > "$shots\01_dashboard.png"

Write-Host "Catatan: isi IP server lewat tombol globe di aplikasi: $ServerUrl"
Write-Host "Log aplikasi: adb logcat -s AndroidRuntime MainActivity"
