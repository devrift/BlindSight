#!/usr/bin/env python3
"""BlindSight WAV Asset Verifier.

Asserts canonical 16-bit PCM mono 22050 Hz RIFF/WAV format for LittleFS voice clips:
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
