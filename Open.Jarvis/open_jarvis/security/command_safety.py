"""Process command safety checks for local runtime helpers."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class CommandSafetyResult:
    """Structured command safety decision."""

    allowed: bool
    reason: str = ""
    command: tuple[str, ...] = ()


DANGEROUS_EXECUTABLES = {
    "del",
    "erase",
    "format",
    "mkfs",
    "rm",
}
DANGEROUS_WHEN_UNAPPROVED = {
    "shutdown",
    "shutdown.exe",
}
SHELL_EXECUTABLES = {
    "cmd",
    "cmd.exe",
    "powershell",
    "powershell.exe",
    "pwsh",
    "pwsh.exe",
}
SHELL_EXECUTION_FLAGS = {
    "/c",
    "/k",
    "-c",
    "-command",
    "/command",
    "-encodedcommand",
    "/encodedcommand",
    "-enc",
    "/enc",
}
PIPE_TO_SHELL_MARKERS = ("|", "&&", ";")


def validate_process_command(command: Any, *, allow_destructive: bool = False) -> CommandSafetyResult:
    """Validate a subprocess command before shell-free execution."""

    if isinstance(command, str):
        return CommandSafetyResult(False, "command must be an argument sequence, not a shell string")
    try:
        parts = tuple(str(part).strip() for part in command)
    except TypeError:
        return CommandSafetyResult(False, "command must be an iterable argument sequence")
    if not parts or not parts[0]:
        return CommandSafetyResult(False, "command must include an executable")
    if any(not part for part in parts):
        return CommandSafetyResult(False, "command arguments must be non-empty strings")

    executable = Path(parts[0]).name.lower()
    lowered_args = tuple(part.lower() for part in parts[1:])

    if executable in DANGEROUS_EXECUTABLES:
        return CommandSafetyResult(False, f"dangerous executable is blocked: {executable}", parts)
    if executable in DANGEROUS_WHEN_UNAPPROVED and not allow_destructive:
        return CommandSafetyResult(False, f"destructive executable requires explicit approval: {executable}", parts)
    if executable in SHELL_EXECUTABLES and any(arg in SHELL_EXECUTION_FLAGS for arg in lowered_args):
        return CommandSafetyResult(False, "shell command execution flags are blocked", parts)
    if executable in {"curl", "curl.exe", "wget", "wget.exe"} and any(marker in arg for arg in lowered_args for marker in PIPE_TO_SHELL_MARKERS):
        return CommandSafetyResult(False, "pipe-to-shell command patterns are blocked", parts)

    return CommandSafetyResult(True, command=parts)


def assert_process_command_safe(command: Any, *, allow_destructive: bool = False) -> list[str]:
    """Return a validated command list or raise a safe ValueError."""

    if isinstance(command, str):
        raise TypeError("command must be an argument sequence, not a shell string")
    result = validate_process_command(command, allow_destructive=allow_destructive)
    if not result.allowed:
        raise ValueError(result.reason)
    return list(result.command)
