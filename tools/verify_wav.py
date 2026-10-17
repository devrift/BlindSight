#!/usr/bin/env python3
"""BlindSight WAV Asset Verifier.

Asserts canonical 16-bit PCM mono 22050 Hz RIFF/WAV format and speech duration bounds:
- analysing.wav
- left.wav
- right.wav
- stop.wav
- careful.wav
- clear.wav
"""
from __future__ import annotations

import argparse
import sys
import wave
from pathlib import Path

REQUIRED_FILES = [
    "analysing.wav",
    "left.wav",
    "right.wav",
    "stop.wav",
    "careful.wav",
    "clear.wav",
]

EXPECTED_TEXT = {
    "analysing.wav": "Analysing",
    "left.wav": "Obstacle ahead. Move left.",
    "right.wav": "Obstacle ahead. Move right.",
    "stop.wav": "Stop. Obstacle very close ahead.",
    "careful.wav": "Object ahead. Proceed carefully.",
    "clear.wav": "Path clear.",
}

WORDS_PER_SECOND = 2.6
MIN_CLIP_SECONDS = 0.5
MAX_CLIP_SECONDS = 10.0


def min_seconds_for(text: str) -> float:
    return max(MIN_CLIP_SECONDS, len(text.split()) / WORDS_PER_SECOND * 0.75)


def verify_directory(data_dir: Path) -> bool:
    if not data_dir.is_dir():
        print(f"[FAIL] Data directory does not exist: {data_dir}", file=sys.stderr)
        return False

    all_ok = True
    print(f"Verifying LittleFS voice clips in {data_dir}...")

    for name in REQUIRED_FILES:
        target = data_dir / name
        if not target.is_file():
            print(f"  [FAIL] Missing required clip: {name}", file=sys.stderr)
            all_ok = False
            continue

        try:
            with wave.open(str(target), "rb") as wav:
                channels = wav.getnchannels()
                width = wav.getsampwidth()
                rate = wav.getframerate()
                frames = wav.getnframes()
                duration = frames / float(rate) if rate > 0 else 0.0

                errors = []
                if channels != 1:
                    errors.append(f"channels={channels} (expected 1 mono)")
                if width != 2:
                    errors.append(f"sample_width={width * 8}bit (expected 16-bit)")
                if rate != 22050:
                    errors.append(f"sample_rate={rate}Hz (expected 22050Hz)")
                if frames == 0:
                    errors.append("empty audio frames")

                expected_phrase = EXPECTED_TEXT.get(name, "")
                min_dur = min_seconds_for(expected_phrase)
                if duration < min_dur:
                    errors.append(
                        f"duration={duration:.2f}s is too short for '{expected_phrase}' (need >={min_dur:.2f}s)"
                    )
                if duration > MAX_CLIP_SECONDS:
                    errors.append(f"duration={duration:.2f}s exceeds {MAX_CLIP_SECONDS}s budget")

                if errors:
                    print(f"  [FAIL] {name}: {', '.join(errors)}", file=sys.stderr)
                    all_ok = False
                else:
                    print(f"  [OK]   {name:<14} 1-ch 16-bit 22050Hz | {frames:>5} samples ({duration:.2f}s)")
        except Exception as exc:
            print(f"  [FAIL] {name}: invalid WAV ({exc})", file=sys.stderr)
            all_ok = False

    return all_ok


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify BlindSight WAV files")
    parser.add_argument(
        "--dir",
        type=Path,
        default=Path(__file__).resolve().parent.parent / "firmware" / "esp32_main" / "data",
        help="Path to data folder",
    )
    args = parser.parse_args()

    success = verify_directory(args.dir)
    if success:
        print("\nAll LittleFS voice clips verified successfully.")
        return 0
    else:
        print("\nWAV verification failed.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
