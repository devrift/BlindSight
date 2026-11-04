"""Provider request and response types."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass, field
from typing import Any, Protocol


class ProviderUnavailable(RuntimeError):
    """Raised when a provider cannot be used safely."""


@dataclass(frozen=True, repr=False)
class ProviderRequest:
    """A provider request with a redacted representation."""

    command: str
    context: str = ""
    allow_cloud: bool = False
    allow_memory_context: bool = True
    metadata: Mapping[str, Any] = field(default_factory=dict)

    def __repr__(self) -> str:
        return (
            "ProviderRequest("
            f"command_chars={len(self.command or '')}, "
            f"context_chars={len(self.context or '')}, "
            f"allow_cloud={self.allow_cloud}, "
            f"allow_memory_context={self.allow_memory_context}, "
            f"metadata_keys={sorted(str(key) for key in self.metadata)}"
            ")"
        )


@dataclass(frozen=True, repr=False)
class ProviderResponse:
    """A safe provider result envelope."""

    provider: str
    status: str
    action: dict[str, Any] | None = None
    text: str | None = None
    error: str | None = None
    fallback_used: bool = False
    latency_ms: float | None = None

    @property
    def ok(self) -> bool:
        return self.status == "success"

    def __repr__(self) -> str:
        return (
            "ProviderResponse("
            f"provider={self.provider!r}, "
            f"status={self.status!r}, "
            f"has_action={self.action is not None}, "
            f"has_text={self.text is not None}, "
            f"error={self.error!r}, "
            f"fallback_used={self.fallback_used}, "
            f"latency_ms={self.latency_ms!r}"
            ")"
        )


class BaseProvider(Protocol):
    name: str

    def analyze(self, request: ProviderRequest) -> ProviderResponse:
        """Return a provider response for a command request."""
