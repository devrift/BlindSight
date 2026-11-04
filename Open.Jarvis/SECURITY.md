# Security Policy

## Threat model

Open.Jarvis can listen to voice commands, open applications, control the desktop, read clipboard text, use API keys, and persist memory. The main risks are accidental destructive actions, credential exposure, unsafe plugins, prompt injection, over-broad desktop automation, and untrusted release artifacts.

## Current safety defaults

- Destructive runtime actions are blocked unless `JARVIS_ALLOW_DESTRUCTIVE_ACTIONS=true`.
- Process helpers run with `shell=False` and reject shell strings, shell execution flags, and obvious destructive command executables by default.
- Browser navigation is limited to `http` and `https` URLs.
- File/path safety helpers reject traversal outside an allowed root and identify private runtime paths such as memory, settings, logs, provider state, and plugin state.
- Plugin manifests must keep entrypoints inside the plugin directory.
- Plugin loader entrypoints are rechecked at load time so malformed registry entries cannot import files outside the plugin directory.
- Plugin discovery must not execute plugin code.
- Plugin permissions are deny-by-default for unknown, high-risk, and critical capabilities.
- Privacy mode can disable normal memory collection writes and mask secret-like values in memory views and exports.
- Provider routing is local-first; cloud fallback is disabled unless explicitly enabled in non-secret settings and backed by environment credentials.
- Release signing uses trusted signers and a signing key policy.
- Health checks surface missing credentials before normal runtime use.
- Public release checks treat signing as optional for source releases, but signed executable/model artifacts should use a trusted signing key.

## Secret and environment rules

- Keep real credentials only in a local `.env` file.
- Commit `.env.example` with placeholders only.
- Keep `settings.json` limited to non-secret preferences. The v0.6.0 Settings UI shows secret presence only and does not store raw API keys, OAuth secrets, tokens, or signing keys.
- Run `python repo_hygiene.py --include-secrets` before publishing.
- Run `python repo_hygiene.py --clean` to remove generated artifacts; add `--include-secrets` only after backing up or rotating local keys.
- Do not paste API keys, OAuth tokens, Spotify secrets, plugin signing keys, release signing keys, runtime logs, or JSONL event streams into issues.
- Keep memory exports private. Exports mask secret-like values, but notes, habits, and preferences can still contain personal data.
- Treat cloud fallback as data sharing. When enabled, command text may be sent to the selected provider; privacy mode prevents persisted memory context from being attached to provider prompts.
- Do not paste private plugin manifests if they contain local paths, internal package names, or signing metadata.
- Logs and UI messages should mask secret-like values instead of printing raw credentials.

## Report a vulnerability

Do not publish exploit details in a public issue. Create a private report or contact the maintainer directly with:

- Affected file or feature.
- Steps to reproduce.
- Expected and actual impact.
- Whether secrets, files, desktop control, or external services are involved.
- Suggested mitigation if known.

## Supported versions

The current stable release line starts with the published `v1.0.0` release. Use the GitHub Releases page plus the current `main` branch audit result to determine the newest support status.

| Version line | Status |
| --- | --- |
| `v1.0.x` | Stable release line |
| `v0.9.x` | Security-hardening baseline carried into v1.0.0 |
| Older `v0.x` | Historical releases |

## Secure development checklist

- Add or update tests for every security-sensitive behavior.
- Run `python -m unittest discover -s tests -q`.
- Run `python -m ruff check .` and `python -m mypy .`.
- Run `python project_audit.py`.
- Never commit `.env`, API keys, tokens, generated secrets, or local user data.
- Never commit real `config/settings.json` files from source or portable runs.
- Never commit `memory.json` or user-selected memory export files from local data-control flows.
- Never commit provider or plugin runtime state or cache directories such as `provider_cache/`, `provider_state/`, `.provider*`, `plugin_cache/`, `plugin_state/`, `.plugin*`, or `groq_cache/`.
- Prefer allowlists over blocklists for commands, plugins, and URLs.
- Add negative tests for plugin permissions, broken plugin hooks, and invalid manifests.

## Out of scope

- Support for user-modified plugins that bypass the trust policy.
- Systems running with intentionally disabled safety gates.
- Full OS-level sandboxing, kernel isolation, encrypted vault storage, and signed installer guarantees unless a future release explicitly implements them.
- Secrets leaked outside the repository by local tools or shell history.
