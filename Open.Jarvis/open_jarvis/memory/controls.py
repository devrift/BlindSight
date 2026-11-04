"""User-directed privacy and data controls for local memory."""

from __future__ import annotations

import copy
import json
from pathlib import Path
from typing import Any

from open_jarvis.config.manager import ConfigManager
from open_jarvis.memory.memory_store import DEFAULT_MEMORY, MEMORY_FILE, load_memory, save_memory
from open_jarvis.memory.privacy_mode import build_privacy_session, mask_sensitive_value, memory_writes_enabled


class MemoryControlService:
    """Read, export, and remove local memory without exposing raw secrets."""

    def __init__(self, *, memory_path: str | Path = MEMORY_FILE, config_manager: ConfigManager | None = None) -> None:
        self.memory_path = Path(memory_path)
        self.config_manager = config_manager or ConfigManager()

    def privacy_state(self) -> dict[str, Any]:
        self.config_manager.load()
        privacy_mode = bool(self.config_manager.get("privacy.privacy_mode", False))
        memory_enabled = bool(self.config_manager.get("privacy.memory_enabled", True))
        state = build_privacy_session(enabled=privacy_mode)
        state["memory_enabled"] = memory_enabled
        state["writes_allowed"] = memory_writes_enabled(self.config_manager)
        return state

    def view_memory(self) -> dict[str, Any]:
        """Return a masked memory snapshot without creating missing memory."""

        memory = load_memory(self.memory_path, create_if_missing=False)
        return mask_sensitive_value(memory)

    def list_memory(self) -> list[dict[str, Any]]:
        """Return masked list rows for preferences, notes, and habits."""

        if not self.memory_path.exists():
            return []
        memory = self.view_memory()
        rows: list[dict[str, Any]] = []
        for key, value in memory.get("preferences", {}).items():
            rows.append({"category": "preference", "key": key, "value": value})
        for index, note in enumerate(memory.get("notes", [])):
            rows.append({"category": "note", "index": index, "value": note})
        for key, value in memory.get("habits", {}).items():
            rows.append({"category": "habit", "key": key, "value": value})
        return rows

    def delete_note(self, index: int) -> dict[str, Any]:
        """Delete one persisted note by index."""

        memory = load_memory(self.memory_path, create_if_missing=False)
        notes = list(memory.get("notes", []))
        if not 0 <= index < len(notes):
            return {"status": "missing", "index": index}
        del notes[index]
        memory["notes"] = notes
        save_memory(memory, memory_file=self.memory_path)
        return {"status": "deleted", "index": index}

    def clear_memory(self) -> dict[str, Any]:
        """Reset the persistent local memory document on explicit user request."""

        cleared = copy.deepcopy(DEFAULT_MEMORY)
        save_memory(cleared, memory_file=self.memory_path)
        return {"status": "cleared", "path": str(self.memory_path)}

    def export_memory(self, export_path: str | Path) -> dict[str, Any]:
        """Export a masked JSON memory snapshot to an explicit user path."""

        path = Path(export_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        payload = {"schema_version": 1, "memory": self.view_memory(), "privacy": self.privacy_state()}
        path.write_text(json.dumps(payload, indent=2, ensure_ascii=False, sort_keys=True) + "\n", encoding="utf-8")
        return {"status": "exported", "path": str(path), "entries": len(self.list_memory())}
