"""Shared pytest fixtures.

Hermeticity: edge_server/server.py builds a module-level app on import and
creates its data directories, so the runtime directory is redirected to a
temp path *before* the import and the background sweeper is disabled.
"""
from __future__ import annotations

import os
import sys
import tempfile
from pathlib import Path

import pytest

REPO_ROOT = Path(__file__).resolve().parent.parent
EDGE_SERVER = REPO_ROOT / "edge_server"

os.environ.setdefault("BLINDSIGHT_DISABLE_SWEEPER", "1")
os.environ["BLINDSIGHT_DATA"] = tempfile.mkdtemp(prefix="blindsight-test-")
os.environ.pop("BLINDSIGHT_MODEL_PATH", None)

if str(EDGE_SERVER) not in sys.path:
    sys.path.insert(0, str(EDGE_SERVER))


@pytest.fixture(scope="session")
def repo_root() -> Path:
    return REPO_ROOT


@pytest.fixture(scope="session")
def edge_server_dir() -> Path:
    return EDGE_SERVER


@pytest.fixture()
def client():
    import server

    return server.app.test_client()


@pytest.fixture()
def jpeg_bytes() -> bytes:
    import io

    from PIL import Image

    buf = io.BytesIO()
    Image.new("RGB", (320, 240), (90, 120, 60)).save(buf, "JPEG")
    return buf.getvalue()
