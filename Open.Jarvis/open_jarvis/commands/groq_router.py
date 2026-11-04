"""Compatibility wrappers for Groq-backed command analysis and summarization."""

from __future__ import annotations

import os

from dotenv import load_dotenv

from open_jarvis.health.observability import record_runtime_event
from open_jarvis.providers import GroqProvider, ProviderRequest, ProviderRouter
from open_jarvis.providers.groq import (
    DEFAULT_GROQ_MODEL,
    GROQ_COOLDOWN_SECONDS,
    activate_groq_cooldown,
    extract_action_json,
    is_groq_cooling_down,
)
from open_jarvis.security.jarvis_admin import format_actionable_message
from open_jarvis.utils.jarvis_logging import get_logger

try:
    from groq import GroqError
except ImportError:  # pragma: no cover - dependency is available in normal installs.
    GroqError = RuntimeError

load_dotenv()

logger = get_logger("commands")
GROQ_API_KEY = os.getenv("GROQ_API_KEY")
client = None

if client is None:
    logger.warning("Groq API key not found. Running in local-only mode.")

SYSTEM_PROMPT = """
You are JARVIS, Tony Stark's personal AI assistant from Iron Man.
You are highly intelligent, witty, and always professional.
You speak in a formal British manner and address the user as "sir".
You remember the user's preferences and adapt to their habits.
You are proactive — if you notice patterns, mention them.
You occasionally make subtle, dry humor remarks.
Always be concise but complete in your responses.
You are JARVIS, an AI assistant like in Iron Man.
Think carefully before responding. Always return valid JSON.
Analyze the user's command and return ONLY valid JSON.

IMPORTANT: If the command contains multiple tasks (e.g. "open chrome and go to youtube"),
return a list of actions. Otherwise return a single action object.

Single action format:
{"action": "ACTION_NAME", "params": {}, "response": "What JARVIS says"}

Multiple actions format:
{"actions": [{"action": "ACTION_NAME", "params": {}}, {"action": "ACTION_NAME", "params": {}}], "response": "What JARVIS says"}

Available actions:
- "open_app": {"app": "chrome|steam|epic|spotify|vscode|notepad|calculator|explorer|taskmgr|discord|whatsapp|word|excel|powerpoint|paint|cmd"}
- "open_web": {"url": "full URL"}
- "search_google": {"query": "search term"}
- "get_time": {}
- "get_date": {}
- "get_battery": {}
- "get_ram": {}
- "get_cpu": {}
- "screenshot": {}
- "read_clipboard": {}
- "summarize_clipboard": {}
- "type_text": {"text": "text to type"}
- "press_key": {"key": "enter|esc|space|tab|ctrl+c|ctrl+v|ctrl+z|ctrl+s|alt+f4|win|f5|delete|volumeup|volumedown|volumemute"}
- "mouse_click": {"x": 0, "y": 0, "button": "left|right|double"}
- "scroll": {"direction": "up|down", "amount": 3}
- "minimize_all": {}
- "maximize_window": {}
- "close_window": {}
- "lock_screen": {}
- "shutdown": {}
- "restart": {}
- "sleep": {}
- "spotify_play": {}
- "spotify_pause": {}
- "spotify_next": {}
- "spotify_prev": {}
- "spotify_volume": {"level": 50}
- "spotify_search": {"query": "song or artist name"}
- "spotify_current": {}
- "memory_stats": {}
- "memory_habits": {}
- "memory_health": {}
- "memory_summary": {}
- "prune_memory": {}
- "add_note": {"text": "note text"}
- "read_notes": {}
- "talk": {}
"""


def _env_flag_enabled(name: str, default: bool = False) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def groq_enabled() -> bool:
    """Return whether optional Groq routing is enabled by configuration."""

    return _env_flag_enabled("JARVIS_ENABLE_GROQ", default=False)


def _resolve_client(provided_client):
    return provided_client if provided_client is not None else client


