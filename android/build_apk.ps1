# Build APK memakai Gradle dari cache wrapper (tanpa gradlew.bat, karena
# proyek ini memakai gradle.properties di root proyek).
$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME = "C:\Android\Sdk"

$gradleHome = Join-Path $env:USERPROFILE ".gradle"
$launcher = Get-ChildItem (Join-Path $gradleHome "wrapper\dists") -Recurse -Filter "gradle-launcher-*.jar" |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $launcher) { throw "jar gradle-launcher tidak ditemukan di $gradleHome" }
Write-Host "Gradle: $($launcher.Name)"

$cp = $launcher.FullName
$root = (Resolve-Path (Join-Path $PSScriptRoot ".")).Path
Write-Host "Proyek: $root"

$args = @(
    "-Xmx2048m", "-Dfile.encoding=UTF-8",
    "-cp", $cp,
    "org.gradle.launcher.GradleMain",
    "-p", $root,
    "assembleDebug", "assembleRelease", "--console=plain", "--no-daemon"
)
& "$env:JAVA_HOME\bin\java.exe" @args
exit $LASTEXITCODE
