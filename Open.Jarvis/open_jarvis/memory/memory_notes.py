"""Note storage helpers."""

from __future__ import annotations

import datetime

from open_jarvis.memory.memory_store import load_memory, save_memory
from open_jarvis.memory.privacy_mode import memory_reads_enabled, memory_writes_enabled


def add_note(note: str, *, config_manager=None):
    """Save a note."""

    if not memory_writes_enabled(config_manager):
        return
    memory = load_memory()
    memory["notes"].append(
        {
            "text": note,
            "created_at": datetime.datetime.now().strftime("%d.%m.%Y %H:%M"),
        }
    )
    save_memory(memory)


def get_notes(*, config_manager=None) -> list:
    """Return all saved notes."""

    if not memory_reads_enabled(config_manager):
        return []
    memory = load_memory()
    return memory.get("notes", [])


def clear_notes():
    """Clear all notes."""

    memory = load_memory()
    memory["notes"] = []
    save_memory(memory)
