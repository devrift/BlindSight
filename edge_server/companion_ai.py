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


def ask_companion(prompt: str, image_path: str | Path | None = None) -> str:
    """Sends a conversational or vision query to OpenRouter and returns spoken-ready text."""
    api_key = (os.environ.get("OPENROUTER_API_KEY") or "").strip("'\" \t\r\n")
    if not api_key:
        print("[ERROR] OPENROUTER_API_KEY not found in environment or .env file.", file=sys.stderr)
        return FALLBACK_REPLY

    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
        "HTTP-Referer": "https://github.com/blindsight",
        "X-Title": "BlindSight Voice Companion",
    }

    user_content: Any = prompt
    if image_path:
        img_p = Path(image_path)
        if img_p.is_file():
            try:
                img_bytes = img_p.read_bytes()
                b64_data = base64.b64encode(img_bytes).decode("utf-8")
                mime = "image/png" if img_p.suffix.lower() == ".png" else "image/jpeg"
                user_content = [
                    {"type": "text", "text": prompt},
                    {
                        "type": "image_url",
                        "image_url": {"url": f"data:{mime};base64,{b64_data}"},
                    },
                ]
            except Exception:
                user_content = prompt

    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": user_content},
    ]

    for model_name in [DEFAULT_MODEL, FALLBACK_MODEL]:
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
            if resp.status_code == 200:
                data = resp.json()
                reply = data.get("choices", [{}])[0].get("message", {}).get("content", "")
                if reply:
                    return reply.strip()
            else:
                print(f"[ERROR] OpenRouter error {resp.status_code}: {resp.text}", file=sys.stderr)
        except Exception as err:
            print(f"[ERROR] OpenRouter request exception: {err}", file=sys.stderr)
            continue

    return FALLBACK_REPLY
