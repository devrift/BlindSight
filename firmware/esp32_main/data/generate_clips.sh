#!/usr/bin/env bash
# Generate the LittleFS fallback voice clips for the main ESP32.
#
# The firmware's offline ultrasonic path plays these from LittleFS. Without them
# every playFile() call silently does nothing and the documented safety fallback
# is mute, so this directory is part of the source tree (un-ignored in .gitignore).
#
# MANDATORY FORMAT: 16-bit PCM, MONO, 22050 Hz RIFF/WAV.
#   playFile() does not call i2s_set_sample_rates() for streamed audio and the
#   DAC is clocked at 22050 by initI2S(); a clip at any other rate plays at the
#   wrong pitch and speed. espeak-ng writes exactly 22050 Hz 16-bit mono by
#   default, which is why it is the generator of record.
#
# Usage:
#   ./firmware/esp32_main/data/generate_clips.sh
#
# Requires espeak-ng. The script verifies every clip afterwards with
# tools/verify_wav.py and exits non-zero if any clip does not match the
# mandatory format - do not upload clips this script rejected.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"
ESPEAK="${ESPEAK_BINARY:-espeak-ng}"

if ! command -v "$ESPEAK" >/dev/null 2>&1; then
  echo "ERROR: $ESPEAK not found on PATH." >&2
  echo "Install it (sudo apt install espeak-ng) or set ESPEAK_BINARY to an" >&2
  echo "equivalent that writes 22050 Hz 16-bit mono WAV, e.g. espeak." >&2
  exit 1
fi

generate() {
  local name="$1"; shift
  local text="$*"
  "$ESPEAK" -v en -s 155 -w "$HERE/$name.wav" "$text"
  echo "generated $name.wav"
}

generate analysing  "Analysing"
generate left       "Obstacle ahead. Move left."
generate right      "Obstacle ahead. Move right."
generate stop       "Stop. Obstacle very close ahead."
generate careful    "Object ahead. Proceed carefully."
generate clear      "Path clear."

echo
echo "Verifying format (16-bit PCM, mono, 22050 Hz)..."
python3 "$ROOT/tools/verify_wav.py" "$HERE"/*.wav
echo
echo "OK. Next: PlatformIO -> Ctrl+Shift+P -> 'PlatformIO: Upload Filesystem Image'"