def get_groq_model() -> str:
    """Return the configured free-first Groq routing model."""

    return os.getenv("JARVIS_GROQ_MODEL", DEFAULT_GROQ_MODEL).strip() or DEFAULT_GROQ_MODEL


def _missing_groq_action() -> dict:
    return {
        "action": "talk",
        "params": {},
        "response": format_actionable_message(
            "Groq API key not found. Running in local-only mode.",
            "AI command routing is disabled because Groq is not configured or not enabled.",
            "Add GROQ_API_KEY to your .env file and set JARVIS_ENABLE_GROQ=true to enable cloud routing.",
        ),
    }


def _local_only_action() -> dict:
    return {
        "action": "talk",
        "params": {},
        "response": format_actionable_message(
            "I am running in local-only mode, sir.",
            "The local router could not handle that command and cloud fallback is disabled or unavailable.",
            "Enable Groq fallback in settings only if you want cloud AI routing.",
        ),
    }


def analyze_with_groq(command, *, client=None, logger=logger):
    """Analyze a command through the local-first provider router."""

    active_client = _resolve_client(client)
    if active_client is None and (not GROQ_API_KEY or not groq_enabled()):
        logger.warning("Groq API key not found. Running in local-only mode.")
        record_runtime_event("groq_missing", "Groq analysis skipped", "warning")
        return _missing_groq_action()

    if active_client is not None:
        return analyze_with_groq_direct(command, client=active_client)

    logger.info("Analyzing command with provider router.")
    record_runtime_event("provider_request", "Analyzing command with provider router", "info", {"command_chars": len(command or "")})
    provider = GroqProvider(
        api_key=GROQ_API_KEY or ("injected-client" if active_client is not None else ""),
        enabled=groq_enabled() or active_client is not None,
        model=get_groq_model(),
        client=active_client,
        activate_cooldown=activate_groq_cooldown,
        system_prompt=SYSTEM_PROMPT,
    )
    response = ProviderRouter(cloud_provider=provider).route(command)
    if response.ok and response.action is not None:
        return response.action
    if response.error == "rate_limited" and response.action is not None:
        return response.action
    return _local_only_action()


def analyze_with_groq_direct(command: str, *, client=None) -> dict:
    """Send a command directly to Groq for compatibility tests."""

    provider = GroqProvider(
        api_key=GROQ_API_KEY or ("injected-client" if client is not None else ""),
        enabled=groq_enabled() or client is not None,
        model=get_groq_model(),
        client=client,
        activate_cooldown=activate_groq_cooldown,
        system_prompt=SYSTEM_PROMPT,
    )
    response = provider.analyze(ProviderRequest(command=command, allow_cloud=True, allow_memory_context=False))
    if response.action:
        return response.action
    return _local_only_action()


def summarize_text(text, *, client=None, logger=logger):
    """Summarize text using Groq when explicitly available."""

    active_client = _resolve_client(client)
    if active_client is None and not GROQ_API_KEY:
        logger.warning("Summarization skipped because GROQ_API_KEY is missing.")
        return None

    provider = GroqProvider(
        api_key=GROQ_API_KEY or ("injected-client" if active_client is not None else ""),
        enabled=groq_enabled() or active_client is not None,
        model=get_groq_model(),
        client=active_client,
    )
    response = provider.summarize(text)
    if response.ok:
        return response.text
    logger.warning("Groq summarization failed: %s", response.error)
    record_runtime_event("summarization_error", "Groq summarization failed", "warning", {"error": response.error})
    return None


__all__ = [
    "DEFAULT_GROQ_MODEL",
    "GROQ_COOLDOWN_SECONDS",
    "GroqError",
    "SYSTEM_PROMPT",
    "activate_groq_cooldown",
    "analyze_with_groq",
    "analyze_with_groq_direct",
    "client",
    "extract_action_json",
    "get_groq_model",
    "groq_enabled",
    "is_groq_cooling_down",
    "summarize_text",
]
