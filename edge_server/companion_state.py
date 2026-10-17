"""Persistent companion state: reply language, chat history, small memory.

The companion used to be stateless: every /api/chat call was a single shot with
no context, so the assistant forgot the user's language and everything it had
been told between requests. This module keeps a tiny JSON document on disk so
conversations, preferences and user-taught facts survive restarts.

Design rules (this gateway serves blind users, so honesty beats cleverness):

* The file lives outside ``runtime/`` - that folder is swept by the TTL
  cleaner, and memory must not expire an hour after being taught.
* Writes are atomic (temp file + ``os.replace``) so a power cut mid-save
  cannot truncate the only copy.
* A corrupt file is quarantined as ``companion.json.corrupt-<epoch>`` and the
  companion starts fresh, loudly, instead of crashing or guessing.
* Everything is capped (history turns, facts, fact length) so the "little
  memory" stays little on a 1 GB Pi.
* Local commands (remember/forget/recall/language/clear chat) are handled
  without any API key, so they keep working when the cloud is down.
"""
from __future__ import annotations

import json
import os
import threading
import time
from pathlib import Path
from typing import Any

SERVER_DIR = Path(__file__).resolve().parent
ROOT_DIR = SERVER_DIR.parent

STATE_DIR = Path(os.getenv("BLINDSIGHT_STATE_DIR", str(ROOT_DIR / "state")))
STATE_FILE = STATE_DIR / "companion.json"

HISTORY_MAX = 24          # stored turns (user+assistant pairs count as 2)
HISTORY_SENT = 8          # turns shipped to the model per request
MEMORY_MAX = 20           # user-taught facts
FACT_MAX_CHARS = 200

# code -> (espeak-ng voice, human label). espeak-ng ships all of these voices;
# quality varies per language but every entry here is a real voice name.
LANGUAGES = {
    "en": ("en", "English"),
    "hi": ("hi", "Hindi"),
    "bn": ("bn", "Bengali"),
    "ta": ("ta", "Tamil"),
    "te": ("te", "Telugu"),
    "es": ("es", "Spanish"),
    "fr": ("fr", "French"),
}
DEFAULT_LANGUAGE = "en"

_LOCK = threading.Lock()
_CACHE: dict[str, Any] = {}


def _empty() -> dict[str, Any]:
    return {"prefs": {"language": DEFAULT_LANGUAGE, "voice": ""}, "history": [], "memory": []}


def _load() -> dict[str, Any]:
    """Reads the state file; quarantines corrupt copies and starts fresh."""
    if "state" in _CACHE:
        return _CACHE["state"]
    state = _empty()
    if STATE_FILE.is_file():
        try:
            loaded = json.loads(STATE_FILE.read_text(encoding="utf-8"))
            if not isinstance(loaded, dict):
                raise ValueError("state root is not an object")
            prefs = loaded.get("prefs")
            state["prefs"] = {
                "language": prefs.get("language", DEFAULT_LANGUAGE)
                if isinstance(prefs, dict) and prefs.get("language") in LANGUAGES
                else DEFAULT_LANGUAGE,
                "voice": prefs.get("voice", "") if isinstance(prefs, dict) and isinstance(prefs.get("voice"), str) else "",
            }
            state["history"] = [
                t for t in loaded.get("history", [])
                if isinstance(t, dict) and t.get("role") in ("user", "assistant")
                and isinstance(t.get("content"), str)
            ][-HISTORY_MAX:]
            state["memory"] = [
                f for f in loaded.get("memory", []) if isinstance(f, str)
            ][-MEMORY_MAX:]
        except (ValueError, OSError) as err:
            quarantine = STATE_FILE.with_suffix(f".json.corrupt-{int(time.time())}")
            try:
                STATE_FILE.replace(quarantine)
                print(f"[ERROR] companion state unreadable ({err}); kept a copy at {quarantine}", flush=True)
            except OSError:
                print(f"[ERROR] companion state unreadable ({err}); starting fresh", flush=True)
            state = _empty()
    _CACHE["state"] = state
    return state


def _save() -> None:
    STATE_DIR.mkdir(parents=True, exist_ok=True)
    tmp = STATE_DIR / f".companion.json.tmp-{os.getpid()}"
    tmp.write_text(json.dumps(_load(), indent=1, sort_keys=True), encoding="utf-8")
    os.replace(tmp, STATE_FILE)


def reset_cache() -> None:
    """Test hook: forget the in-memory copy so the next call re-reads disk."""
    _CACHE.clear()


def get_language() -> str:
    with _LOCK:
        return _load()["prefs"]["language"]


def set_language(code: str) -> str | None:
    """Returns the stored code, or None when `code` is not supported."""
    code = (code or "").strip().lower()
    if code not in LANGUAGES:
        return None
    with _LOCK:
        _load()["prefs"]["language"] = code
        _save()
        return code


def language_options() -> list[dict[str, str]]:
    return [{"code": c, "label": label} for c, (_voice, label) in LANGUAGES.items()]


def get_voice() -> str:
    with _LOCK:
        return _load()["prefs"]["voice"]


def set_voice(name: str) -> str:
    name = (name or "").strip()[:120]
    with _LOCK:
        _load()["prefs"]["voice"] = name
        _save()
        return name


def espeak_voice(code: str | None = None) -> str:
    return LANGUAGES.get(code or get_language(), LANGUAGES[DEFAULT_LANGUAGE])[0]


def append_turn(role: str, content: str) -> None:
    with _LOCK:
        history = _load()["history"]
        history.append({"role": role, "content": content[:2000], "ts": int(time.time())})
        if len(history) > HISTORY_MAX:
            del history[:-HISTORY_MAX]
        _save()


def recent_history(limit: int = HISTORY_SENT) -> list[dict[str, str]]:
    with _LOCK:
        return [{"role": t["role"], "content": t["content"]} for t in _load()["history"][-limit:]]


def clear_history() -> None:
    with _LOCK:
        _load()["history"] = []
        _save()


def add_fact(fact: str) -> str:
    fact = " ".join(fact.split())[:FACT_MAX_CHARS]
    with _LOCK:
        memory = _load()["memory"]
        lowered = fact.lower()
        memory[:] = [f for f in memory if f.lower() != lowered]
        memory.append(fact)
        if len(memory) > MEMORY_MAX:
            del memory[:-MEMORY_MAX]
        _save()
        return fact


def forget_fact(query: str) -> str | None:
    query = query.strip().lower()
    with _LOCK:
        memory = _load()["memory"]
        hit = next((f for f in reversed(memory) if query and query in f.lower()), None)
        if hit is not None:
            memory.remove(hit)
            _save()
        return hit


def clear_memory() -> int:
    with _LOCK:
        count = len(_load()["memory"])
        _load()["memory"] = []
        _save()
        return count


def list_facts() -> list[str]:
    with _LOCK:
        return list(_load()["memory"])
