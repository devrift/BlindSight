#!/usr/bin/env python3
"""Assert that LittleFS voice clips are in the format the firmware requires.

The main ESP32's playFile() streams raw PCM bytes to an I2S DAC that initI2S()
clocked at 22050 Hz. It never re-clocks for a file, and it skips the first 44
bytes assuming a canonical RIFF header. A clip that is 44.1 kHz, stereo or
8-bit therefore plays as noise or at the wrong pitch - silently, on a device
used by blind people.

Required: RIFF/WAVE, PCM (format 1), 1 channel, 16 bits, 22050 Hz, and a data
chunk whose size matches the file length.

Usage:
    python3 tools/verify_wav.py                 # check firmware/esp32_main/data
    python3 tools/verify_wav.py path/a.wav ...  # check specific files

Exit code 0 when every file is valid, 1 otherwise.
"""
from __future__ import annotations

import struct
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
CLIP_DIR = REPO_ROOT / "firmware" / "esp32_main" / "data"

REQUIRED_RATE = 22050
REQUIRED_BITS = 16
REQUIRED_CHANNELS = 1

EXPECTED_CLIPS = ("analysing", "left", "right", "stop", "careful", "clear")

# The spoken text each clip must carry. Used for a duration sanity gate: a
# format-only check passes tone/beep placeholders that contain no speech at all,
# which is how a parallel remediation once "fixed" the silent fallback by
# committing 0.4 s harmonic tones and reporting B5 RESOLVED. At espeak's -s 155
# (~2.6 words/s) four words cannot fit in 0.4 s, so a per-phrase minimum derived
# from the word count catches that class of fake cheaply and deterministically.
EXPECTED_TEXT = {
    "analysing": "Analysing",
    "left": "Obstacle ahead. Move left.",
    "right": "Obstacle ahead. Move right.",
    "stop": "Stop. Obstacle very close ahead.",
    "careful": "Object ahead. Proceed carefully.",
    "clear": "Path clear.",
}
WORDS_PER_SECOND = 2.6
MIN_CLIP_SECONDS = 0.5
MAX_CLIP_SECONDS = 10.0


def min_seconds_for(text: str) -> float:
    return max(MIN_CLIP_SECONDS, len(text.split()) / WORDS_PER_SECOND * 0.75)


class WavError(Exception):
    pass


def check_wav(path: Path) -> dict:
    data = path.read_bytes()
    if len(data) < 44:
        raise WavError("file is smaller than a canonical WAV header")
    if data[0:4] != b"RIFF" or data[8:12] != b"WAVE":
        raise WavError("not a RIFF/WAVE file")

    offset = 12
    fmt = None
    data_size = None
    while offset + 8 <= len(data):
        chunk_id = data[offset:offset + 4]
        (chunk_size,) = struct.unpack_from("<I", data, offset + 4)
        body = offset + 8
        if chunk_id == b"fmt ":
            if chunk_size < 16:
                raise WavError("fmt chunk too small")
            audio_format, channels, rate, _byte_rate, _align, bits = struct.unpack_from("<HHIIHH", data, body)
            fmt = {
                "format": audio_format,
                "channels": channels,
                "rate": rate,
                "bits": bits,
            }
        elif chunk_id == b"data":
            data_size = chunk_size
        offset = body + chunk_size + (chunk_size & 1)

    if fmt is None:
        raise WavError("no fmt chunk")
    if data_size is None:
        raise WavError("no data chunk")
    if fmt["format"] != 1:
        raise WavError(f"audio format {fmt['format']} is not PCM (1); the firmware cannot decode compressed WAV")
    if fmt["channels"] != REQUIRED_CHANNELS:
        raise WavError(f"{fmt['channels']} channels; firmware requires mono")
    if fmt["bits"] != REQUIRED_BITS:
        raise WavError(f"{fmt['bits']}-bit; firmware requires 16-bit")
    if fmt["rate"] != REQUIRED_RATE:
        raise WavError(f"{fmt['rate']} Hz; firmware DAC is clocked at {REQUIRED_RATE} Hz")

    seconds = data_size / float(rate * channels * (bits // 8))
    return {
        "rate": fmt["rate"],
        "channels": fmt["channels"],
        "bits": fmt["bits"],
        "pcm_bytes": data_size,
        "seconds": seconds,
    }


def main(argv: list[str]) -> int:
    if argv:
        paths = [Path(a) for a in argv]
    else:
        paths = sorted(CLIP_DIR.glob("*.wav"))
        missing = [n for n in EXPECTED_CLIPS if not (CLIP_DIR / f"{n}.wav").is_file()]
        if missing:
            print(
                f"ERROR: missing clips in {CLIP_DIR}: {', '.join(m + '.wav' for m in missing)}\n"
                "Run firmware/esp32_main/data/generate_clips.sh to create them; the\n"
                "firmware's offline fallback is silent without them.",
                file=sys.stderr,
            )
            return 1

    if not paths:
        print(f"ERROR: no .wav files found in {CLIP_DIR}", file=sys.stderr)
        return 1

    failures = 0
    for path in paths:
        try:
            info = check_wav(path)
            text = EXPECTED_TEXT.get(path.stem)
            if text is not None:
                lo = min_seconds_for(text)
                if info["seconds"] < lo:
                    raise WavError(
                        f"{info['seconds']:.2f}s is too short to contain \"{text}\" "
                        f"(need >={lo:.2f}s); a tone/beep placeholder is not speech"
                    )
                if info["seconds"] > MAX_CLIP_SECONDS:
                    raise WavError(f"{info['seconds']:.2f}s exceeds the {MAX_CLIP_SECONDS}s clip budget")
            print(
                f"OK   {path}  {info['rate']}Hz {info['bits']}-bit {info['channels']}ch "
                f"{info['seconds']:.2f}s ({info['pcm_bytes']} bytes PCM)"
            )
        except OSError as error:
            failures += 1
            print(f"FAIL {path}: {error}", file=sys.stderr)
        except WavError as error:
            failures += 1
            print(f"FAIL {path}: {error}", file=sys.stderr)

    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
