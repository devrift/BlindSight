"""Guards against the failure mode that broke this repository (audit A1, A2, A3).

Unresolved merge conflict markers were committed as content in .gitignore and
edge_server/vision_ai.py, and a merge-tool backup (.orig) was committed too.
Nothing in CI noticed. This makes sure it can never happen silently again.
"""
from __future__ import annotations

import re
import subprocess
from pathlib import Path

MARKER = re.compile(r"^(<{7}( |$)|={7}$|>{7}( |$)|\|{7}( |$))")
ARTIFACT_SUFFIXES = (".orig", ".rej", ".BASE.", ".LOCAL.", ".REMOTE.", ".BACKUP.")
BINARY_HINTS = (b"\x89PNG", b"%PDF", b"PK\x03\x04", b"\xff\xd8\xff")


def tracked_files(repo_root: Path) -> list[Path]:
    out = subprocess.run(
        ["git", "-C", str(repo_root), "ls-files"],
        capture_output=True,
        text=True,
        check=True,
    ).stdout.splitlines()
    return [repo_root / line for line in out if line]


def test_no_conflict_markers_in_tracked_text_files(repo_root: Path):
    offenders = []
    for path in tracked_files(repo_root):
        if not path.is_file():
            continue
        head = path.read_bytes()[:64]
        if any(head.startswith(h) for h in BINARY_HINTS):
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for lineno, line in enumerate(text.splitlines(), 1):
            if MARKER.match(line):
                offenders.append(f"{path.relative_to(repo_root)}:{lineno}: {line[:60]}")
    assert not offenders, "unresolved merge conflict markers committed:\n" + "\n".join(offenders)


def test_no_merge_artifacts_tracked(repo_root: Path):
    offenders = [
        str(path.relative_to(repo_root))
        for path in tracked_files(repo_root)
        if any(name in path.name for name in ARTIFACT_SUFFIXES)
    ]
    assert not offenders, f"merge/backup artifacts committed: {offenders}"


def test_all_python_sources_parse(repo_root: Path):
    broken = []
    for path in tracked_files(repo_root):
        if path.suffix != ".py" or not path.is_file():
            continue
        try:
            compile(path.read_text(encoding="utf-8"), str(path), "exec")
        except SyntaxError as error:
            broken.append(f"{path.relative_to(repo_root)}: line {error.lineno}: {error.msg}")
    assert not broken, "tracked Python files that do not parse:\n" + "\n".join(broken)
