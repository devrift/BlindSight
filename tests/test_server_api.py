"""Gateway API contracts (audit B2, B3, C11, D2-of-audit).

Run with the sandbox's real (broken) environment: no ONNX model and no
espeak-ng. The point is that the gateway reports that honestly instead of
claiming a working vision pipeline or serving a placeholder as a live frame.
"""
from __future__ import annotations



def test_health_is_honest_when_pipeline_broken(client):
    response = client.get("/health")
    body = response.get_json()
    # No model and no espeak-ng in CI: this must NOT be reported as ok.
    assert response.status_code == 503, body
    assert body["status"] in ("degraded", "error")
    assert body["vision_ready"] is False
    assert isinstance(body["issues"], list) and body["issues"]


def test_health_reports_component_fields(client):
    body = client.get("/health").get_json()
    for field in (
        "status",
        "vision_ready",
        "vision_script_ok",
        "model_path",
        "tts_ready",
        "companion_ready",
        "last_vision_success",
        "issues",
    ):
        assert field in body


def test_latest_frame_404_without_a_frame(client):
    """Audit B3: no silent fallback to a bundled placeholder image."""
    response = client.get("/api/latest-frame")
    assert response.status_code == 404
    assert "no fresh frame" in response.get_json()["error"]


def test_api_config_does_not_leak_token(client):
    body = client.get("/api/config").get_json()
    assert set(body) == {"auth_required", "frame_max_age_seconds"}, body
    assert "auth_required" in body


def test_service_worker_served_with_root_scope(client):
    response = client.get("/static/sw.js")
    assert response.status_code == 200
    assert response.headers.get("Service-Worker-Allowed") == "/"


def test_manifest_has_png_icons(client):
    body = client.get("/static/manifest.json").get_json()
    types = {icon["type"] for icon in body["icons"]}
    assert "image/png" in types, "installability requires real PNG icons, not only SVG"
    purposes = {icon.get("purpose") for icon in body["icons"]}
    assert "maskable" in purposes


def test_analyse_requires_multipart_image(client):
    assert client.post("/analyse").status_code == 400


def test_analyse_rejects_corrupt_bytes(client):
    # espeak-ng is absent in CI, so the TTS pre-check answers first; either way
    # the corrupt payload must never produce a 200.
    response = client.post(
        "/analyse",
        data={"image": (__import__("io").BytesIO(b"not a jpeg"), "f.jpg")},
        content_type="multipart/form-data",
    )
    assert response.status_code in (400, 503)


def test_analyse_without_tts_does_not_leak_frame(client):
    """The 503 path must not leave a per-request jpg behind (audit C11)."""
    import io
    from PIL import Image

    import server

    buf = io.BytesIO()
    Image.new("RGB", (64, 64), (1, 2, 3)).save(buf, "JPEG")

    data_dir = server.app.config["SETTINGS"].data_dir
    before = {p.name for p in data_dir.iterdir()}
    response = client.post(
        "/analyse",
        data={"image": (io.BytesIO(buf.getvalue()), "f.jpg")},
        content_type="multipart/form-data",
    )
    assert response.status_code == 503
    after = {p.name for p in data_dir.iterdir()}
    assert after - before <= {"latest.jpg"}, f"unexpected leftovers: {after - before}"


def test_audio_route_rejects_bad_ids(client):
    assert client.get("/audio/").status_code == 404
    assert client.get("/audio/zz").status_code == 404
    assert client.get("/audio/" + "a" * 15).status_code == 404
    assert client.get("/audio/" + "a" * 17).status_code == 404
    assert client.get("/audio/" + "-" * 16).status_code == 404


def test_chat_requires_prompt(client):
    assert client.post("/api/chat", json={}).status_code == 400


def test_chat_reports_degradation(client):
    """Audit D2: a provider outage must be distinguishable from an answer."""
    response = client.post("/api/chat", json={"prompt": "what is in front of me?", "use_camera": True})
    # No OPENROUTER_API_KEY in CI: the gateway must say so, loudly.
    assert response.status_code == 502
    body = response.get_json()
    assert body["degraded"] is True
    assert body["reason"] == "missing_api_key"
    assert body["frame_stale"] is True
    assert body["image_attached"] is False


def test_chat_rate_limit(client):
    import server

    service = server.app.config["SERVICE"]
    original = service._rate_hits.copy()
    try:
        client_id = "rate-test-client"
        for _ in range(server.CHAT_RATE_LIMIT):
            assert service.allow_chat(client_id)
        assert not service.allow_chat(client_id)
    finally:
        service._rate_hits = original


def test_runtime_sweeper_removes_old_files(client):
    import os
    import time

    import server

    stale = server.app.config["SETTINGS"].data_dir / "stale.jpg"
    stale.write_bytes(b"x")
    old = time.time() - server.RUNTIME_TTL_SECONDS - 10
    os.utime(stale, (old, old))

    removed = server.app.config["SERVICE"].sweep_runtime()
    assert removed >= 1
    assert not stale.exists()


def test_sw_js_root_alias_serves_worker_with_scope_header(client):
    # The antigravity remediation registered the worker at /sw.js; this branch
    # registers /static/sw.js. Both paths must keep working after the merge.
    for path in ("/sw.js", "/static/sw.js"):
        res = client.get(path)
        assert res.status_code == 200, path
        assert res.headers.get("Service-Worker-Allowed") == "/", path
        assert "javascript" in res.headers["Content-Type"]


def test_api_chat_auth_token_enforced_when_set(client, monkeypatch):
    # With BLINDSIGHT_API_TOKEN configured, /api/chat must reject bearer-less
    # calls and accept the right token (then fail honestly on the missing key).
    monkeypatch.setenv("BLINDSIGHT_API_TOKEN", "secret123")
    import server as server_module

    settings = server_module.load_settings()
    app = server_module.create_app(settings)
    test_client = app.test_client()
    denied = test_client.post("/api/chat", json={"prompt": "hi"})
    assert denied.status_code == 401
    allowed = test_client.post(
        "/api/chat", json={"prompt": "hi"}, headers={"Authorization": "Bearer secret123"}
    )
    assert allowed.status_code != 401
