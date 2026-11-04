"""Path safety helpers for scoped local file operations."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path, PureWindowsPath


@dataclass(frozen=True)
class PathSafetyResult:
    """Structured path safety decision."""

    allowed: bool
    reason: str = ""
    resolved: Path | None = None


PRIVATE_RUNTIME_NAMES = {
    ".env",
    "memory.json",
    "settings.json",
}
PRIVATE_RUNTIME_PARTS = {
    "exports",
    "logs",
    "provider_cache",
    "provider_state",
    "plugin_cache",
    "plugin_state",
    "groq_cache",
    "__pycache__",
}


def is_private_runtime_path(path: str | Path) -> bool:
    """Return whether a relative path names private runtime data."""

    value = str(path).replace("\\", "/")
    parts = [part.lower() for part in value.split("/") if part]
    if not parts:
        return False
    name = parts[-1]
    return (
        name in PRIVATE_RUNTIME_NAMES
        or any(part in PRIVATE_RUNTIME_PARTS for part in parts)
        or any(part.startswith(".provider") or part.startswith(".plugin") for part in parts)
        or any(marker in name for marker in ("token", "credential", "secret", "apikey", "api_key"))
    )


def validate_path_within_root(root: str | Path, candidate: str | Path, *, allow_private: bool = False) -> PathSafetyResult:
    """Resolve candidate and require it to stay inside root."""

    root_path = Path(root).resolve()
    raw_candidate = Path(candidate)
    if PureWindowsPath(str(candidate)).is_absolute() and not raw_candidate.is_absolute():
        return PathSafetyResult(False, "absolute Windows paths are not allowed")
    try:
        resolved = (raw_candidate if raw_candidate.is_absolute() else root_path / raw_candidate).resolve()
    except (OSError, RuntimeError, ValueError) as exc:
        return PathSafetyResult(False, f"path could not be resolved: {exc}")
    try:
        resolved.relative_to(root_path)
    except ValueError:
        return PathSafetyResult(False, "path escapes the allowed root", resolved)

    relative = resolved.relative_to(root_path)
    if not allow_private and is_private_runtime_path(relative):
        return PathSafetyResult(False, "private runtime path is not allowed", resolved)
    return PathSafetyResult(True, resolved=resolved)


def require_path_within_root(root: str | Path, candidate: str | Path, *, allow_private: bool = False) -> Path:
    """Return a safe resolved path or raise ValueError."""

    result = validate_path_within_root(root, candidate, allow_private=allow_private)
    if not result.allowed:
        raise ValueError(result.reason)
    assert result.resolved is not None
    return result.resolved
