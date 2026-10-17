from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
VISION_SCRIPT = ROOT_DIR / "edge_server" / "vision_ai.py"


def test_missing_image_fails_closed():
    """Missing image must exit non-zero (1), with empty stdout and diagnostic on stderr."""
    res = subprocess.run(
        [sys.executable, str(VISION_SCRIPT), "nonexistent_frame.jpg"],
        capture_output=True,
        text=True,
    )
    assert res.returncode == 1
    assert res.stdout.strip() == ""
    assert "error" in res.stderr.lower() or "not found" in res.stderr.lower()


def test_missing_model_fails_closed(tmp_path: Path):
    """Missing model must exit non-zero (1), with empty stdout and diagnostic on stderr."""
    dummy_img = tmp_path / "dummy.jpg"
    # Create a tiny 1x1 JPEG
    from PIL import Image
    Image.new("RGB", (100, 100)).save(dummy_img, format="JPEG")

    env = dict(os.environ)
    env["BLINDSIGHT_MODEL_PATH"] = str(tmp_path / "nonexistent.onnx")

    res = subprocess.run(
        [sys.executable, str(VISION_SCRIPT), str(dummy_img)],
        capture_output=True,
        text=True,
        env=env,
    )
    assert res.returncode == 1
    assert res.stdout.strip() == ""
    assert "not found" in res.stderr.lower() or "nonexistent.onnx" in res.stderr.lower()
