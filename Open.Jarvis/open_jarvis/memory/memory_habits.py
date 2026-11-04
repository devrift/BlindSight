"""Habit tracking helpers."""

from __future__ import annotations

from open_jarvis.memory.memory_store import MAX_HABITS, load_memory, save_memory
from open_jarvis.memory.privacy_mode import memory_reads_enabled, memory_writes_enabled


def track_command(command: str, *, config_manager=None):
    """Track how often a command is used."""

    if not memory_writes_enabled(config_manager):
        return
    memory = load_memory()
    habits = memory.get("habits", {})
    key = " ".join(command.strip().split()[:3]).lower()
    habits[key] = habits.get(key, 0) + 1
    if len(habits) > MAX_HABITS:
        sorted_habits = sorted(habits.items(), key=lambda item: item[1], reverse=True)[:MAX_HABITS]
        habits = dict(sorted_habits)
    memory["habits"] = habits
    memory["total_commands"] = memory.get("total_commands", 0) + 1
    save_memory(memory)


def get_top_habits(n: int = 5, *, config_manager=None) -> list:
    """Return the N most used commands."""

    if not memory_reads_enabled(config_manager):
        return []
    memory = load_memory()
    habits = memory.get("habits", {})
    sorted_habits = sorted(habits.items(), key=lambda item: item[1], reverse=True)
    return sorted_habits[:n]
