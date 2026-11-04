"""Centralized process execution helpers."""

from __future__ import annotations

import subprocess
from collections.abc import Sequence

from open_jarvis.security.command_safety import assert_process_command_safe


def launch_process(command: Sequence[str]):
    """Launch a process without invoking a shell."""

    return subprocess.Popen(assert_process_command_safe(command), shell=False)


def run_command(command: Sequence[str], *, allow_destructive: bool = False):
    """Run a command with shell disabled."""

    return subprocess.run(assert_process_command_safe(command, allow_destructive=allow_destructive), check=False, shell=False)
