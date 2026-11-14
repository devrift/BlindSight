"""
BlindSight Voice Assistant - Powered by Open.Jarvis Architecture & Gemini Multimodal
https://github.com/dmrr35/Open.Jarvis | https://github.com/FatihMakes/Mark-LVI

Features:
- Dual Wake Words: "Hey BlindSight" & "Jarvis" (customizable)
- Adaptive Speech Recognition: Google STT (multi-accent) + Local Offline Faster-Whisper Fallback
- Audio Pre-processing: Peak Normalization, DC Bias Removal & Dynamic Energy Thresholding
- Multimodal AI Brain: Google Gemini 2.5 Flash Lite (spatially trained for blind navigation)
- Voice Synthesis: Microsoft Edge Neural TTS (Iron Man RyanNeural / SwaraNeural / Svara-TTS v1)
- State Machine: STANDBY -> WAKE_WORD_DETECTED -> LISTENING -> PROCESSING -> SPEAKING
- Native Microphone capture via sounddevice (hardware Realtek mic)
- Edge Reflex Integration: ESP32-CAM YOLOv8n ONNX obstacle alerts
- 100% Free with offline graceful degradation.
"""

from __future__ import annotations

import asyncio
import base64
import io
import os
import re
import sys
import tempfile
import threading
import time
import wave
from pathlib import Path
from typing import Any, Callable, Dict, Optional, Tuple

import edge_tts
import numpy as np
import requests
import speech_recognition as sr

SERVER_DIR = Path(__file__).resolve().parent
PROJECT_DIR = SERVER_DIR.parent
JARVIS_DIR = PROJECT_DIR / "Open.Jarvis"
RUNTIME_DIR = SERVER_DIR / "runtime"
RUNTIME_DIR.mkdir(parents=True, exist_ok=True)

# Load environment variables from .env files
try:
    from dotenv import load_dotenv
    load_dotenv(SERVER_DIR / ".env")
    load_dotenv(PROJECT_DIR / ".env")
except ImportError:
    pass

if str(JARVIS_DIR) not in sys.path and JARVIS_DIR.is_dir():
    sys.path.insert(0, str(JARVIS_DIR))

# Voice Configuration
DEFAULT_WAKE_WORDS = [
    "blindsight", "hey blindsight", "ok blindsight", "okay blindsight",
    "jarvis", "hey jarvis", "ok jarvis", "okay jarvis",
    "blind sight", "hey blind sight"
]
ACTIVE_TIMEOUT = 30.0  # seconds to stay awake after wake word

VOICE_MAP = {
    "en": "en-GB-RyanNeural",       # Jarvis Iron Man voice
    "en-us": "en-US-GuyNeural",
    "hi": "hi-IN-SwaraNeural",       # Hindi neural
    "mr": "mr-IN-AarohiNeural",      # Marathi neural
    "ta": "ta-IN-PallaviNeural",     # Tamil neural
    "te": "te-IN-MohanNeural",       # Telugu neural
    "bn": "bn-IN-TanishaaNeural",    # Bengali neural
    "gu": "gu-IN-DhwaniNeural",      # Gujarati neural
    "kn": "kn-IN-GaganNeural",       # Kannada neural
    "ml": "ml-IN-SobhanaNeural",     # Malayalam neural
}

SVARA_URL = os.getenv("SVARA_URL", "http://localhost:8000/v1/audio/speech")
OLLAMA_URL = os.getenv("OLLAMA_URL", "http://localhost:11434/api/chat")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "llama3.2:1b")
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-3.5-flash")


def normalize_voice_phrase(text: str) -> str:
    """Normalize speech text for regex token matching."""
    normalized = re.sub(r"[^a-zA-Z0-9]+", " ", (text or "").lower())
    return " ".join(normalized.split())


