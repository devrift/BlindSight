"""
BlindSight Voice AI - 100% Local, Permanent, No API Keys.
Replaces OpenRouter with faster-whisper + Ollama (llama3.2:1b) + Piper / svara-TTS.
"""
from __future__ import annotations

import io
import json
import os
import subprocess
import tempfile
import wave
from pathlib import Path

import requests

SERVER_DIR = Path(__file__).resolve().parent

# --- Configuration ---
OLLAMA_URL = os.getenv("OLLAMA_URL", "http://localhost:11434/api/chat")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "llama3.2:1b")
SVARA_URL = os.getenv("SVARA_URL", "http://localhost:8081/v1/audio/speech")

SYSTEM_PROMPT = """You are BlindSight, a voice assistant for blind users.
Rules:
- Keep responses under 2 sentences
- Reply in the SAME language the user speaks (Hindi, English, Marathi, etc.)
- Be direct, compassionate, and helpful
- If the user asks about surroundings or navigation, use the vision context provided
- For navigation: say "left", "right", "straight", "stop" clearly
"""

# Global model caches
_whisper_model = None
_piper_voices: dict[str, any] = {}


def get_whisper_model():
    """Lazy loader for faster-whisper to keep initial startup fast."""
    global _whisper_model
    if _whisper_model is None:
        try:
            from faster_whisper import WhisperModel
            print("Loading faster-whisper (STT) on CPU int8...")
            _whisper_model = WhisperModel("base", device="cpu", compute_type="int8")
        except Exception as e:
            print(f"Warning: faster-whisper not initialized: {e}")
            return None
    return _whisper_model


def speech_to_text(audio_bytes: bytes) -> tuple[str, str]:
    """Convert audio bytes (WAV/WEBM/OGG/MP3) to text.
    
    Returns (transcript, detected_language_code).
    """
    model = get_whisper_model()
    if model is None:
        return "", "en"

    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        f.write(audio_bytes)
        tmp_path = f.name

    try:
        segments, info = model.transcribe(tmp_path, language=None)
        text = " ".join(s.text.strip() for s in segments)
        lang = getattr(info, "language", "en") or "en"
        return text.strip(), lang
    except Exception as e:
        print(f"STT error: {e}")
        return "", "en"
    finally:
        with context_suppress():
            os.unlink(tmp_path)


def get_llm_response(user_text: str, vision_context: str = "", lang: str = "en") -> str:
    """Query local Ollama LLM (llama3.2:1b)."""
    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
    ]
    if vision_context:
        messages.append({"role": "system", "content": f"Vision observation: {vision_context}"})
    messages.append({"role": "user", "content": user_text})

    try:
        resp = requests.post(
            OLLAMA_URL,
            json={
                "model": OLLAMA_MODEL,
                "messages": messages,
                "stream": False,
                "options": {
                    "num_predict": 90,  # Max 90 tokens = ~2 concise sentences
                    "temperature": 0.3,
                },
            },
            timeout=25,
        )
        if resp.ok:
            data = resp.json()
            reply = data.get("message", {}).get("content", "").strip()
            if reply:
                return reply
    except Exception as e:
        print(f"Ollama error: {e}")

    # Fallback if Ollama is loading or unreachable
    if lang == "hi":
        return "मैं सुन रहा हूँ। आपका मार्ग सुरक्षित रखने के लिए मैं यहाँ हूँ।"
    return "I am listening. I am here to help you navigate and stay safe."


def text_to_speech(text: str, language: str = "en") -> bytes:
    """Convert text to WAV audio bytes.
    
    Uses Piper for Hindi & English (~200ms on Pi CPU).
    Uses svara-TTS for Marathi, Bengali, Tamil, Telugu, and other Indic languages.
    Falls back to synthetic WAV tone/espeak if neither is available.
    """
    clean_text = text.replace("*", "").replace("#", "").strip()
    if not clean_text:
        return _generate_silent_wav()

    # 1. Hindi and English: Fast Piper TTS
    if language in ("hi", "en"):
        audio = _piper_tts(clean_text, language)
        if audio:
            return audio

    # 2. Indic languages: svara-TTS GGUF
    audio = _svara_tts(clean_text, language)
    if audio:
        return audio

    # 3. Fallback: espeak-ng or basic speech generator
    return _espeak_fallback(clean_text, language)


