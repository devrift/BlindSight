"""Companion memory, language switching and chat history (OpenRouter companion).

The companion used to forget everything between requests. These tests pin the
new behaviour: persistent prefs, user-taught facts, conversation history sent
to the model, offline voice commands, capped storage and corrupt-file recovery.
All state is redirected into a tmp dir so tests never touch repo state/.
"""
from __future__ import annotations

import json
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


def test_prefs_roundtrip_and_validation(client):
    prefs = client.get("/api/prefs").get_json()
    assert prefs["language"] == "en"
    assert {"code": "hi", "label": "Hindi"} in prefs["options"]

    assert client.post("/api/prefs", json={"language": "hi"}).status_code == 200
    assert client.get("/api/prefs").get_json()["language"] == "hi"

    bad = client.post("/api/prefs", json={"language": "klingon"})
    assert bad.status_code == 400
    assert client.get("/api/prefs").get_json()["language"] == "hi"


def test_remember_and_recall_work_without_api_key(client):
    reply = client.post("/api/chat", json={"prompt": "remember that my dog is Bruno"})
    body = reply.get_json()
    assert reply.status_code == 200 and body["local_command"] is True
    assert "Bruno" in body["reply"]

    recall = client.post("/api/chat", json={"prompt": "what do you remember?"}).get_json()
    assert "Bruno" in recall["reply"]


def test_forget_single_fact_and_everything(client):
    client.post("/api/chat", json={"prompt": "remember that my dog is Bruno"})
    client.post("/api/chat", json={"prompt": "remember that I live on the second floor"})

    gone = client.post("/api/chat", json={"prompt": "forget bruno"}).get_json()
    assert "Bruno" in gone["reply"]
    left = client.post("/api/chat", json={"prompt": "what do you remember?"}).get_json()
    assert "Bruno" not in left["reply"] and "second floor" in left["reply"]

    wiped = client.post("/api/chat", json={"prompt": "forget everything"}).get_json()
    assert "0 things" in wiped["reply"] or "forgot" in wiped["reply"].lower()
    assert companion_state.list_facts() == []


def test_language_switch_by_name_and_code(client):
    assert client.post("/api/chat", json={"prompt": "language hindi"}).get_json()["note"] == "language_set"
    assert companion_state.get_language() == "hi"
    assert companion_state.espeak_voice() == "hi"

    assert client.post("/api/chat", json={"prompt": "switch language to en"}).get_json()["note"] == "language_set"
    assert companion_state.get_language() == "en"

    unknown = client.post("/api/chat", json={"prompt": "language klingon"}).get_json()
    assert unknown["note"] == "language_unknown" and "I can reply in" in unknown["reply"]


def test_clear_chat_command(client):
    companion_state.append_turn("user", "hello")
    companion_state.append_turn("assistant", "hi there")
    body = client.post("/api/chat", json={"prompt": "start over"}).get_json()
    assert body["note"] == "history_cleared"
    assert companion_state.recent_history() == []


def test_history_and_memory_reach_the_model(client, monkeypatch):
    import companion_ai

    monkeypatch.setenv("OPENROUTER_API_KEY", "test-key-123")
    captured = {}

    class FakeResponse:
        status_code = 200
        text = "ok"

        def json(self):
            return {"choices": [{"message": {"content": "Stub answer."}}]}

    def fake_post(url, headers=None, json=None, timeout=None):
        captured["payload"] = json
        return FakeResponse()

    monkeypatch.setattr(companion_ai.requests, "post", fake_post)

    companion_state.set_language("hi")
    companion_state.add_fact("my dog is Bruno")
    companion_state.append_turn("user", "what is in front of me?")
    companion_state.append_turn("assistant", "A chair, two metres ahead.")

    reply = client.post("/api/chat", json={"prompt": "and on the left?"})
    assert reply.status_code == 200
    messages = captured["payload"]["messages"]
    system = messages[0]["content"]
    assert "Hindi" in system and "my dog is Bruno" in system
    roles = [m["role"] for m in messages]
    assert roles == ["system", "user", "assistant", "user"]
    assert messages[-1]["content"] == "and on the left?"

    # a successful exchange is stored for the next turn
    history = companion_state.recent_history()
    assert history[-2:] == [
        {"role": "user", "content": "and on the left?"},
        {"role": "assistant", "content": "Stub answer."},
    ]


def test_failed_call_does_not_pollute_history(client, monkeypatch):
    monkeypatch.delenv("OPENROUTER_API_KEY", raising=False)
    reply = client.post("/api/chat", json={"prompt": "hello?"})
    assert reply.status_code == 502
    assert companion_state.recent_history() == []


def test_history_is_capped(isolated_state):
    for i in range(30):
        companion_state.append_turn("user", f"turn {i}")
    assert len(companion_state.recent_history(limit=100)) == companion_state.HISTORY_MAX
    assert len(companion_state.recent_history()) == companion_state.HISTORY_SENT
    stored = json.loads((isolated_state / "companion.json").read_text())
    assert len(stored["history"]) == companion_state.HISTORY_MAX


def test_memory_is_capped_and_deduped(isolated_state):
    for i in range(25):
        companion_state.add_fact(f"fact number {i}")
    companion_state.add_fact("fact number 24")  # repeat must not duplicate
    facts = companion_state.list_facts()
    assert len(facts) == companion_state.MEMORY_MAX
    assert facts.count("fact number 24") == 1
    assert facts[-1] == "fact number 24"


def test_corrupt_state_is_quarantined_not_fatal(client, isolated_state):
    (isolated_state / "companion.json").write_text("{not json at all", encoding="utf-8")
    companion_state.reset_cache()
    prefs = client.get("/api/prefs")
    assert prefs.status_code == 200
    assert prefs.get_json()["language"] == "en"
    quarantined = list(isolated_state.glob("companion.json.corrupt-*"))
    assert quarantined, "corrupt state file must be kept for inspection"


def test_state_survives_reload(isolated_state):
    companion_state.set_language("ta")
    companion_state.add_fact("I prefer quiet routes")
    companion_state.reset_cache()  # simulates a gateway restart
    assert companion_state.get_language() == "ta"
    assert companion_state.list_facts() == ["I prefer quiet routes"]


def test_tts_voice_follows_language(isolated_state):
    settings = server.Settings(
        data_dir=isolated_state,
        uploads_dir=isolated_state,
        models_dir=isolated_state,
        vision_command=["python3", "vision_ai.py"],
        vision_script=None,
        espeak_binary="espeak-ng",
        api_token=None,
    )
    service = server.VisionService.__new__(server.VisionService)
    service.settings = settings
    assert service.tts_command("hello", isolated_state / "out.wav")[1:3] == ["-v", "en"]
    companion_state.set_language("hi")
    assert service.tts_command("hello", isolated_state / "out.wav")[1:3] == ["-v", "hi"]


def test_system_prompt_includes_language_and_memory():
    import companion_ai

    plain = companion_ai.build_system_prompt("English", [])
    assert "Hindi" not in plain
    rich = companion_ai.build_system_prompt("Hindi", ["my dog is Bruno"])
    assert "Always reply in Hindi" in rich and "my dog is Bruno" in rich
