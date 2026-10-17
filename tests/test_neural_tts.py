"""Neural (human-sounding) TTS chain and language-follows-script behaviour.

The field complaint was "the voices are still like the AI": browser
speechSynthesis robots. The gateway now renders replies with Microsoft's
neural voices via edge-tts (mp3), falls back to espeak-ng, and only then lets
the browser speak. These tests pin the chain order and the chat payload.
"""
from __future__ import annotations

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "edge_server"))

import companion_state
import server


@pytest.fixture(autouse=True)
def isolated_state(tmp_path, monkeypatch):
    state_dir = tmp_path / "state"
    state_dir.mkdir()
    monkeypatch.setattr(companion_state, "STATE_DIR", state_dir)
    monkeypatch.setattr(companion_state, "STATE_FILE", state_dir / "companion.json")
    companion_state.reset_cache()
    yield state_dir
    companion_state.reset_cache()


def _service(tmp_path):
    settings = server.Settings(
        data_dir=tmp_path,
        uploads_dir=tmp_path,
        models_dir=tmp_path,
        vision_command=["python3", "vision_ai.py"],
        vision_script=None,
        espeak_binary="espeak-ng",
        api_token=None,
    )
    return server.VisionService.__new__(server.VisionService), settings


def test_neural_tts_wins_over_espeak(tmp_path, monkeypatch):
    service, settings = _service(tmp_path)
    service.settings = settings
    monkeypatch.setattr(service, "edge_tts_cmd", lambda: ["fake-edge"])
    calls = []

    def fake_run(argv, **kwargs):
        calls.append(argv)
        out = Path(argv[argv.index("--write-media") + 1])
        out.write_bytes(b"x" * 2000)

        class Done:
            returncode = 0

        return Done()

    monkeypatch.setattr(server.subprocess, "run", fake_run)
    audio = service.synthesize_reply("hello there", "en-IN-NeerjaNeural", tmp_path / "abc")
    assert audio is not None and audio.suffix == ".mp3"
    assert calls[0][0:3] == ["fake-edge", "--voice", "en-IN-NeerjaNeural"]


def test_espeak_fallback_when_neural_fails(tmp_path, monkeypatch):
    service, settings = _service(tmp_path)
    service.settings = settings
    monkeypatch.setattr(service, "edge_tts_cmd", lambda: ["fake-edge"])

    def fake_run(argv, **kwargs):
        if argv[0] == "fake-edge":
            class Failed:
                returncode = 1

            return Failed()
        out = Path(argv[argv.index("-w") + 1])
        out.write_bytes(b"x" * 900)

        class Done:
            returncode = 0

        return Done()

    monkeypatch.setattr(server.subprocess, "run", fake_run)
    monkeypatch.setattr(server.shutil, "which", lambda name: "/usr/bin/" + name)
    audio = service.synthesize_reply("hello", "", tmp_path / "abc")
    assert audio is not None and audio.suffix == ".wav"


def test_no_tts_returns_none(tmp_path, monkeypatch):
    service, settings = _service(tmp_path)
    service.settings = settings
    monkeypatch.setattr(service, "edge_tts_cmd", lambda: None)
    monkeypatch.setattr(service, "tts_available", lambda: False)
    assert service.synthesize_reply("hello", "", tmp_path / "abc") is None


def test_chat_returns_audio_url_and_follows_script(client, monkeypatch, tmp_path):
    import companion_ai

    monkeypatch.setenv("OPENROUTER_API_KEY", "test-key-123")
    captured = {}

    class FakeResponse:
        status_code = 200
        text = "ok"

        def json(self):
            return {"choices": [{"message": {"content": "Stub answer."}}]}

    def fake_post(url, headers=None, json=None, timeout=None):
        captured["system"] = json["messages"][0]["content"]
        return FakeResponse()

    monkeypatch.setattr(companion_ai.requests, "post", fake_post)

    def fake_synth(self, text, voice, out_stem):
        mp3 = out_stem.with_suffix(".mp3")
        mp3.write_bytes(b"x" * 1500)
        return mp3

    monkeypatch.setattr(server.VisionService, "synthesize_reply", fake_synth)

    # English preference, Hindi script input: the turn must answer in Hindi
    companion_state.set_language("en")
    reply = client.post("/api/chat", json={"prompt": "क्या मेरे आगे कुछ है?"})
    body = reply.get_json()
    assert reply.status_code == 200
    assert body["language"] == "hi"
    assert "Hindi" in captured["system"]
    assert body["audio_url"], "neural audio must ride along with the reply"

    audio = client.get(body["audio_url"])
    assert audio.status_code == 200
    assert audio.headers["Content-Type"] == "audio/mpeg"

    # Latin script returns to the saved preference
    reply = client.post("/api/chat", json={"prompt": "what is ahead?"})
    assert reply.get_json()["language"] == "en"


def test_voice_preference_roundtrip(client):
    assert client.post("/api/prefs", json={"voice": "en-IN-NeerjaNeural"}).status_code == 200
    assert client.get("/api/prefs").get_json()["voice"] == "en-IN-NeerjaNeural"


def test_tts_voices_endpoint_falls_back_to_browser(client, monkeypatch):
    monkeypatch.setattr(server.VisionService, "edge_tts_cmd", lambda self: None)
    body = client.get("/api/tts/voices").get_json()
    assert body["source"] == "browser"
    assert body["voices"] == []
