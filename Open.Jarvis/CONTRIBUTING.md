# Contributing

Thanks for helping improve Open.Jarvis. The project controls a real desktop, so small changes can have real safety impact.

## Quality gate

Run these before calling work complete:

```bash
python -m unittest discover -s tests -q
python -m coverage run -m unittest discover -s tests -q
python -m coverage report -m
python -m ruff check .
python -m ruff format --check .
python -m mypy .
python -m compileall .
python project_audit.py
python repo_hygiene.py
python scripts/public_release_check.py
python kontrol.py --no-pause
python -m pip check
```

## Development rules

- Write tests for new features and bug fixes.
- Keep destructive actions behind explicit safety gates.
- Do not add secrets or local machine data to the repo.
- Prefer small domain modules over growing large files.
- Use actionable errors: explain why something failed and what to do next.
- Update README or docs when behavior changes.
- Keep README release-contract sections intact: Product Feature Modules, Easy Roadmap Complete, Current Next Roadmap, and Compared With Other Jarvis Projects.
- Keep stable-release docs conservative; do not claim full OS sandboxing, encrypted vault storage, signed installer guarantees, or cloud privacy guarantees beyond the implemented controls.

## Pull request checklist

- Tests cover the new behavior.
- Health check still runs.
- Feature quality notes are updated when a core feature changes.
- Security impact is documented for desktop, plugin, file, network, or credential changes.
- Public release checks and repo hygiene pass before publication.
