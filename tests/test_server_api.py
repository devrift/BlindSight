from __future__ import annotations

import io
import sys
from pathlib import Path

import pytest

ROOT_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT_DIR / "edge_server"))

import server


@pytest.fixture
def client():
    app = server.create_app()
    app.config["TESTING"] = True
    with app.test_client() as client:
        yield client


def test_health_endpoint(client):
    res = client.get("/health")
    assert res.status_code in (200, 503)
    data = res.get_json()
    assert "status" in data
    assert "vision_script_ok" in data
    assert "model_path" in data
    assert "espeak_ok" in data
    assert "last_vision_success" in data
    assert data["vision_script_ok"] is True


def test_health_reports_error_when_model_missing(client, monkeypatch):
    monkeypatch.setenv("BLINDSIGHT_MODEL_PATH", "nonexistent_model.onnx")
    res = client.get("/health")
    data = res.get_json()
    assert data["status"] in ("degraded", "error")
    assert data["model_path"] is None


def test_latest_frame_404_when_empty(client, tmp_path, monkeypatch):
    # Ensure fresh empty directories
    test_settings = server.Settings(
        data_dir=tmp_path / "runtime",
        uploads_dir=tmp_path / "uploads",
        models_dir=ROOT_DIR / "edge_server" / "models",
        vision_script=ROOT_DIR / "edge_server" / "vision_ai.py",
        vision_command=["python3", str(ROOT_DIR / "edge_server" / "vision_ai.py")],
        espeak_binary="espeak-ng",
    )
    test_app = server.create_app(test_settings)
    test_app.config["TESTING"] = True
    c = test_app.test_client()

    res = c.get("/api/latest-frame")
    assert res.status_code == 404
    data = res.get_json()
    assert data.get("error") == "no frame captured yet"


def test_sw_js_served_at_root(client):
    res = client.get("/sw.js")
    assert res.status_code == 200
    assert "javascript" in res.content_type.lower()
    assert res.headers.get("Service-Worker-Allowed") == "/"


def test_api_chat_missing_prompt(client):
    res = client.post("/api/chat", json={})
    assert res.status_code == 400
    assert "prompt" in res.get_json().get("error", "").lower()


def test_api_chat_rate_limiting(client):
    # Send requests to trigger rate limit (max 30 / min)
    for _ in range(30):
        res = client.post("/api/chat", json={"prompt": "hello"}, environ_base={"REMOTE_ADDR": "192.168.1.99"})
        assert res.status_code != 429
    # 31st request from same IP should get 429
    res = client.post("/api/chat", json={"prompt": "hello"}, environ_base={"REMOTE_ADDR": "192.168.1.99"})
    assert res.status_code == 429
    assert "rate limit" in res.get_json().get("error", "").lower()


def test_api_chat_auth_token(monkeypatch, tmp_path):
    monkeypatch.setenv("BLINDSIGHT_API_TOKEN", "secret-test-token")
    test_app = server.create_app()
    test_app.config["TESTING"] = True
    c = test_app.test_client()

    # Unauthorized
    res = c.post("/api/chat", json={"prompt": "hello"})
    assert res.status_code == 401

    # Authorized
    res = c.post(
        "/api/chat",
        json={"prompt": "hello"},
        headers={"Authorization": "Bearer secret-test-token"},
    )
    # Token passed; returns assistant response or fallback
    assert res.status_code in (200, 503)


def test_analyse_missing_file(client):
    res = client.post("/analyse", data={})
    assert res.status_code == 400
    assert "missing" in res.get_json().get("error", "")


def test_analyse_corrupt_jpeg(client):
    data = {"image": (io.BytesIO(b"not a valid jpeg"), "image.jpg")}
    res = client.post("/analyse", data=data, content_type="multipart/form-data")
    assert res.status_code == 400
    assert "invalid" in res.get_json().get("error", "").lower()


def test_audio_invalid_id(client):
    res = client.get("/audio/invalid-id")
    assert res.status_code == 404
