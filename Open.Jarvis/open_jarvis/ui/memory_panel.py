"""User-visible memory management helpers."""

from __future__ import annotations

import copy
from typing import Any

from open_jarvis.memory.privacy_mode import build_privacy_session, mask_sensitive_value


def build_memory_panel(memory: dict[str, Any], *, privacy_enabled: bool = False) -> dict[str, Any]:
    """Return a safe snapshot suitable for a memory settings panel."""

    snapshot = copy.deepcopy(memory)
    preferences = mask_sensitive_value(snapshot.get("preferences", {}))
    notes = snapshot.get("notes", [])
    habits = snapshot.get("habits", {})
    return {
        "preferences": preferences,
        "notes": notes,
        "recent_notes": notes[-5:],
        "habits": habits,
        "privacy": build_privacy_session(enabled=privacy_enabled),
        "counts": {
            "preferences": len(preferences),
            "notes": len(notes),
            "habits": len(habits),
        },
    }


def update_preference(memory: dict[str, Any], key: str, value: Any) -> dict[str, Any]:
    """Update a preference without mutating the caller's memory object."""

    updated = copy.deepcopy(memory)
    updated.setdefault("preferences", {})[key] = value
    return updated


def delete_note(memory: dict[str, Any], index: int) -> dict[str, Any]:
    """Delete a note by index when it exists."""

    updated = copy.deepcopy(memory)
    notes = list(updated.get("notes", []))
    if 0 <= index < len(notes):
        del notes[index]
    updated["notes"] = notes
    return updated


class MemoryPanelModel:
    """Headless-safe bridge from UI controls to memory data controls."""

    def __init__(self, service) -> None:
        self.service = service

    def view_model(self) -> dict[str, Any]:
        state = self.service.privacy_state()
        return build_memory_panel(self.service.view_memory(), privacy_enabled=bool(state["enabled"]))

    def list_memory(self) -> list[dict[str, Any]]:
        return self.service.list_memory()

    def delete_note(self, index: int) -> dict[str, Any]:
        return self.service.delete_note(index)

    def clear_memory(self) -> dict[str, Any]:
        return self.service.clear_memory()

    def export_memory(self, export_path: str) -> dict[str, Any]:
        return self.service.export_memory(export_path)
