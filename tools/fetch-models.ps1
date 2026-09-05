<#
  Downloads the ML models into the phone app's assets.
  The Vosk model (~40MB) is gitignored, so run this once after cloning.
  The EfficientDet model is small and committed, but re-fetched here if missing.
#>
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$ProgressPreference = 'SilentlyContinue'

$root = Split-Path -Parent $PSScriptRoot
$assets = Join-Path $root 'android-phone-app\app\src\main\assets'
New-Item -ItemType Directory -Force -Path $assets | Out-Null

# --- Vosk small English model ---
$voskDir = Join-Path $assets 'vosk-model-small-en-us-0.15'
if (-not (Test-Path $voskDir)) {
    $zip = Join-Path $env:TEMP 'vosk-model-small-en-us.zip'
    Write-Host 'Downloading Vosk model (~40MB)...'
    Invoke-WebRequest -Uri 'https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip' -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $assets -Force
    Remove-Item $zip -Force
    Write-Host "Vosk model -> $voskDir"
} else {
    Write-Host 'Vosk model already present.'
}

# --- EfficientDet-Lite0 object detector ---
$det = Join-Path $assets 'efficientdet_lite0.tflite'
if (-not (Test-Path $det)) {
    Write-Host 'Downloading EfficientDet-Lite0...'
    Invoke-WebRequest -Uri 'https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/latest/efficientdet_lite0.tflite' -OutFile $det
    Write-Host "Detector -> $det"
} else {
    Write-Host 'Detector model already present.'
}
Write-Host 'Done.'
