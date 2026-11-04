# Stable Release Readiness

This checklist defines what `v1.0.0` means for Open.Jarvis.

## Stable Scope

- Windows-first desktop assistant behavior is documented.
- Local-first command routing and keyless degraded mode remain the default posture.
- Optional Groq/cloud fallback remains disabled unless explicitly configured.
- Memory privacy controls remain available and tested.
- v0.9.0 command, path, URL, plugin, provider-regression, and artifact safety checks remain in force.
- Public release, repo hygiene, project audit, Ruff, unittest, pytest, release, and security checks pass locally.

## Release Checklist

Before tagging a stable release:

1. Run `python scripts/public_release_check.py`.
2. Run `python repo_hygiene.py`.
3. Run `python project_audit.py`.
4. Run `python -m ruff check .`.
5. Run `python -m unittest discover -s tests -q`.
6. Run `python -m pytest`.
7. Run release and security targeted tests.
8. Run CI-equivalent release smoke commands.
9. Confirm no generated/private files are present.
10. Confirm the GitHub Release title, tag, commit, latest badge, and assets match the intended release.

## Known Limitations

`v1.0.0` does not claim:

- full OS-level sandboxing
- encrypted vault storage
- signed Windows installer guarantees
- safe arbitrary command execution
- mandatory cloud provider support
- cloud-never-receives-data behavior when cloud fallback is explicitly enabled

## Artifact Rules

Do not publish or commit:

- `.env`
- `settings.json`
- `memory.json`
- `exports/`
- `logs/`
- `provider_cache/`
- `provider_state/`
- `plugin_cache/`
- `plugin_state/`
- `.provider*`
- `.plugin*`
- `groq_cache/`
- build, dist, release, cache, token, credential, or secret artifacts

`.env.example` is allowed as a placeholder template.