def _piper_tts(text: str, language: str) -> bytes | None:
    """Synthesize speech using Piper TTS."""
    voice_name = "hi_IN-pratham-medium" if language == "hi" else "en_US-lessac-medium"

    # Try Piper CLI first (standard installation)
    try:
        # Check standard model paths
        candidates = [
            Path(f"/home/pi/piper_voices/{voice_name}.onnx"),
            Path(f"{voice_name}.onnx"),
            Path(f"/usr/share/piper-voices/{voice_name}.onnx"),
            SERVER_DIR / "models" / f"{voice_name}.onnx",
        ]
        model_path = next((p for p in candidates if p.is_file()), None)

        cmd = ["piper", "--model", str(model_path) if model_path else voice_name, "--output_file", "-"]
        proc = subprocess.Popen(
            cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
        )
        stdout, _ = proc.communicate(input=text.encode("utf-8"), timeout=10)
        if proc.returncode == 0 and len(stdout) > 44:  # Valid WAV header
            return stdout
    except Exception:
        pass

    # Try Python piper library if installed
    try:
        import piper
        global _piper_voices
        if voice_name not in _piper_voices:
            _piper_voices[voice_name] = piper.PiperVoice.load(voice_name)
        voice = _piper_voices[voice_name]

        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
            voice.synthesize(text, f)
            f.flush()
            audio = f.read()
            tmp_name = f.name
        with context_suppress():
            os.unlink(tmp_name)
        if len(audio) > 44:
            return audio
    except Exception:
        pass

    return None


def _svara_tts(text: str, language: str) -> bytes | None:
    """svara-TTS via OpenAI-compatible endpoint (port 8081 or 8000)."""
    lang_map = {
        "mr": "Marathi",
        "bn": "Bengali",
        "ta": "Tamil",
        "te": "Telugu",
        "kn": "Kannada",
        "ml": "Malayalam",
        "gu": "Gujarati",
        "pa": "Punjabi",
        "as": "Assamese",
        "hi": "Hindi",
        "en": "English",
    }
    speaker = f"{lang_map.get(language, 'Hindi')} (Female)"

    for endpoint in (SVARA_URL, "http://localhost:8000/v1/audio/speech"):
        try:
            resp = requests.post(
                endpoint,
                json={
                    "model": "svara-tts-v1",
                    "voice": speaker,
                    "input": text,
                    "response_format": "wav",
                },
                timeout=15,
            )
            if resp.ok and len(resp.content) > 44:
                return resp.content
        except Exception:
            continue
    return None


def _espeak_fallback(text: str, language: str) -> bytes:
    """Lightweight espeak-ng fallback to guarantee audio is always returned."""
    try:
        lang_flag = "hi" if language == "hi" else "en"
        cmd = ["espeak-ng", f"-v{lang_flag}", "-s155", "--stdout", text]
        result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=5)
        if result.returncode == 0 and len(result.stdout) > 44:
            return result.stdout
    except Exception:
        pass
    return _generate_silent_wav()


def _generate_silent_wav() -> bytes:
    """Generate 0.2s of silent WAV so response is always valid audio."""
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        w.writeframes(b"\x00" * 3200)
    return buf.getvalue()


def process_voice(audio_bytes: bytes, vision_context: str = "") -> bytes:
    """Complete voice pipeline: audio bytes in -> audio bytes out."""
    # 1. Speech to Text
    text, lang = speech_to_text(audio_bytes)
    print(f"Voice AI heard ({lang}): {text}")

    if not text.strip():
        fallback = "मैं आपकी बात नहीं समझ पाया। कृपया पुनः बोलें।" if lang == "hi" else "I did not catch that. Please try again."
        return text_to_speech(fallback, lang)

    # 2. Local LLM Reasoning (llama3.2:1b)
    reply = get_llm_response(text, vision_context=vision_context, lang=lang)
    print(f"Voice AI replied: {reply}")

    # 3. Natural Neural Text to Speech
    return text_to_speech(reply, lang)


class context_suppress:
    """Simple context manager to ignore file deletion errors."""
    def __enter__(self):
        return self
    def __exit__(self, exc_type, exc_val, exc_tb):
        return True
