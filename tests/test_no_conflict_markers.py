from __future__ import annotations

import re
import subprocess
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
CONFLICT_PATTERN = re.compile(r"^(<{7}|={7}$|>{7})", re.MULTILINE)


def test_no_git_conflict_markers():
    """Verify zero merge conflict markers exist in any tracked file."""
    # Run git grep for conflict markers
    res = subprocess.run(
        ["git", "grep", "-n", "-P", "^(<{7}|={7}$|>{7})"],
        cwd=str(ROOT_DIR),
        capture_output=True,
        text=True,
    )
    # git grep returns 1 when no matches are found
    assert res.returncode == 1, f"Found conflict markers:\n{res.stdout}"
    assert res.stdout.strip() == ""


def test_no_backup_or_rejected_files():
    """Verify no *.orig, *.rej, or *.bak files exist in the repository."""
    forbidden_suffixes = {".orig", ".rej", ".bak"}
    found = []
    for p in ROOT_DIR.rglob("*"):
        if ".git" in p.parts:
            continue
        if p.is_file() and p.suffix.lower() in forbidden_suffixes:
            found.append(str(p.relative_to(ROOT_DIR)))
    assert found == [], f"Found backup/patch artifacts that should be deleted: {found}"
