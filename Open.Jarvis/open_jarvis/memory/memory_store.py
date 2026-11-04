"""Persistence helpers and base memory schema for JARVIS."""

from __future__ import annotations

import copy
import datetime
import json
from json import JSONDecodeError
from pathlib import Path
from typing import Any

from open_jarvis.utils.jarvis_logging import get_logger

logger = get_logger("memory")

MEMORY_FILE = "memory.json"

DEFAULT_MEMORY: dict[str, Any] = {
    "preferences": {
        "favorite_music": None,
        "favorite_app": None,
        "preferred_volume": None,
        "wake_word": "jarvis",
        "custom": {},
    },
    "habits": {},
    "notes": [],
    "created_at": None,
    "last_seen": None,
    "total_commands": 0,
}

MAX_NOTES = 25
MAX_HABITS = 50


def load_memory(memory_file: str | Path = MEMORY_FILE, *, create_if_missing: bool = True) -> dict:
    """Load memory from disk, optionally returning defaults without writing."""

    path = Path(memory_file)
    if path.exists():
        try:
            with path.open(encoding="utf-8") as file:
                data = json.load(file)
                for key, value in DEFAULT_MEMORY.items():
                    if key not in data:
                        data[key] = copy.deepcopy(value)
                return prune_memory(data, persist=False)
        except (OSError, JSONDecodeError) as exc:
            logger.exception("Memory load error: %s", exc)

    memory = copy.deepcopy(DEFAULT_MEMORY)
    memory["created_at"] = datetime.datetime.now().isoformat()
    if create_if_missing:
        save_memory(memory, memory_file=path)
    return memory


def save_memory(memory: dict, memory_file: str | Path = MEMORY_FILE):
    """Save memory to disk."""

    try:
        memory = prune_memory(memory, persist=False)
        memory["last_seen"] = datetime.datetime.now().isoformat()
        path = Path(memory_file)
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("w", encoding="utf-8") as file:
            json.dump(memory, file, indent=2, ensure_ascii=False)
    except OSError as exc:
        logger.exception("Memory save error: %s", exc)


def prune_memory(memory: dict | None = None, max_notes: int = MAX_NOTES, persist: bool = True) -> dict:
    """Trim memory growth while keeping the most recent information."""

    base = copy.deepcopy(load_memory() if memory is None else memory)

    notes = base.get("notes", [])
    if len(notes) > max_notes:
        base["notes"] = notes[-max_notes:]

    habits = base.get("habits", {})
    if len(habits) > MAX_HABITS:
        sorted_habits = sorted(habits.items(), key=lambda item: item[1], reverse=True)[:MAX_HABITS]
        base["habits"] = dict(sorted_habits)

    if persist:
        save_memory(base)

    return base
