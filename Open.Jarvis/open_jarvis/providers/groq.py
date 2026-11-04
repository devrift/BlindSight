"""Groq provider adapter isolated behind the provider interface."""

from __future__ import annotations

import json
import time
from collections.abc import Callable
from typing import Any

from open_jarvis.providers.base import ProviderRequest, ProviderResponse
from open_jarvis.security.jarvis_admin import format_actionable_message

try:
    from groq import GroqError
except ImportError:  # pragma: no cover - optional dependency is installed in normal dev/test flows.
    GroqError = RuntimeError

DEFAULT_GROQ_MODEL = "llama-3.1-8b-instant"
GROQ_COOLDOWN_SECONDS = 120
_groq_cooldown_until = 0.0


def safe_provider_error(error: object) -> str:
    """Return a bounded provider error that never includes raw exception text."""

    text = str(error).lower()
    if "rate" in text or "quota" in text or "429" in text:
        return "rate_limited"
    if "api" in text or "key" in text or "auth" in text:
        return "provider_auth_failed"
    return "provider_error"


def extract_action_json(text: str) -> dict[str, Any]:
    """Extract the first JSON object from a model response."""

    value = (text or "").strip()
    if "```" in value:
        chunks = value.split("```")
        value = next((chunk[4:].strip() if chunk.startswith("json") else chunk.strip() for chunk in chunks if "{" in chunk), value)

    start = value.find("{")
    end = value.rfind("}")
    if start == -1 or end == -1 or end < start:
        raise ValueError("No JSON object found in provider response")
    return json.loads(value[start : end + 1])


def is_groq_cooling_down(now: float | None = None) -> bool:
    """Return True when Groq should be skipped after a recent rate-limit error."""

    return (time.time() if now is None else now) < _groq_cooldown_until


def activate_groq_cooldown(seconds: int = GROQ_COOLDOWN_SECONDS, now: float | None = None) -> None:
    """Temporarily avoid Groq after free-tier/rate-limit failures."""

    global _groq_cooldown_until
    _groq_cooldown_until = (time.time() if now is None else now) + max(1, seconds)


def rate_limit_action() -> dict[str, Any]:
    return {
        "action": "talk",
        "params": {},
        "response": format_actionable_message(
            "I reached the free Groq quota for the moment, sir.",
            "Cloud AI routing is cooling down to avoid repeatedly hitting the free-tier limit.",
            "I will keep handling simple commands locally and try cloud routing again shortly.",
        ),
    }


class GroqProvider:
    name = "groq"

    def __init__(
        self,
        *,
        api_key: str | None = None,
        enabled: bool = False,
        model: str = DEFAULT_GROQ_MODEL,
        client: Any = None,
        client_factory: Callable[[str], Any] | None = None,
        activate_cooldown: Callable[[], None] = activate_groq_cooldown,
        system_prompt: str = "",
    ) -> None:
        self.api_key = (api_key or "").strip()
        self.enabled = enabled
        self.model = model.strip() or DEFAULT_GROQ_MODEL
        self.client = client
        self.client_factory = client_factory
        self.activate_cooldown = activate_cooldown
        self.system_prompt = system_prompt

    def analyze(self, request: ProviderRequest) -> ProviderResponse:
        if not request.allow_cloud:
            return ProviderResponse(provider=self.name, status="unavailable", error="Cloud provider use is disabled.")
        if not self.enabled:
            return ProviderResponse(provider=self.name, status="unavailable", error="Groq provider is disabled.")
        if not self.api_key:
            return ProviderResponse(provider=self.name, status="unavailable", error="Groq API key is missing.")
        if is_groq_cooling_down():
            return ProviderResponse(provider=self.name, status="error", action=rate_limit_action(), error="rate_limited")

        try:
            active_client = self._client()
        except (RuntimeError, ValueError, TypeError, AttributeError, OSError) as exc:
            return ProviderResponse(provider=self.name, status="error", error=safe_provider_error(exc))
        if active_client is None:
            return ProviderResponse(provider=self.name, status="unavailable", error="Groq client is unavailable.")

        started = time.perf_counter()
        try:
            system = f"{request.context}\n\n{self.system_prompt}" if request.context else self.system_prompt
            response = active_client.chat.completions.create(
                model=self.model,
                messages=[
                    {"role": "system", "content": system},
                    {"role": "user", "content": request.command},
                ],
                temperature=0.1,
                max_tokens=500,
            )
            action = extract_action_json(response.choices[0].message.content)
            return ProviderResponse(
                provider=self.name,
                status="success",
                action=action,
                latency_ms=(time.perf_counter() - started) * 1000,
            )
        except (GroqError, RuntimeError, json.JSONDecodeError, ValueError, AttributeError, OSError) as exc:
            error = safe_provider_error(exc)
            if error == "rate_limited":
                self.activate_cooldown()
                return ProviderResponse(provider=self.name, status="error", action=rate_limit_action(), error=error)
            return ProviderResponse(provider=self.name, status="error", error=error)

    def summarize(self, text: str) -> ProviderResponse:
        if not self.enabled or not self.api_key:
            return ProviderResponse(provider=self.name, status="unavailable", error="Groq provider is unavailable.")
        try:
            active_client = self._client()
        except (RuntimeError, ValueError, TypeError, AttributeError, OSError) as exc:
            return ProviderResponse(provider=self.name, status="error", error=safe_provider_error(exc))
        if active_client is None:
            return ProviderResponse(provider=self.name, status="unavailable", error="Groq client is unavailable.")
        try:
            value = text[:4000] if len(text) > 4000 else text
            response = active_client.chat.completions.create(
                model=self.model,
                messages=[{"role": "user", "content": f"Summarize this in 3-4 sentences as JARVIS would:\n\n{value}"}],
                temperature=0.1,
                max_tokens=500,
            )
            return ProviderResponse(provider=self.name, status="success", text=response.choices[0].message.content.strip())
        except (GroqError, RuntimeError, AttributeError, OSError) as exc:
            return ProviderResponse(provider=self.name, status="error", error=safe_provider_error(exc))

    def _client(self):
        if self.client is not None:
            return self.client
        if self.client_factory is not None:
            self.client = self.client_factory(self.api_key)
            return self.client
        try:
            from groq import Groq
        except ImportError:
            return None
        self.client = Groq(api_key=self.api_key)
        return self.client
