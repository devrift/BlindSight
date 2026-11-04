"""Command dispatch package for JARVIS."""

from .action_dispatcher import execute_action as execute_action
from .action_dispatcher import execute_single_action as execute_single_action

__all__ = ["execute_action", "execute_single_action"]
