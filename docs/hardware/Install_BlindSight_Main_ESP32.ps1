# BlindSight Main ESP32 one-click installer/uploader (no-terminal path) — v1.5
#
# Run: Right-click -> Run with PowerShell, or:
#   powershell -ExecutionPolicy Bypass -File .\Install_BlindSight_Main_ESP32.ps1
#
# v1.5 CHANGE: this script no longer embeds a separate copy of the sketch and
# no longer asks for your Wi-Fi password or the Pi IP. It uploads the canonical
# firmware/esp32_main/BlindSight_Main_ESP32.ino, which uses the tzapu
# WiFiManager captive portal. After uploading, you join the "BlindSight-Setup"
# Wi-Fi AP on your phone and enter the venue Wi-Fi + edge-server IP there.
# That means moving between exhibits never requires running this script again.
# See firmware/FIRMWARE_VARIANTS.md.
$ErrorActionPreference = 'Stop'
$Port = 'COM4'
$BoardUrl = 'https://espressif.github.io/arduino-esp32/package_esp32_index.json'

function Invoke-Cli {
  param([string[]]$Arguments)
  & $cliPath @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "arduino-cli failed (exit $LASTEXITCODE): arduino-cli $($Arguments -join ' ')"
  }
}

Write-Host "BlindSight Main ESP32 uploader (v1.5 — WiFiManager portal)" -ForegroundColor Cyan
Write-Host "This installs Arduino CLI + ESP32 support + WiFiManager, compiles, and uploads to $Port." -ForegroundColor Yellow
Write-Host "You will NOT be asked for a Wi-Fi password here. After upload, configure the" -ForegroundColor Yellow
Write-Host "device from your phone via the 'BlindSight-Setup' Wi-Fi network." -ForegroundColor Yellow

# Optional: bake in the camera pairing MAC at compile time. You can also leave
# the default and pair later; the MAC is the only compile-time hardware setting.
$camMac = Read-Host 'Enter the CAM MAC from the camera serial monitor (e.g. 3C,71,BF,12,34,56) or press Enter to skip'
$macDefine = ''
if (-not [string]::IsNullOrWhiteSpace($camMac)) {
  $macBytes = ($camMac -split '[,\s:]+') | Where-Object { $_ -ne '' }
  if ($macBytes.Count -ne 6) { throw 'Camera MAC must be exactly six hex bytes.' }
  $macLiteral = '{' + (($macBytes | ForEach-Object { '0x' + $_.PadLeft(2, '0') }) -join ',') + '}'
  $macDefine = "#define BLINDSIGHT_CAMERA_MAC $macLiteral"
}

# Locate the canonical sketch relative to this script (docs/hardware -> repo root).
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$canonical = Join-Path $repoRoot 'firmware\esp32_main\BlindSight_Main_ESP32.ino'
if (-not (Test-Path $canonical)) {
  throw "Cannot find canonical sketch at $canonical. Run this script from inside the BlindSight repo."
}

# Find Arduino CLI; install it via winget if it is not already available.
$cliCmd = Get-Command arduino-cli -ErrorAction SilentlyContinue
if (-not $cliCmd) {
  Write-Host 'Installing Arduino CLI using winget. Approve prompts if Windows asks.' -ForegroundColor Yellow
  winget install --id ArduinoSA.CLI --exact --accept-package-agreements --accept-source-agreements
  if ($LASTEXITCODE -ne 0) { throw "winget install failed (exit $LASTEXITCODE)." }
  $possible = @(
    "$env:LOCALAPPDATA\Programs\Arduino CLI\arduino-cli.exe",
    "$env:ProgramFiles\Arduino CLI\arduino-cli.exe",
    "$env:USERPROFILE\AppData\Local\Microsoft\WinGet\Packages"
  )
  $cliPath = $null
  foreach ($p in $possible[0..1]) { if (Test-Path $p) { $cliPath = $p; break } }
  if (-not $cliPath -and (Test-Path $possible[2])) {
    $cliPath = (Get-ChildItem $possible[2] -Filter arduino-cli.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
  }
  if (-not $cliPath) { $cliCmd = Get-Command arduino-cli -ErrorAction SilentlyContinue; if ($cliCmd) { $cliPath = $cliCmd.Source } }
  if (-not $cliPath) { throw 'Arduino CLI was installed but was not found. Close PowerShell, reopen it, then run this file again.' }
} else { $cliPath = $cliCmd.Source }
Write-Host "Using: $cliPath" -ForegroundColor Green

# Build a temporary sketch folder named after the .ino (Arduino CLI requirement).
$work = Join-Path ([System.IO.Path]::GetTempPath()) ("BlindSight_Main_ESP32_" + [System.Guid]::NewGuid().ToString("N"))
$sketchDir = Join-Path $work 'BlindSight_Main_ESP32'
New-Item -ItemType Directory -Force -Path $sketchDir | Out-Null
$ino = Join-Path $sketchDir 'BlindSight_Main_ESP32.ino'
Copy-Item -LiteralPath $canonical -Destination $ino -Force
if ($macDefine -ne '') {
  Set-Content -Path (Join-Path $sketchDir 'secrets.h') -Value $macDefine -Encoding UTF8
}

Write-Host 'Installing ESP32 board package (first time can take several minutes)...' -ForegroundColor Yellow
Invoke-Cli @('core', 'update-index', '--additional-urls', $BoardUrl)
Invoke-Cli @('core', 'install', 'esp32:esp32', '--additional-urls', $BoardUrl)

Write-Host 'Installing WiFiManager library (tzapu)...' -ForegroundColor Yellow
Invoke-Cli @('lib', 'install', 'WiFiManager')

Write-Host 'Compiling...' -ForegroundColor Yellow
Invoke-Cli @('compile', '--fqbn', 'esp32:esp32:esp32', $sketchDir)
Write-Host "Uploading to $Port..." -ForegroundColor Yellow
Invoke-Cli @('upload', '-p', $Port, '--fqbn', 'esp32:esp32:esp32', $sketchDir)

Write-Host ''
Write-Host 'SUCCESS: Main ESP32 firmware uploaded.' -ForegroundColor Green
Write-Host 'NEXT (zero-reflash setup):' -ForegroundColor Cyan
Write-Host '  1. Power the ESP32. If it cannot reach the saved network/server it opens' -ForegroundColor Cyan
Write-Host '     a Wi-Fi AP called "BlindSight-Setup" (or hold the reflex button 3 s).' -ForegroundColor Cyan
Write-Host '  2. On your phone join "BlindSight-Setup", open http://192.168.4.1,' -ForegroundColor Cyan
Write-Host '     pick the venue Wi-Fi, and enter the Edge Server IP + port printed' -ForegroundColor Cyan
Write-Host '     in the edge server terminal.' -ForegroundColor Cyan
Write-Host '  3. Open a serial monitor at 115200 baud and copy the printed MAIN MAC' -ForegroundColor Cyan
Write-Host '     into the ESP32-CAM sketch pairing MAC.' -ForegroundColor Cyan
Write-Host 'Note: upload the LittleFS WAV voice clips separately for offline speech.' -ForegroundColor Yellow

# Clean up the temporary sketch (no secrets are embedded anymore, but tidy up).
Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
Read-Host 'Press Enter to close'
