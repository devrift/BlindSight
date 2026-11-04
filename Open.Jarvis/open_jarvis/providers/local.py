"""Deterministic local provider using built-in command rules."""

from __future__ import annotations

from open_jarvis.commands.local_intent_router import route_local_intent
from open_jarvis.providers.base import ProviderRequest, ProviderResponse


class LocalProvider:
    name = "local"

    def __init__(self, *, enabled: bool = True) -> None:
        self.enabled = enabled

    def analyze(self, request: ProviderRequest) -> ProviderResponse:
        if not self.enabled:
            return ProviderResponse(provider=self.name, status="unavailable", error="Local provider is disabled.")
        action = route_local_intent(request.command)
        if action is None:
            return ProviderResponse(provider=self.name, status="unsupported", error="Local provider could not route this command.")
        return ProviderResponse(provider=self.name, status="success", action=action)
