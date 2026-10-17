"""The safety contract for the edge vision path (audit B1).

vision_ai.py must NEVER print a navigation sentence when it did not actually
run the model. A previous revision of main() printed
"Path ahead appears clear." and exited 0 when the image was missing, when no
ONNX model was found, and when inference raised - telling a blind user the
path was safe when nothing had been observed. stdout is reserved for real
observations; every failure is stderr + non-zero exit so the gateway speaks its
safe fallback instead.
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

import pytest

NAVIGATION_MARKERS = (
    "Path ahead appears clear.",
    "Path clear",
    "Warning:",
    "Caution",
    "Obstacle",
    "Move left",
    "Move right",
    "Proceed carefully",
)


def run_vision(vision_script: Path, *args: str, env_extra: dict | None = None) -> subprocess.CompletedProcess:
    import os

    env = dict(os.environ)
    env.pop("BLINDSIGHT_MODEL_PATH", None)
    if env_extra:
        env.update(env_extra)
    return subprocess.run(
        [sys.executable, str(vision_script), *args],
        capture_output=True,
        text=True,
        timeout=60,
        env=env,
        check=False,  # the exit code IS the assertion in these tests
    )


@pytest.fixture(scope="module")
def vision_script(edge_server_dir: Path) -> Path:
    return edge_server_dir / "vision_ai.py"


def test_missing_image_fails_closed(vision_script, tmp_path):
    result = run_vision(vision_script, str(tmp_path / "nope.jpg"))
    assert result.returncode != 0, f"expected non-zero exit, got {result.returncode}"
    assert result.stdout.strip() == "", f"stdout must be empty on failure, got: {result.stdout!r}"
    assert "image not found" in result.stderr


def test_missing_model_fails_closed(vision_script, tmp_path, jpeg_bytes):
    image = tmp_path / "frame.jpg"
    image.write_bytes(jpeg_bytes)
    result = run_vision(
        vision_script,
        str(image),
        env_extra={"BLINDSIGHT_MODEL_PATH": str(tmp_path / "absent.onnx")},
    )
    assert result.returncode != 0
    assert result.stdout.strip() == ""
    assert "not found" in result.stderr


def test_unreadable_model_fails_closed(vision_script, tmp_path, jpeg_bytes):
    """A corrupt/unloadable model must not degrade into a navigation sentence."""
    image = tmp_path / "frame.jpg"
    image.write_bytes(jpeg_bytes)
    fake = tmp_path / "fake.onnx"
    fake.write_bytes(b"definitely not an ONNX model")
    result = run_vision(vision_script, str(image), env_extra={"BLINDSIGHT_MODEL_PATH": str(fake)})
    assert result.returncode != 0
    assert result.stdout.strip() == "", f"stdout must be empty, got: {result.stdout!r}"
    for marker in NAVIGATION_MARKERS:
        assert marker not in result.stdout


def test_corrupt_image_fails_closed(vision_script, tmp_path):
    bad = tmp_path / "bad.jpg"
    bad.write_bytes(b"\xff\xd8\xff this is not really a jpeg")
    fake = tmp_path / "fake.onnx"
    fake.write_bytes(b"not a model")
    result = run_vision(vision_script, str(bad), env_extra={"BLINDSIGHT_MODEL_PATH": str(fake)})
    assert result.returncode != 0
    assert result.stdout.strip() == ""


def test_directory_as_image_fails_closed(vision_script, tmp_path):
    result = run_vision(vision_script, str(tmp_path))
    assert result.returncode != 0
    assert result.stdout.strip() == ""


def test_module_docstring_documents_fail_closed(vision_script):
    """Guard the intent, not just the behaviour: the contract must stay written down."""
    source = vision_script.read_text(encoding="utf-8")
    assert "exits non-zero" in source, "the fail-closed contract vanished from the docstring"
    assert "SAFETY CONTRACT" in source
