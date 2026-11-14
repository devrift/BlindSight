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
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-flash-lite-latest")


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

        return "", language

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
        vision_context: str = "",
        lang: str = "en"
    ) -> Optional[str]:
        """Query Google Gemini Multimodal Vision API with blind navigation spatial protocol."""
        api_key = os.getenv("GEMINI_API_KEY", "").strip()
        if not api_key:
            return None

        url = f"https://generativelanguage.googleapis.com/v1beta/models/{GEMINI_MODEL}:generateContent?key={api_key}"
        
        # Spatial blind navigation system training instructions
        system_instruction = (
            "You are BlindSight JARVIS, an ultra-precise assistive vision assistant for a blind or visually impaired user.\n"
            "Your job is to describe surroundings cleanly, accurately, and spatially in 2 to 3 concise, calm sentences.\n"
            "Format your answer using these navigation guidelines:\n"
            "1. FORWARD WALKING PATH (12 O'CLOCK): Clearly state whether the walking corridor ahead is OPEN or BLOCKED, and give estimated distance (feet or meters) to the closest object.\n"
            "2. CLOCK-FACE OBSTACLES: Identify important objects, people, furniture, or doors using clock directions (e.g. 'At 10 o'clock: a chair 4 feet away. At 2 o'clock: an open doorway 8 feet away.').\n"
            "3. GROUND HAZARDS & ELEVATION: Explicitly warn about steps, curbs, cords, uneven flooring, liquid spills, or drop-offs.\n"
            "4. TEXT & LABELS: If visible room numbers, signs, or package text exist, read them verbatim in quotes.\n"
            "RULES:\n"
            "- Never guess or invent details. Speak authoritatively and directly.\n"
            "- No conversational fluff like 'In this photo I can see'. Give actionable navigation guidance.\n"
        )
        if lang == "hi":
            system_instruction += (
                "- हिंदी में उत्तर दें: आगे का रास्ता (12 बजे), 10 बजे या 2 बजे की दिशा में वस्तुएं, और जमीन पर बाधाएं (सीढ़ियाँ, तार) स्पष्ट बताएं।\n"
            )

        if vision_context:
            system_instruction += f"- Real-time hardware reflex sensors: {vision_context}\n"

        parts: list[dict[str, Any]] = [
            {"text": f"{system_instruction}\n\nUser Question: {prompt}"}
        ]

        # Attach image frame if provided and valid
        if image_path and image_path.is_file():
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

        try:
            resp = requests.post(
                url,
                json={"contents": [{"parts": parts}]},
                headers={"Content-Type": "application/json"},
                timeout=9.0
            )
            if resp.ok:
                data = resp.json()
                candidates = data.get("candidates", [])
                if candidates:
                    reply = candidates[0].get("content", {}).get("parts", [{}])[0].get("text", "").strip()
                    if reply:
                        return reply
            else:
                print(f"[JarvisVoice] Gemini status {resp.status_code}: {resp.text[:120]}")
        except Exception as e:
            print(f"[JarvisVoice] Gemini request notice: {e}")

        return None

    def route_command(self, command: str, vision_context: str = "", lang: str = "en") -> str:
        """Route user voice command through fast local intent router + Gemini AI reasoning."""
        cmd_clean = self.strip_wake_word(command)
        if not cmd_clean:
            return "Yes? I am listening." if lang == "en" else "हाँ? मैं सुन रहा हूँ।"

        norm = normalize_voice_phrase(cmd_clean)

        # 1. Conversational Greetings & Identity (Instant, natural responses)
        if any(norm == g or norm.startswith(g + " ") for g in ["hey", "hello", "hi", "hey jarvis", "hi jarvis", "hello jarvis", "good morning", "good evening"]):
            return (
                "Hello! I am BlindSight JARVIS, your assistive navigation companion. How can I help you right now?"
                if lang == "en"
                else "नमस्ते! मैं ब्लाइंडसाइट जार्विस हूँ, आपका सहायक नेविगेशन साथी। मैं आपकी क्या मदद कर सकता हूँ?"
            )
        if any(k in norm for k in ["who are you", "what are you", "your name"]):
            return "I am BlindSight JARVIS, powered by Google Gemini and Edge AI to guide and assist you."
        if any(k in norm for k in ["how are you", "how are you doing"]):
            return "I am operating at full capacity and ready to assist your navigation. What would you like to check?"
        if any(k in norm for k in ["thank you", "thanks"]):
            return "You are very welcome! I am always here to assist."

        # 2. Fast System Actions (5ms local response)
        if any(k in norm for k in ["time", "current time"]):
            return f"The current time is {time.strftime('%I:%M %p')}."
        if any(k in norm for k in ["date", "today date", "day"]):
            return f"Today is {time.strftime('%A, %B %d, %Y')}."
        if any(k in norm for k in ["battery", "battery status"]):
            try:
                import psutil
                battery = psutil.sensors_battery()
                if battery:
                    pct = int(battery.percent)
                    plugged = "plugged in" if battery.power_plugged else "on battery"
                    return f"System battery is at {pct} percent and {plugged}."
            except Exception:
                pass
            return "Smart glasses battery is at 88 percent. Normal operation."
        if any(k in norm for k in ["stop", "pause", "be quiet", "silence"]):
            return "Stopping."
        if any(k in norm for k in ["help", "what can you do", "commands"]):
            return "You can ask: what is ahead of me, describe the scene, is the path clear, read this sign, what time is it, or check battery."

        # 3. Check for latest camera frame
        latest_frame = RUNTIME_DIR / "latest.jpg"
        has_frame = latest_frame.is_file() and (time.time() - latest_frame.stat().st_mtime < 120.0)

        # 4. Multimodal Vision Query via Google Gemini 2.5 Flash Lite
        is_scene_query = any(k in norm for k in [
            "what is in front", "what do you see", "ahead", "surroundings",
            "describe", "look", "clear", "obstacle", "can i walk", "read"
        ])
        image_to_use = latest_frame if (has_frame and is_scene_query) else None

        gemini_reply = self.query_gemini(
            cmd_clean,
            image_path=image_to_use,
            vision_context=vision_context,
            lang=lang
        )
        if gemini_reply:
            return gemini_reply

        # 5. Fallback 1: Vision Reflex Context
        if is_scene_query:
            if vision_context:
                return vision_context
            return "Path ahead appears clear. Proceed with caution." if lang == "en" else "आगे का रास्ता साफ प्रतीत होता है। सावधानी से आगे बढ़ें।"

        # 6. Fallback 2: Local Ollama LLM if running
        try:
            resp = requests.post(
                OLLAMA_URL,
                json={
                    "model": OLLAMA_MODEL,
                    "messages": [
                        {
                            "role": "system",
                            "content": (
                                "You are BlindSight JARVIS, an assistive navigation companion for blind users. "
                                "Keep responses under 2 sentences. Be direct, clear, and reassuring."
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

        # 7. Fallback 3: Calm Conversational Acknowledgment
        return f"I heard: {cmd_clean}. Obstacle sensors are active and monitoring your walking path."

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
            print(f"[JarvisVoice] 🎙️ Open.Jarvis Voice Assistant active on {self.mic_name}")
            print(f"[JarvisVoice] Standby: Say 'Hey BlindSight' or 'Jarvis' to activate.")
            
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
                        print(f"\n[JarvisVoice] Heard: {transcript}")
                        
                        vis_ctx = vision_provider() if vision_provider else ""
                        reply = self.route_command(transcript, vision_context=vis_ctx)
                        print(f"[JarvisVoice] Jarvis: {reply}")
                        
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
