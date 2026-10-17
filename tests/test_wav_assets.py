from __future__ import annotations

import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT_DIR / "tools"))

from verify_wav import REQUIRED_FILES, verify_directory


def test_littlefs_wav_assets():
    data_dir = ROOT_DIR / "firmware" / "esp32_main" / "data"
    assert data_dir.is_dir(), "firmware/esp32_main/data directory must exist"

    # All 6 required files must exist
    for name in REQUIRED_FILES:
        assert (data_dir / name).is_file(), f"Missing required voice clip: {name}"

    # Verifier must pass all audio format assertions
    assert verify_directory(data_dir) is True
