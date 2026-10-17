"""LittleFS fallback voice clips (audit B5).

The main ESP32's offline ultrasonic path plays these clips from LittleFS.
playFile() streams raw PCM to a DAC clocked at 22050 Hz and never re-clocks, so
the format is a hard requirement: RIFF/WAVE, PCM, mono, 16-bit, 22050 Hz.

A fresh clone has no clips (they are generated, not authored), so the "nothing
there" case skips with instructions rather than failing CI. A *partial* set is a
footgun - some fallback messages would speak and some would be silent - so that
fails.
"""
from __future__ import annotations

import math
import struct
import sys
import wave
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "tools"))

import verify_wav

CLIP_DIR = verify_wav.CLIP_DIR


@pytest.fixture(scope="module")
def clips() -> list[Path]:
    present = sorted(CLIP_DIR.glob("*.wav")) if CLIP_DIR.is_dir() else []
    missing = [name for name in verify_wav.EXPECTED_CLIPS if not (CLIP_DIR / f"{name}.wav").is_file()]
    if not present:
        pytest.skip(
            "no LittleFS voice clips generated yet; run "
            "firmware/esp32_main/data/generate_clips.sh on a machine with espeak-ng. "
            "Until then the firmware's offline fallback is silent."
        )
    assert not missing, (
        f"partial clip set: missing {missing}. Either generate all of them with "
        "firmware/esp32_main/data/generate_clips.sh or remove the partial clips."
    )
    return present


def test_clips_have_the_required_format(clips):
    bad = []
    for path in clips:
        try:
            verify_wav.check_wav(path)
        except verify_wav.WavError as error:
            bad.append(f"{path.name}: {error}")
    assert not bad, "clips not in the format the firmware requires:\n" + "\n".join(bad)


def test_clip_names_match_firmware_calls(clips):
    names = {path.stem for path in clips}
    expected = set(verify_wav.EXPECTED_CLIPS)
    assert expected <= names, f"firmware calls playFile() for clips that do not exist: {expected - names}"


# ---------------------------------------------------------------------------
# Content sanity: a format-only gate is not enough.
#
# A parallel remediation of this repo "fixed" the silent fallback by committing
# 0.4 s harmonic tones (correct 16-bit/mono/22050 Hz format, no speech) and
# reported audit B5 as RESOLVED. The duration rule below is what catches that.
# ---------------------------------------------------------------------------
def _write_tone(path: Path, samples: int, rate: int = verify_wav.REQUIRED_RATE) -> None:
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(rate)
        frames = bytearray()
        for i in range(samples):
            t = i / rate
            env = min(1.0, i / (rate * 0.05), (samples - i) / (rate * 0.05))
            value = int((math.sin(2 * math.pi * 440 * t) * 0.6 + math.sin(4 * math.pi * 440 * t) * 0.3) * env * 24000)
            frames.extend(struct.pack("<h", value))
        handle.writeframes(bytes(frames))


def test_tone_placeholders_are_rejected_as_too_short_for_speech(tmp_path):
    for name, text in verify_wav.EXPECTED_TEXT.items():
        clip = tmp_path / f"{name}.wav"
        _write_tone(clip, int(0.40 * verify_wav.REQUIRED_RATE))
        info = verify_wav.check_wav(clip)  # format is fine - that is the trap
        assert info["seconds"] < verify_wav.min_seconds_for(text), name


def test_duration_rule_accepts_plausible_speech_lengths():
    # espeak at -s 155 is ~2.6 words/s plus a little lead-in/out padding; a real
    # rendering of each phrase clears the lenient 0.75x floor min_seconds_for
    # uses, so genuine clips are never rejected by the sanity gate.
    for text in verify_wav.EXPECTED_TEXT.values():
        words = len(text.split())
        plausible = words / 2.6 + 0.15
        assert plausible >= verify_wav.min_seconds_for(text), text


def test_min_seconds_scales_with_phrase_length():
    assert verify_wav.min_seconds_for("Analysing") < verify_wav.min_seconds_for("Stop. Obstacle very close ahead.")
    assert verify_wav.min_seconds_for("") == verify_wav.MIN_CLIP_SECONDS


# ---------------------------------------------------------------------------
# Spectral gate: the duration rule has a hole a tone generator fits through.
# The parallel remediation's generator (tools/generate_clips.py on origin/main,
# slated for adoption in the merge) degrades to two-partial harmonic "cues" of
# 0.8-1.3 s when neither espeak-ng nor Windows SAPI is available - long enough
# to clear every per-phrase floor - so a Pi without espeak-ng would regenerate
# fake clips that pass format and duration checks. Speech spreads energy over
# hundreds of FFT bins; a tone piles ~100% of it into its few partials.
# ---------------------------------------------------------------------------
def test_plausible_length_tone_is_rejected_by_spectral_gate(tmp_path):
    clip = tmp_path / "left.wav"
    duration = 1.6  # clears the 1.15 s floor for "Obstacle ahead. Move left."
    assert duration >= verify_wav.min_seconds_for(verify_wav.EXPECTED_TEXT["left"])
    _write_tone(clip, int(duration * verify_wav.REQUIRED_RATE))
    info = verify_wav.check_wav(clip)
    assert verify_wav.top3_energy_ratio(info["pcm"]) > verify_wav.TONE_TOP3_ENERGY
    assert verify_wav.main([str(clip)]) == 1  # end-to-end: the CLI must fail it


def test_broadband_signal_passes_spectral_gate(tmp_path):
    # Positive control: a noisy many-component signal must not trip the gate,
    # otherwise genuine-but-rough speech (or espeak output) would be rejected.
    rate = verify_wav.REQUIRED_RATE
    samples = int(1.3 * rate)
    partials = [(183.0, 0.20), (311.0, 0.15), (457.0, 0.12), (691.0, 0.10), (934.0, 0.08)]
    state = 12345
    with wave.open(str(clip := tmp_path / "left.wav"), "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(rate)
        frames = bytearray()
        for i in range(samples):
            t = i / rate
            value = sum(amp * math.sin(2 * math.pi * freq * t) for freq, amp in partials)
            state = (state * 1103515245 + 12345) % 2**31
            value += 0.45 * (state / 2**31 - 0.5)
            env = min(1.0, i / (rate * 0.05), (samples - i) / (rate * 0.05))
            frames.extend(struct.pack("<h", int(value * env * 12000)))
        handle.writeframes(bytes(frames))
    info = verify_wav.check_wav(clip)
    assert verify_wav.top3_energy_ratio(info["pcm"]) < verify_wav.TONE_TOP3_ENERGY
    assert verify_wav.main([str(clip)]) == 0
