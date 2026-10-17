#!/usr/bin/env python3
"""BlindSight LittleFS Voice Clip Generator.

Generates 16-bit PCM mono 22050 Hz WAV clips for ESP32 LittleFS offline fallback:
- analysing.wav ("Analysing")
- left.wav ("Obstacle ahead. Move left.")
- right.wav ("Obstacle ahead. Move right.")
- stop.wav ("Stop. Obstacle very close ahead.")
- careful.wav ("Object ahead. Proceed carefully.")
- clear.wav ("Path clear.")

Uses espeak-ng if available, Windows SAPI TTS if on Windows, or synthesizes harmonic cues.
"""
from __future__ import annotations

import argparse
import math
import shutil
import struct
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

CLIPS = {
    "analysing.wav": {
        "text": "Analysing",
        "freqs": [(660, 0.3), (880, 0.5)],
    },
    "left.wav": {
        "text": "Obstacle ahead. Move left.",
        "freqs": [(523, 0.5), (392, 0.8)],
    },
    "right.wav": {
        "text": "Obstacle ahead. Move right.",
        "freqs": [(392, 0.5), (523, 0.8)],
    },
    "stop.wav": {
        "text": "Stop. Obstacle very close ahead.",
        "freqs": [(880, 0.5), (0, 0.1), (880, 0.9)],
    },
    "careful.wav": {
        "text": "Object ahead. Proceed carefully.",
        "freqs": [(587, 0.5), (494, 0.8)],
    },
    "clear.wav": {
        "text": "Path clear.",
        "freqs": [(440, 0.3), (554, 0.3), (659, 0.6)],
    },
}


def synthesize_windows_sapi(path: Path, text: str) -> bool:
    """Uses Windows SAPI speech synthesis via tools/tts_win.ps1 to generate genuine speech WAV."""
    if sys.platform != "win32":
        return False
    ps_script = Path(__file__).resolve().parent / "tts_win.ps1"
    if not ps_script.is_file():
        return False
    try:
        res = subprocess.run(
            [
                "powershell",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                str(ps_script),
                "-text",
                text,
                "-outPath",
                str(path.resolve()),
            ],
            capture_output=True,
                check=False,  # a missing TTS backend is a fallback, not a crash
            timeout=15,
        )
        if res.returncode == 0 and path.is_file() and path.stat().st_size > 44:
            with wave.open(str(path), "rb") as src:
                if src.getnchannels() == 1 and src.getsampwidth() == 2 and src.getframerate() == 22050:
                    return True
    except Exception:
        pass
    return False


def synthesize_synthetic_clip(path: Path, tones: list[tuple[float, float]], sample_rate: int = 22050) -> None:
    """Synthesizes a 16-bit mono PCM WAV clip from frequency/duration pairs."""
    frames = bytearray()
    for freq, duration in tones:
        n_samples = int(sample_rate * duration)
        if freq <= 0:
            frames.extend(b"\x00\x00" * n_samples)
            continue

        for i in range(n_samples):
            t = float(i) / sample_rate
            val_float = (
                0.60 * math.sin(2.0 * math.pi * freq * t)
                + 0.25 * math.sin(2.0 * math.pi * 2.0 * freq * t)
                + 0.15 * math.sin(2.0 * math.pi * 3.0 * freq * t)
            )
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
        generated_speech = False

        if espeak_bin:
            try:
                with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tf:
                    temp_path = Path(tf.name)
                res = subprocess.run(
                    [espeak_bin, "-v", "en", "-s", "155", "-w", str(temp_path), spec["text"]],
                    capture_output=True,
                check=False,  # a missing TTS backend is a fallback, not a crash
                    timeout=5,
                )
                if res.returncode == 0 and temp_path.stat().st_size > 44:
                    with wave.open(str(temp_path), "rb") as src:
                        if src.getnchannels() == 1 and src.getsampwidth() == 2 and src.getframerate() == 22050:
                            shutil.copy2(temp_path, target)
                            generated_speech = True
                temp_path.unlink(missing_ok=True)
            except Exception:
                generated_speech = False

        if not generated_speech and sys.platform == "win32":
            generated_speech = synthesize_windows_sapi(target, spec["text"])

        if not generated_speech:
            synthesize_synthetic_clip(target, spec["freqs"], sample_rate=22050)

        duration = 0.0
        try:
            with wave.open(str(target), "rb") as w:
                duration = w.getnframes() / float(w.getframerate())
        except Exception:
            pass
        print(f"Generated {target.name} ({target.stat().st_size} bytes, {duration:.2f}s)")


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
