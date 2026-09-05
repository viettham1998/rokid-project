<#
  Rokid demo — portable build/install helper (no Android Studio needed).

  Uses the portable toolchain under D:\rokid-project\toolchain that was set up
  once (JDK 17 + Android SDK + Gradle). Nothing is installed system-wide.

  USAGE (from any PowerShell, in any folder):
    .\tools\build.ps1                 # build both APKs
    .\tools\build.ps1 -Devices        # list connected adb devices
    .\tools\build.ps1 -InstallPhone   # build + install phone APK
    .\tools\build.ps1 -InstallGlasses # build + install glasses APK
    .\tools\build.ps1 -InstallGlasses -Serial <serial>   # pick a device
    .\tools\build.ps1 -Adb            # just print the adb.exe path
#>
param(
  [switch]$Devices,
  [switch]$InstallPhone,
  [switch]$InstallGlasses,
  [switch]$Adb,
  [string]$Serial
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$root = Split-Path -Parent $PSScriptRoot            # D:\rokid-project
$tc   = Join-Path $root 'toolchain'
$sdk  = Join-Path $tc 'android-sdk'

$env:JAVA_HOME = (Get-ChildItem (Join-Path $tc 'jdk17') -Directory |
                  Where-Object { $_.Name -like 'jdk-17*' } | Select-Object -First 1).FullName
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_HOME = $sdk
$gradle = Join-Path $tc 'gradle-8.7\bin\gradle.bat'
$adbExe    = Join-Path $sdk 'platform-tools\adb.exe'

$phoneProj   = Join-Path $root 'android-phone-app'
$glassesProj = Join-Path $root 'rokid-app'
$phoneApk    = Join-Path $phoneProj   'app\build\outputs\apk\debug\app-debug.apk'
$glassesApk  = Join-Path $glassesProj 'app\build\outputs\apk\debug\app-debug.apk'
$outDir      = Join-Path $root 'apk'

if ($Adb)     { Write-Host $adbExe; return }
if ($Devices) { & $adbExe devices; return }

function Build($proj, $label) {
  Write-Host "=== Building $label ===" -ForegroundColor Cyan
  & $gradle -p $proj assembleDebug --console=plain -q
  if ($LASTEXITCODE -ne 0) { throw "$label build failed" }
}

# Always build what we might install (or both if no install flag)
$buildPhone   = $InstallPhone   -or (-not $InstallGlasses)
$buildGlasses = $InstallGlasses -or (-not $InstallPhone)

if ($buildPhone)   { Build $phoneProj   'PHONE app' }
if ($buildGlasses) { Build $glassesProj 'GLASSES app' }

# Copy fresh APKs to D:\rokid-project\apk\ with clear names
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
if ($buildPhone   -and (Test-Path $phoneApk))   { Copy-Item $phoneApk   (Join-Path $outDir 'rokid-phone-debug.apk')   -Force }
if ($buildGlasses -and (Test-Path $glassesApk)) { Copy-Item $glassesApk (Join-Path $outDir 'rokid-glasses-debug.apk') -Force }

Write-Host "`n=== APKs ===" -ForegroundColor Green
Get-ChildItem $outDir -Filter *.apk | ForEach-Object {
  Write-Host ("  {0}  ({1} MB)" -f $_.Name, [math]::Round($_.Length/1MB,2))
}

$target = @(); if ($Serial) { $target = @('-s', $Serial) }
if ($InstallPhone) {
  Write-Host "`n=== Installing PHONE app ===" -ForegroundColor Cyan
  & $adbExe @target install -r $phoneApk
}
if ($InstallGlasses) {
  Write-Host "`n=== Installing GLASSES app ===" -ForegroundColor Cyan
  & $adbExe @target install -r $glassesApk
}
