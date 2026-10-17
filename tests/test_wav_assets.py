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

import sys
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