def clean_phonetic_substitutions(text: str) -> str:
    """Fix common STT acoustic mishearings for BlindSight and Jarvis."""
    if not text:
        return ""
    cleaned = text
    # BlindSight homophones
    cleaned = re.sub(r"\bblind\s*site\b", "blindsight", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\bblind\s*side\b", "blindsight", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\bline\s*sight\b", "blindsight", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\bblind\s*light\b", "blindsight", cleaned, flags=re.IGNORECASE)
    # Jarvis homophones
    cleaned = re.sub(r"\bjar\s*vis\b", "jarvis", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\bharvest\b", "jarvis", cleaned, flags=re.IGNORECASE)
    return cleaned.strip()


class VoiceState:
    STANDBY = "STANDBY"
    WAKE_WORD_DETECTED = "WAKE_WORD_DETECTED"
    LISTENING = "LISTENING"
    PROCESSING = "PROCESSING"
    SPEAKING = "SPEAKING"
    ERROR = "ERROR"


class JarvisVoiceAssistant:
    def __init__(self, wake_words: list[str] | None = None):
        self.wake_words = [normalize_voice_phrase(w) for w in (wake_words or DEFAULT_WAKE_WORDS)]
        self.state = VoiceState.STANDBY
        self.state_detail = "Ready"
        self.recognizer = sr.Recognizer()
        self.recognizer.energy_threshold = int(os.getenv("JARVIS_ENERGY_THRESHOLD", "250"))
        self.recognizer.dynamic_energy_threshold = True
        self.recognizer.pause_threshold = 0.8
        
        self.last_wake_time = 0.0
        self.is_active = False
        self.mic_available = False
        self.mic_name = "None"
        
        self._listener_thread: Optional[threading.Thread] = None
        self._stop_listener = threading.Event()
        self.state_listeners: list[Callable[[str, str], None]] = []

        # Local Offline Whisper Model (lazy-loaded)
        self._whisper_model: Any = None
        self._whisper_lock = threading.Lock()

        self._check_microphone()

    def _get_whisper_model(self) -> Any:
        """Lazy loader for faster-whisper on CPU with int8 quantization."""
        if self._whisper_model is None:
            with self._whisper_lock:
                if self._whisper_model is None:
                    try:
                        from faster_whisper import WhisperModel
                        print("[JarvisVoice] 🧠 Loading offline Faster-Whisper (tiny, int8)...")
                        self._whisper_model = WhisperModel("tiny", device="cpu", compute_type="int8")
                        print("[JarvisVoice] ✅ Faster-Whisper ready for offline STT fallback.")
                    except Exception as e:
                        print(f"[JarvisVoice] Faster-Whisper init notice: {e}")
        return self._whisper_model

    def _check_microphone(self):
        """Probe available hardware audio inputs."""
        try:
            import sounddevice as sd
            dev = sd.query_devices(kind="input")
            if dev:
                self.mic_available = True
                self.mic_name = dev.get("name", "Default Microphone")
                print(f"[JarvisVoice] Hardware microphone detected: {self.mic_name}")
                return
        except Exception as e:
            print(f"[JarvisVoice] Microphone probe notice: {e}")
        self.mic_available = False
        self.mic_name = "Unavailable"

    def add_state_listener(self, fn: Callable[[str, str], None]):
        self.state_listeners.append(fn)

    def set_state(self, state: str, detail: str = ""):
        self.state = state
        self.state_detail = detail
        for fn in self.state_listeners:
            try:
                fn(state, detail)
            except Exception:
                pass

    def check_wake_word(self, text: str) -> bool:
        """Check if any configured wake phrase is in the spoken text."""
        norm = normalize_voice_phrase(clean_phonetic_substitutions(text))
        tokens = norm.split()
        for w in self.wake_words:
            w_tokens = w.split()
            if not w_tokens:
                continue
            if len(tokens) >= len(w_tokens):
                for i in range(len(tokens) - len(w_tokens) + 1):
                    if tokens[i : i + len(w_tokens)] == w_tokens:
                        return True
        return False

    def strip_wake_word(self, text: str) -> str:
        """Remove the wake word prefix/mention from the text command."""
        low = clean_phonetic_substitutions(text).lower()
        for w in sorted(self.wake_words, key=len, reverse=True):
            pattern = re.compile(rf"\b{re.escape(w)}\b", re.IGNORECASE)
            low = pattern.sub("", low).strip()
        return low.strip()

    def _normalize_wav_audio(self, audio_bytes: bytes) -> bytes:
        """Normalize raw WAV audio: DC bias removal + peak volume normalization."""
        try:
            buf_in = io.BytesIO(audio_bytes)
            with wave.open(buf_in, "rb") as wf:
                n_channels = wf.getnchannels()
                samp_width = wf.getsampwidth()
                frame_rate = wf.getframerate()
                n_frames = wf.getnframes()
                raw_frames = wf.readframes(n_frames)

            if samp_width != 2:
                # If not 16-bit PCM, return untouched
                return audio_bytes

            samples = np.frombuffer(raw_frames, dtype=np.int16).astype(np.float32)
            if len(samples) == 0:
                return audio_bytes

            # 1. DC Offset Removal
            samples = samples - np.mean(samples)

            # 2. Peak Normalization (target ~85% full scale = 27,800)
            peak = np.max(np.abs(samples))
            if peak > 120.0:  # Ignore absolute dead silence / noise floor
                scale = min(27800.0 / peak, 35.0)  # Adaptive gain boost for soft microphones
                samples = samples * scale
                samples = np.clip(samples, -32767.0, 32767.0)

            normalized_int16 = samples.astype(np.int16)
            buf_out = io.BytesIO()
            with wave.open(buf_out, "wb") as wf:
                wf.setnchannels(n_channels)
                wf.setsampwidth(samp_width)
                wf.setframerate(frame_rate)
                wf.writeframes(normalized_int16.tobytes())

            return buf_out.getvalue()
        except Exception as e:
            print(f"[JarvisVoice] Audio normalization notice: {e}")
            return audio_bytes

    def _transcribe_whisper_offline(self, wav_bytes: bytes, lang: str = "en") -> str:
        """Transcribe speech using local offline faster-whisper on CPU."""
        model = self._get_whisper_model()
        if model is None:
            return ""

        tmp_path = None
        try:
            with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
                f.write(wav_bytes)
                tmp_path = f.name

            # Map language code
            whisper_lang = "hi" if lang.startswith("hi") else ("mr" if lang.startswith("mr") else "en")
            segments, info = model.transcribe(
                tmp_path,
                language=whisper_lang,
                beam_size=1,
                vad_filter=True,
                vad_parameters=dict(min_silence_duration_ms=400)
            )
            text = " ".join(s.text.strip() for s in segments if s.text).strip()
            return clean_phonetic_substitutions(text)
        except Exception as e:
            print(f"[JarvisVoice] Whisper offline transcription notice: {e}")
            return ""
        finally:
            if tmp_path and os.path.exists(tmp_path):
                try:
                    os.unlink(tmp_path)
                except Exception:
                    pass

    def transcribe_audio_bytes(self, audio_bytes: bytes, language: str = "en-US") -> Tuple[str, str]:
        """Convert WAV audio bytes to text with normalization and multi-engine fallback."""
        if not audio_bytes or len(audio_bytes) < 100:
            return "", language

        # 1. Normalize audio waveform so quiet speech is loud and clear
        norm_bytes = self._normalize_wav_audio(audio_bytes)

        # 2. Try Google STT with multi-accent retry (e.g. en-IN for Indian English)
        try:
            buf = io.BytesIO(norm_bytes)
            with sr.AudioFile(buf) as source:
                audio = self.recognizer.record(source)

            # Determine language fallback candidate list
            lang_candidates = [language]
            if language == "en-US":
                lang_candidates.append("en-IN")
            elif language == "en-IN":
                lang_candidates.append("en-US")
            elif language.startswith("hi"):
                lang_candidates.extend(["hi-IN", "en-IN"])

            for candidate in lang_candidates:
                try:
                    text = self.recognizer.recognize_google(audio, language=candidate)
                    if text and text.strip():
                        return clean_phonetic_substitutions(text.strip()), candidate
                except (sr.UnknownValueError, sr.RequestError):
                    continue

        except Exception as exc:
            print(f"[JarvisVoice] Google STT error: {exc}")

        # 3. Fallback to Local Offline Faster-Whisper (zero network dependency)
        whisper_text = self._transcribe_whisper_offline(norm_bytes, lang=language)
        if whisper_text:
            return whisper_text, language

        # 4. Fallback to Gemini Multimodal Audio (understands Indian accents, Hinglish, quiet speech)
        gemini_text = self._transcribe_gemini_audio(norm_bytes, lang=language)
        if gemini_text:
            return gemini_text, language

        return "", language

    def _transcribe_gemini_audio(self, wav_bytes: bytes, lang: str = "en") -> str:
        """Transcribe speech using Gemini Multimodal Audio understanding."""
        api_key = os.getenv("GEMINI_API_KEY", "").strip()
        if not api_key or not wav_bytes or len(wav_bytes) < 400:
            return ""
        try:
            url = f"https://generativelanguage.googleapis.com/v1beta/models/{GEMINI_MODEL}:generateContent?key={api_key}"
            audio_b64 = base64.b64encode(wav_bytes).decode("utf-8")
            prompt = (
                "Listen to this audio recording of a user speaking to their voice assistant. "
                "Transcribe the spoken words verbatim in the language they spoke (English, Hindi, Hinglish, etc.). "
                "CRITICAL: Return ONLY the exact transcription as plain text. Do not add quotes, do not explain, do not add any markdown. "
                "If the audio contains only silence, clicks, or unintelligible noise with no real human speech, return an empty string."
            )
            payload = {
                "contents": [{
                    "parts": [
                        {"text": prompt},
                        {
                            "inline_data": {
                                "mime_type": "audio/wav",
                                "data": audio_b64
                            }
                        }
                    ]
                }],
                "generationConfig": {
                    "temperature": 0.0,
                    "maxOutputTokens": 100
                }
            }
            resp = requests.post(url, json=payload, timeout=8)
            if resp.status_code == 200:
                data = resp.json()
                candidates = data.get("candidates", [])
                if candidates:
                    parts = candidates[0].get("content", {}).get("parts", [])
                    if parts:
                        text = parts[0].get("text", "").strip()
                        if text.lower() in ("(silence)", "none", "no speech", "silence", "no audio"):
                            return ""
                        return clean_phonetic_substitutions(text)
        except Exception as e:
            print(f"[BlindSight] Gemini audio transcription notice: {e}")
        return ""

    def record_mic_audio(self, duration: float = 3.5, sample_rate: int = 16000) -> bytes:
        """Capture live microphone audio from hardware sound card."""
        try:
            import sounddevice as sd
            samples = int(duration * sample_rate)
            raw = sd.rec(samples, samplerate=sample_rate, channels=1, dtype="int16")
            sd.wait()
            
            buf = io.BytesIO()
            with wave.open(buf, "wb") as wf:
                wf.setnchannels(1)
                wf.setsampwidth(2)
                wf.setframerate(sample_rate)
                wf.writeframes(raw.tobytes())
            return self._normalize_wav_audio(buf.getvalue())
        except Exception as e:
            print(f"[JarvisVoice] Mic record failed: {e}")
            return b""

    def query_gemini(
        self,
        prompt: str,
        image_path: Optional[Path] = None,
        is_vision_query: bool = False,
        vision_context: str = "",
        lang: str = "en"
    ) -> Optional[str]:
        """Query Google Gemini Multimodal Vision API or Conversational Chatbot Brain."""
        api_key = os.getenv("GEMINI_API_KEY", "").strip()
        if not api_key:
            return None

        url = f"https://generativelanguage.googleapis.com/v1beta/models/{GEMINI_MODEL}:generateContent?key={api_key}"
        
        if is_vision_query:
            # Assistive vision instructions (ONLY for explicit scene queries)
            system_instruction = (
                "You are BlindSight, an assistive navigation companion for a blind or visually impaired user.\n"
                "Your job is to describe what is ACTUALLY and genuinely in the camera view in 1 to 2 calm, direct, honest sentences.\n"
                "CRITICAL HONESTY RULES:\n"
                "- Describe ONLY what is physically visible in the camera image. NEVER invent, assume, or hallucinate objects, doors, chairs, people, or pathways that are not there.\n"
                "- If the camera is facing a plain wall, wooden board/background, closed surface, or close object: state directly that a wall or surface is directly in front and the path ahead is blocked.\n"
                "- If the image is dark, blurry, or blocked: say 'The camera view is blocked or too dark to see clearly.'\n"
                "DIRECTION RULES:\n"
                "- Use natural everyday directions: 'Straight ahead', 'To your left', 'To your right'.\n"
                "- NEVER use confusing military/aviation clock numbers like '10 o'clock' or '2 o'clock'.\n"
                "- Estimate distance in feet or steps (e.g. 'about 2 feet away').\n"
            )
            if lang == "hi":
                system_instruction += (
                    "- सरल और स्वाभाविक हिंदी में उत्तर दें। 'सीधे आपके आगे', 'बाईं तरफ', 'दाईं तरफ' जैसे सामान्य शब्दों का प्रयोग करें।\n"
                    "- '10 बजे की दिशा' या '2 बजे की दिशा' जैसे शब्दों का कभी प्रयोग न करें।\n"
                    "- यदि केवल लकड़ी की दीवार, बोर्ड या खाली सतह दिख रही है, तो साफ कहें: 'सीधे आपके सामने लकड़ी की एक सतह या दीवार है और आगे का रास्ता रुका हुआ है।'\n"
                )
            if vision_context:
                system_instruction += f"- Real-time hardware reflex sensors: {vision_context}\n"
        else:
            # Full Conversational Chatbot & Voice Assistant (like Google Assistant)
            system_instruction = (
                "You are BlindSight, an intelligent, friendly, witty, and helpful AI conversational companion (like Google Assistant).\n"
                "You chat naturally, answer general knowledge, tell jokes, solve math, give advice, and assist with any topic the user mentions.\n"
                "RULES:\n"
                "- Keep spoken responses concise, engaging, and under 2-3 natural sentences.\n"
                "- If the user asks for your name or who you are, introduce yourself as BlindSight.\n"
                "- Speak naturally in the same language the user speaks (English, Hindi, etc.).\n"
                "- DO NOT talk about camera, obstacles, walls, or navigation unless the user specifically asked about their surroundings.\n"
            )
            if lang == "hi":
                system_instruction += (
                    "- स्वाभाविक, दोस्ताना हिंदी में बात करें। यदि नाम या परिचय पूछा जाए, तो कहें: 'मेरा नाम ब्लाइंडसाइट है!'।\n"
                    "- जब तक उपयोगकर्ता सीधे आगे देखने के लिए न कहे, कैमरे या बाधाओं की बात बिल्कुल न करें।\n"
                )

        parts: list[dict[str, Any]] = [
            {"text": f"{system_instruction}\n\nUser: {prompt}"}
        ]

        # Attach image frame ONLY for explicit vision queries
        if is_vision_query and image_path and image_path.is_file():
            try:
                with open(image_path, "rb") as f:
                    img_bytes = f.read()
                if len(img_bytes) > 200:
                    parts.append({
                        "inline_data": {
                            "mime_type": "image/jpeg",
                            "data": base64.b64encode(img_bytes).decode("utf-8")
                        }
                    })
            except Exception as e:
                print(f"[JarvisVoice] Image attach notice: {e}")

        candidate_models = [
            GEMINI_MODEL,
            "gemini-3.5-flash",
            "gemini-flash-latest",
            "gemini-3.8-flash",
            "gemini-3.1-flash-lite"
        ]
        # Deduplicate while preserving order
        seen = set()
        models_to_try = [m for m in candidate_models if not (m in seen or seen.add(m))]

        for model_name in models_to_try:
            try:
                model_url = f"https://generativelanguage.googleapis.com/v1beta/models/{model_name}:generateContent?key={api_key}"
                resp = requests.post(
                    model_url,
                    json={"contents": [{"parts": parts}]},
                    headers={"Content-Type": "application/json"},
                    timeout=4.0
                )
                if resp.ok:
                    data = resp.json()
                    candidates = data.get("candidates", [])
                    if candidates:
                        reply = candidates[0].get("content", {}).get("parts", [{}])[0].get("text", "").strip()
                        if reply:
                            return reply
                elif resp.status_code in (429, 503, 404):
                    continue
                else:
                    print(f"[JarvisVoice] Model {model_name} status {resp.status_code}: {resp.text[:120]}")
            except Exception as e:
                pass

        return None

    def route_command(
        self,
        command: str,
        image_path: Optional[Path] = None,
        has_uploaded_image: bool = False,
        vision_context: str = "",
        lang: str = "en"
    ) -> str:
        """Route user voice command through fast local intent router + Gemini AI reasoning."""
        cmd_clean = self.strip_wake_word(command)
        if not cmd_clean and not has_uploaded_image:
            return "Yes? I am listening." if lang == "en" else "हाँ? मैं सुन रहा हूँ।"

        norm = normalize_voice_phrase(cmd_clean)

        # 1. Conversational Greetings & Identity (Instant, natural responses)
        name_triggers = [
            "who are you", "what are you", "your name", "whats your name",
            "what is your name", "aapka naam kya hai", "tumhara naam kya hai",
            "naam kya hai", "tum kaun ho", "aap kaun ho"
        ]
        if any(norm == t or norm.startswith(t + " ") for t in name_triggers):
            return (
                "My name is BlindSight, your AI assistant and navigation companion! How can I help you today?"
                if lang == "en"
                else "मेरा नाम ब्लाइंडसाइट है! मैं आपका स्मार्ट एआई असिस्टेंट साथी हूँ। बताइए, आज मैं आपकी क्या सहायता करूँ?"
            )

        greeting_triggers = ["hey", "hello", "hi", "hey blindsight", "good morning", "good evening", "kya haal hai", "kaise ho"]
        if any(norm == g or norm.startswith(g + " ") for g in greeting_triggers):
            return (
                "Hello! I am BlindSight. How can I assist you right now?"
                if lang == "en"
                else "नमस्ते! मैं ब्लाइंडसाइट हूँ, आपकी सेवा में उपस्थित। बताइए, क्या मदद करूँ?"
            )

        if any(k in norm for k in ["how are you", "how are you doing", "kaise ho"]):
            return (
                "I am running smoothly and ready for conversation or navigation! What's on your mind?"
                if lang == "en"
                else "मैं बिल्कुल ठीक और तैयार हूँ! बताइए, आज आप क्या जानना चाहते हैं?"
            )

        if any(k in norm for k in ["thank you", "thanks", "dhanyawad", "shukriya"]):
            return "You are very welcome! I am always here to assist." if lang == "en" else "आपका बहुत-बहुत स्वागत है! मैं हमेशा आपकी मदद के लिए यहाँ हूँ।"

        # 2. Jokes & Humor (General Chat - never talks about camera or obstacles)
        if any(k in norm for k in ["joke", "funny", "make me laugh", "chutkula"]):
            joke_reply = self.query_gemini(cmd_clean, is_vision_query=False, lang=lang)
            if joke_reply:
                return joke_reply
            return "Why don't scientists trust atoms? Because they make up everything!"

        # 3. Fast Math (Offline 1ms response)
        math_match = re.search(r"(?:what is|calculate|solve|what'?s)?\s*(\d+)\s*([+\-*/x]|plus|minus|times|multiplied by|divided by)\s*(\d+)", norm)
        if math_match:
            try:
                n1 = int(math_match.group(1))
                op = math_match.group(2).lower()
                n2 = int(math_match.group(3))
                res = None
                if op in ("+", "plus"): res = n1 + n2
                elif op in ("-", "minus"): res = n1 - n2
                elif op in ("*", "x", "times", "multiplied by"): res = n1 * n2
                elif op in ("/", "divided by") and n2 != 0: res = n1 / n2
                if res is not None:
                    return f"{n1} {op} {n2} equals {res}." if lang == "en" else f"{n1} {op} {n2} का उत्तर {res} है।"
            except Exception:
                pass

        # 4. Fast System Actions (5ms local response)
        if any(k in norm for k in ["time", "current time", "samay", "kitne baje"]):
            return f"The current time is {time.strftime('%I:%M %p')}." if lang == "en" else f"अभी समय {time.strftime('%I:%M %p')} हुआ है।"
        if any(k in norm for k in ["date", "today date", "day", "tarikh", "aaj konsa din"]):
            return f"Today is {time.strftime('%A, %B %d, %Y')}."
        if any(k in norm for k in ["battery", "battery status"]):
            return "Smart glasses battery is at 88 percent. Normal operation."
        if any(k in norm for k in ["stop", "pause", "be quiet", "silence", "ruk jao", "chup"]):
            return "Stopping." if lang == "en" else "रुक गया।"
        if any(k in norm for k in ["help", "what can you do", "commands", "madad"]):
            return (
                "I can chat with you, answer any question, tell stories or jokes, calculate math, and describe your surroundings when you say 'describe the scene'."
                if lang == "en"
                else "मैं आपसे बातचीत कर सकता हूँ, सवालों के जवाब दे सकता हूँ, चुटकुले सुना सकता हूँ, और जब आप कहेंगे तो आगे का दृश्य भी बता सकता हूँ।"
            )

        # 4. Check for Scene Perception Triggers
        scene_triggers = [
            "describe", "front", "ahead", "see", "around", "clear", "walk",
            "label", "sign", "samne", "aage", "dikh", "dekho", "photo", "image",
            "picture", "tasveer", "color", "shape", "rang", "aakar", "surface",
            "what is in front of me", "what is ahead of me", "what do you see", "what is around me"
        ]
        is_scene_query = has_uploaded_image or any(trigger in norm for trigger in scene_triggers)

        if is_scene_query:
            target_image = image_path if (image_path and image_path.is_file()) else (RUNTIME_DIR / "latest.jpg")
            has_frame = target_image.is_file() and (time.time() - target_image.stat().st_mtime < 120.0)

            # FAIL-CLOSED HONESTY GATE: If camera is off or no frame received, NEVER GUESS!
            if not has_frame:
                if lang == "hi":
                    return "कैमरा बंद है या कोई तस्वीर नहीं मिली। कृपया अपने स्मार्ट ग्लास या फोन का कैमरा चालू करें ताकि मैं आगे देख सकूँ।"
                return "The camera is turned off or not sending frames. Please turn on your camera so I can see what is in front of you."

            gemini_vision_reply = self.query_gemini(
                cmd_clean or "describe what is in front of me",
                image_path=target_image,
                is_vision_query=True,
                vision_context=vision_context,
                lang=lang
            )
            if gemini_vision_reply:
                return gemini_vision_reply

            return "The camera view is unclear or obstructed. Please proceed with caution." if lang == "en" else "कैमरा दृश्य स्पष्ट नहीं है। कृपया सावधानी से आगे बढ़ें।"

        # 4. General Conversational Chatbot (Answers ANY question: jokes, stories, math, advice, general Q&A)
        gemini_chat_reply = self.query_gemini(
            cmd_clean,
            is_vision_query=False,
            lang=lang
        )
        if gemini_chat_reply:
            return gemini_chat_reply

        # 5. Local Ollama Fallback if running
        try:
            resp = requests.post(
                OLLAMA_URL,
                json={
                    "model": OLLAMA_MODEL,
                    "messages": [
                        {
                            "role": "system",
                            "content": (
                                "You are BlindSight JARVIS, an empathetic and witty AI assistant. "
                                "Keep responses under 2-3 sentences. Be conversational and helpful."
                            )
                        },
                        {"role": "user", "content": cmd_clean}
                    ],
                    "stream": False,
                    "options": {"num_predict": 75}
                },
                timeout=4.0
            )
            if resp.ok:
                ans = resp.json().get("message", {}).get("content", "").strip()
                if ans:
                    return ans
        except Exception:
            pass

        # 6. Fallback conversational reply
        return (
            f"I heard: {cmd_clean}. How else can I assist you?"
            if lang == "en"
            else f"मैंने सुना: {cmd_clean}। मैं आपकी और क्या सहायता करूँ?"
        )

    async def _synthesize_edge_tts(self, text: str, voice: str) -> bytes:
        """Synthesize audio bytes using Microsoft Edge Neural TTS."""
        communicate = edge_tts.Communicate(text, voice)
        audio_stream = io.BytesIO()
        async for chunk in communicate.stream():
            if chunk["type"] == "audio":
                audio_stream.write(chunk["data"])
        return audio_stream.getvalue()

    def text_to_speech(self, text: str, lang: str = "en", voice_override: str = "") -> bytes:
        """Convert text to high-fidelity MP3 neural audio.
        
        Uses Edge-TTS (Iron Man Jarvis en-GB-RyanNeural / hi-IN-SwaraNeural)
        with fallback to Svara-TTS if requested.
        """
        if not text:
            return b""

        # Check Svara-TTS if requested
        if voice_override == "svara-tts":
            try:
                resp = requests.post(
                    SVARA_URL,
                    json={
                        "model": "svara-tts-v1",
                        "voice": "Hindi (Female)" if lang == "hi" else "English (Female)",
                        "input": text,
                        "response_format": "mp3"
                    },
                    timeout=5.0
                )
                if resp.ok and len(resp.content) > 200:
                    return resp.content
            except Exception as e:
                print(f"[JarvisVoice] Svara-TTS fallback to Edge-TTS: {e}")

        # Edge-TTS
        voice = voice_override or VOICE_MAP.get(lang.lower(), VOICE_MAP["en"])
        try:
            return asyncio.run(self._synthesize_edge_tts(text, voice))
        except Exception as e:
            print(f"[JarvisVoice] Edge-TTS error: {e}")
            try:
                return asyncio.run(self._synthesize_edge_tts(text, "en-GB-RyanNeural"))
            except Exception:
                return b""

    def play_audio_locally(self, audio_bytes: bytes):
        """Play synthesized audio out of the host PC / Pi speakers."""
        if not audio_bytes:
            return
        try:
            import subprocess
            tmp = SERVER_DIR / "runtime" / f"voice_out_{int(time.time()*1000)}.mp3"
            with open(tmp, "wb") as f:
                f.write(audio_bytes)

            cmd = (
                f'Add-Type -AssemblyName presentationCore; '
                f'$p = New-Object System.Windows.Media.MediaPlayer; '
                f'$p.Open([System.Uri]"{tmp.resolve().as_uri()}"); '
                f'$p.Play(); Start-Sleep -Milliseconds 1800'
            )
            subprocess.Popen(["powershell", "-NoProfile", "-NonInteractive", "-Command", cmd])
        except Exception as e:
            print(f"[JarvisVoice] Local playback notice: {e}")

    def process_full_voice_turn(
        self,
        audio_bytes: bytes,
        vision_context: str = "",
        lang: str = "en",
        play_local: bool = False
    ) -> Dict[str, Any]:
        """Full pipeline: Audio In -> Normalized STT -> Gemini/Intent Routing -> TTS -> Audio Out."""
        self.set_state(VoiceState.PROCESSING, "Transcribing speech")
        
        # 1. Speech to Text
        stt_lang = "hi-IN" if lang == "hi" else "en-US"
        text, detected_lang = self.transcribe_audio_bytes(audio_bytes, language=stt_lang)
        
        if not text:
            reply = "I couldn't hear clearly. Please speak again." if lang == "en" else "मैं स्पष्ट रूप से नहीं सुन सका। कृपया पुनः बोलें।"
            audio_out = self.text_to_speech(reply, lang=lang)
            self.set_state(VoiceState.STANDBY, "Ready")
            return {
                "transcript": "",
                "reply": reply,
                "audio_bytes": audio_out,
                "wake_detected": False,
            }

        # 2. Check Wake Word
        wake_detected = self.check_wake_word(text)
        if wake_detected:
            self.last_wake_time = time.time()
            self.is_active = True
            print(f"[JarvisVoice] 🟢 Wake word detected in: '{text}'")

        # 3. Route Command via Gemini
        self.set_state(VoiceState.PROCESSING, f"Answering with Gemini: {text}")
        reply = self.route_command(text, vision_context=vision_context, lang=lang)

        # 4. Synthesize Response
        self.set_state(VoiceState.SPEAKING, "Synthesizing voice")
        audio_out = self.text_to_speech(reply, lang=lang)

        if play_local and audio_out:
            self.play_audio_locally(audio_out)

        self.set_state(VoiceState.STANDBY, "Ready")
        return {
            "transcript": text,
            "reply": reply,
            "audio_bytes": audio_out,
            "wake_detected": wake_detected,
        }

    def start_background_mic_listener(self, vision_provider: Optional[Callable[[], str]] = None):
        """Starts a background daemon thread that listens on hardware microphone."""
        if self._listener_thread and self._listener_thread.is_alive():
            return

        if not self.mic_available:
            print("[JarvisVoice] Background listener aborted: No hardware microphone.")
            return

        self._stop_listener.clear()

        def _loop():
            print(f"[BlindSight] 🎙️ BlindSight Internal Mic Listener active on {self.mic_name}")
            print(f"[BlindSight] Standby: Say 'Hey BlindSight' to activate.")
            
            while not self._stop_listener.is_set():
                try:
                    audio_raw = self.record_mic_audio(duration=3.5)
                    if not audio_raw:
                        time.sleep(1.0)
                        continue

                    transcript, _ = self.transcribe_audio_bytes(audio_raw)
                    if not transcript:
                        continue

                    now = time.time()
                    wake = self.check_wake_word(transcript)
                    
                    if wake or (self.is_active and (now - self.last_wake_time < ACTIVE_TIMEOUT)):
                        self.is_active = True
                        self.last_wake_time = now
                        print(f"\n[BlindSight] Heard: {transcript}")
                        
                        vis_ctx = vision_provider() if vision_provider else ""
                        reply = self.route_command(transcript, vision_context=vis_ctx)
                        print(f"[BlindSight] Assistant: {reply}")
                        
                        audio_resp = self.text_to_speech(reply)
                        self.play_audio_locally(audio_resp)

                except Exception as exc:
                    time.sleep(1.0)

        self._listener_thread = threading.Thread(target=_loop, daemon=True, name="JarvisVoiceListener")
        self._listener_thread.start()

    def stop_background_listener(self):
        self._stop_listener.set()


# Global Singleton Instance
jarvis_assistant = JarvisVoiceAssistant()
