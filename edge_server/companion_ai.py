"""BlindSight OpenRouter Multimodal Companion Module.

Provides visual and conversational assistance to visually impaired users
via OpenRouter API (Gemini 2.5 Flash / GPT-4o-mini).
"""

from __future__ import annotations

import base64
import os
import sys
from pathlib import Path
from typing import Any

import requests
from dotenv import find_dotenv, load_dotenv

# Search explicitly in edge_server directory and repository root
SERVER_DIR = Path(__file__).resolve().parent
ROOT_DIR = SERVER_DIR.parent

dotenv_path = SERVER_DIR / ".env"
if not dotenv_path.exists():
    if (ROOT_DIR / ".env").exists():
        dotenv_path = ROOT_DIR / ".env"
    else:
        found = find_dotenv(usecwd=True)
        dotenv_path = Path(found) if found else dotenv_path

if dotenv_path.exists():
    load_dotenv(dotenv_path=dotenv_path, override=True)

OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions"
DEFAULT_MODEL = "google/gemini-2.5-flash"
FALLBACK_MODEL = "openai/gpt-4o-mini"
FALLBACK_REPLY = "I'm having trouble connecting to my vision brain right now."
SYSTEM_PROMPT = (
    "You are BlindSight, a warm, concise, and perceptive visual AI companion "
    "for a visually impaired user. Answer directly in 2 to 3 natural spoken sentences. "
    "Avoid markdown or bullet points."
)


def build_system_prompt(language_label: str = "English", memory: list[str] | None = None) -> str:
    """System prompt with the reply language and the user's stored facts folded in.

    The language line is what makes the language switch real: the model answers
    in the chosen language, and the gateway's espeak-ng voice matches it.
    Memory facts are quoted verbatim so the companion can use names, rooms and
    preferences the user taught it earlier.
    """
    prompt = SYSTEM_PROMPT
    if language_label and language_label != "English":
        prompt += f" Always reply in {language_label}, using natural spoken {language_label}."
    facts = [f for f in (memory or []) if f.strip()]
    if facts:
        prompt += " Facts the user taught you earlier, which you may rely on: " + "; ".join(facts) + "."
    return prompt


class CompanionResult:
    """A reply plus enough context for the caller to tell success from failure.

    The gateway used to receive only a string, so a provider outage was
    indistinguishable from a real answer: it returned FALLBACK_REPLY with HTTP
    200 and the PWA spoke the failure message aloud in the assistant's voice as
    though it had described the scene.
    """

    __slots__ = ("ok", "reason", "reply")

    def __init__(self, reply: str, ok: bool, reason: str = "") -> None:
        self.reply = reply
        self.ok = ok
        self.reason = reason

    def __repr__(self) -> str:  # pragma: no cover - debugging aid
        return f"CompanionResult(ok={self.ok}, reason={self.reason!r}, reply={self.reply[:40]!r})"


def _get_api_key() -> str:
    return (os.environ.get("OPENROUTER_API_KEY") or "").strip("'\" \t\r\n")


def _build_user_content(prompt: str, image_path: str | Path | None) -> Any:
    """Returns a plain string, or a text+image parts list when an image is usable."""
    if not image_path:
        return prompt

    img_p = Path(image_path)
    if not img_p.is_file():
        return prompt

    try:
        img_bytes = img_p.read_bytes()
        b64_data = base64.b64encode(img_bytes).decode("utf-8")
        mime = "image/png" if img_p.suffix.lower() == ".png" else "image/jpeg"
    except OSError:
        return prompt

    return [
        {"type": "text", "text": prompt},
        {"type": "image_url", "image_url": {"url": f"data:{mime};base64,{b64_data}"}},
    ]


def ask_companion_detailed(
    prompt: str,
    image_path: str | Path | None = None,
    history: list[dict[str, str]] | None = None,
    system_prompt: str | None = None,
) -> CompanionResult:
    """Sends a conversational or vision query to OpenRouter.

    `history` carries earlier turns (oldest first, {"role", "content"}) so the
    model sees an actual conversation instead of one isolated question.
    Returns a CompanionResult whose `ok` flag is True only when a model actually
    produced the reply.
    """
    api_key = _get_api_key()
    if not api_key:
        print("[ERROR] OPENROUTER_API_KEY not found in environment or .env file.", file=sys.stderr)
        return CompanionResult(FALLBACK_REPLY, ok=False, reason="missing_api_key")

    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
        "HTTP-Referer": "https://github.com/blindsight",
        "X-Title": "BlindSight Voice Companion",
    }

    messages = [{"role": "system", "content": system_prompt or SYSTEM_PROMPT}]
    for turn in history or []:
        role = turn.get("role")
        content = turn.get("content")
        if role in ("user", "assistant") and isinstance(content, str) and content.strip():
            messages.append({"role": role, "content": content})
    messages.append({"role": "user", "content": _build_user_content(prompt, image_path)})

    last_reason = "no_response"
    for model_name in (DEFAULT_MODEL, FALLBACK_MODEL):
        payload = {
            "model": model_name,
            "messages": messages,
            "max_tokens": 150,
            "temperature": 0.5,
        }
        try:
            resp = requests.post(
                OPENROUTER_API_URL,
                headers=headers,
                json=payload,
                timeout=12,
            )
        except requests.RequestException as err:
            last_reason = f"request_failed:{type(err).__name__}"
            print(f"[ERROR] OpenRouter request exception: {err}", file=sys.stderr)
            continue

        if resp.status_code != 200:
            last_reason = f"http_{resp.status_code}"
            print(f"[ERROR] OpenRouter error {resp.status_code}: {resp.text}", file=sys.stderr)
            continue

        try:
            data = resp.json()
        except ValueError:
            last_reason = "non_json_response"
            print("[ERROR] OpenRouter returned a non-JSON body", file=sys.stderr)
            continue

        reply = data.get("choices", [{}])[0].get("message", {}).get("content", "")
        if reply and reply.strip():
            return CompanionResult(reply.strip(), ok=True)
        last_reason = "empty_reply"

    return CompanionResult(FALLBACK_REPLY, ok=False, reason=last_reason)


def ask_companion(prompt: str, image_path: str | Path | None = None) -> str:
    """Backwards-compatible wrapper returning only the reply text."""
    return ask_companion_detailed(prompt, image_path=image_path).reply
