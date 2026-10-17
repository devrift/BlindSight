#!/usr/bin/env python3
"""BlindSight LittleFS Voice Clip Generator.

Generates 16-bit PCM mono 22050 Hz WAV clips for ESP32 LittleFS offline fallback:
- analysing.wav
- left.wav
- right.wav
- stop.wav
- careful.wav
- clear.wav

Uses espeak-ng if available, or synthesizes harmonic auditory cues.
"""
from __future__ import annotations

import argparse
import math
import shutil
import struct
import subprocess
import tempfile
import wave
from pathlib import Path

CLIPS = {
    "analysing.wav": {
        "text": "Analysing.",
        "freqs": [(660, 0.15), (880, 0.25)],
    },
    "left.wav": {
        "text": "Move left.",
        "freqs": [(523, 0.18), (392, 0.22)],
    },
    "right.wav": {
        "text": "Move right.",
        "freqs": [(392, 0.18), (523, 0.22)],
    },
    "stop.wav": {
        "text": "Stop.",
        "freqs": [(880, 0.15), (0, 0.05), (880, 0.25)],
    },
    "careful.wav": {
        "text": "Caution.",
        "freqs": [(587, 0.2), (494, 0.25)],
    },
    "clear.wav": {
        "text": "Path clear.",
        "freqs": [(440, 0.12), (554, 0.12), (659, 0.25)],
    },
}


def synthesize_synthetic_clip(path: Path, tones: list[tuple[float, float]], sample_rate: int = 22050) -> None:
    """Synthesizes a 16-bit mono PCM WAV clip from frequency/duration pairs."""
    frames = bytearray()
    for freq, duration in tones:
        n_samples = int(sample_rate * duration)
        if freq <= 0:
            # Silence
            frames.extend(b"\x00\x00" * n_samples)
            continue

        for i in range(n_samples):
            t = float(i) / sample_rate
            # Harmonics for rich, pleasant auditory cue
            val_float = (
                0.60 * math.sin(2.0 * math.pi * freq * t)
                + 0.25 * math.sin(2.0 * math.pi * 2.0 * freq * t)
                + 0.15 * math.sin(2.0 * math.pi * 3.0 * freq * t)
            )
            # Attack / decay envelope (15ms ramp)
            ramp_len = int(sample_rate * 0.015)
            envelope = min(1.0, i / max(1, ramp_len), (n_samples - i) / max(1, ramp_len))
            sample = int(val_float * envelope * 22000.0)
            sample = max(-32768, min(32767, sample))
            frames.extend(struct.pack("<h", sample))

    with wave.open(str(path), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(sample_rate)
        wav.writeframes(frames)


def generate_clips(output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    espeak_bin = shutil.which("espeak-ng") or shutil.which("espeak")

    for filename, spec in CLIPS.items():
        target = output_dir / filename
        generated_via_espeak = False

        if espeak_bin:
            try:
                with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tf:
                    temp_path = Path(tf.name)
                res = subprocess.run(
                    [espeak_bin, "-v", "en", "-s", "155", "-w", str(temp_path), spec["text"]],
                    capture_output=True,
                    timeout=5,
                )
                if res.returncode == 0 and temp_path.stat().st_size > 44:
                    # Validate header & format
                    with wave.open(str(temp_path), "rb") as src:
                        if src.getnchannels() == 1 and src.getsampwidth() == 2 and src.getframerate() == 22050:
                            shutil.copy2(temp_path, target)
                            generated_via_espeak = True
                temp_path.unlink(missing_ok=True)
            except Exception:
                generated_via_espeak = False

        if not generated_via_espeak:
            synthesize_synthetic_clip(target, spec["freqs"], sample_rate=22050)

        print(f"Generated {target.name} ({target.stat().st_size} bytes)")


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate BlindSight voice clips")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path(__file__).resolve().parent.parent / "firmware" / "esp32_main" / "data",
        help="Target data folder for LittleFS WAV files",
    )
    args = parser.parse_args()
    generate_clips(args.output_dir)


if __name__ == "__main__":
    main()
