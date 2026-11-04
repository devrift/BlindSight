#!/usr/bin/env python3
"""BlindSight Gateway Server.

Permanent, 100% Free, Local Edge AI Server.
Integrates:
- Open.Jarvis Audio Assistant Engine (Wake Word, Google STT, Edge-TTS Neural Voices)
- YOLOv8n ONNX Edge Reflex Obstacle Detection (ESP32-CAM)
- Local Intent Router & Multimodal Vision Perception
- Zero Mandatory API Keys (100% Free)
"""
from __future__ import annotations

import io
import os
import sys
import time
from pathlib import Path
from urllib.parse import quote

from flask import Flask, Response, jsonify, request, send_file

SERVER_DIR = Path(__file__).resolve().parent
ROOT_DIR = SERVER_DIR.parent
RUNTIME_DIR = SERVER_DIR / "runtime"
RUNTIME_DIR.mkdir(parents=True, exist_ok=True)

if str(SERVER_DIR) not in sys.path:
    sys.path.insert(0, str(SERVER_DIR))

# Import Open.Jarvis Audio Engine
try:
    from jarvis_voice import jarvis_assistant, VoiceState
except ImportError as e:
    print(f"Warning: jarvis_voice failed to load: {e}")
    jarvis_assistant = None

# Import YOLOv8n ONNX vision reflex
try:
    import vision_ai
except ImportError:
    vision_ai = None

app = Flask(__name__)

# System state
last_vision_description = "Path ahead appears clear."
last_frame_timestamp = 0.0


@app.after_request
def add_cors_headers(response):
    response.headers["Access-Control-Allow-Origin"] = "*"
    response.headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization, X-Requested-With"
    response.headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
    response.headers["Access-Control-Expose-Headers"] = "X-Transcript, X-Reply, X-Wake-Detected"
    return response


@app.route("/health", methods=["GET"])
@app.route("/api/status", methods=["GET"])
def health():
    """Health check for edge reflex, Open.Jarvis voice assistant, and camera."""
    yolo_model = (SERVER_DIR / "models" / "yolov8n.onnx").is_file()
    mic_name = jarvis_assistant.mic_name if jarvis_assistant else "None"
    wake_words = jarvis_assistant.wake_words if jarvis_assistant else []

    return jsonify({
        "status": "healthy",
        "service": "BlindSight Open.Jarvis Local Edge Gateway",
        "voice_engine": "Open.Jarvis Audio Assistant",
        "tier1_edge_reflex": "ready" if yolo_model else "model_missing",
        "tier2_voice_ai": "ready" if jarvis_assistant else "not_loaded",
        "wake_words": wake_words,
        "hardware_mic": mic_name,
        "mic_available": jarvis_assistant.mic_available if jarvis_assistant else False,
        "llm_brain": "Google Gemini 2.5 Flash Lite (Multimodal Vision + Voice)" if os.getenv("GEMINI_API_KEY") else "Local Intent Router / Ollama",
        "gemini_active": bool(os.getenv("GEMINI_API_KEY")),
        "stt": "Google STT / speech_recognition (free, no key)",
        "tts": "Microsoft Edge Neural TTS (en-GB-RyanNeural / hi-IN-SwaraNeural)",
        "cost": "₹0 / permanent free",
    })


@app.route("/api/voice", methods=["POST"])
def api_voice():
    """Full voice round-trip: raw audio bytes in -> audio response out.
    
    1. STT: Open.Jarvis converts WAV/audio into transcript.
    2. Intent / Reasoning: checks wake words, routes navigational & general questions.
    3. TTS: Edge-TTS generates neural audio stream.
    """
    audio_data = request.get_data()
    if not audio_data or len(audio_data) < 100:
        return Response(b"", status=400, mimetype="text/plain")

    global last_vision_description
    vision_ctx = last_vision_description if (time.time() - last_frame_timestamp < 45.0) else ""
    lang = request.args.get("lang", "en")

    if jarvis_assistant is not None:
        turn = jarvis_assistant.process_full_voice_turn(
            audio_data,
            vision_context=vision_ctx,
            lang=lang,
            play_local=False
        )
        audio_out = turn.get("audio_bytes", b"")
        transcript = turn.get("transcript", "")
        reply = turn.get("reply", "")
        wake = turn.get("wake_detected", False)

        resp = Response(audio_out, mimetype="audio/mpeg")
        resp.headers["X-Transcript"] = quote(transcript)
        resp.headers["X-Reply"] = quote(reply)
        resp.headers["X-Wake-Detected"] = "1" if wake else "0"
        return resp

    return Response(b"", status=500, mimetype="text/plain")


@app.route("/api/chat", methods=["POST"])
def api_chat():
    """Text-based AI query endpoint for PWA companion."""
    data = request.get_json(silent=True) or {}
    prompt = str(data.get("prompt", "")).strip()
    lang = str(data.get("lang", "en")).strip()

    if not prompt:
        return jsonify(error="missing prompt"), 400

    global last_vision_description
    vision_ctx = last_vision_description if (time.time() - last_frame_timestamp < 45.0) else ""

    if jarvis_assistant is not None:
        reply = jarvis_assistant.route_command(prompt, vision_context=vision_ctx, lang=lang)
    else:
        reply = "BlindSight local companion active. Obstacle sensors are monitoring your path."

    return jsonify({
        "reply": reply,
        "ok": True,
        "image_attached": bool(vision_ctx),
        "degraded": False,
        "tts_url": f"/api/tts?text={quote(reply)}&lang={lang}"
    })


@app.route("/api/tts", methods=["GET", "POST"])
def api_tts():
    """Text-to-speech audio streaming endpoint."""
    if request.method == "POST":
        data = request.get_json(silent=True) or {}
        text = str(data.get("text", "")).strip()
        lang = str(data.get("lang", "en")).strip()
        voice = str(data.get("voice", "")).strip()
    else:
        text = str(request.args.get("text", "")).strip()
        lang = str(request.args.get("lang", "en")).strip()
        voice = str(request.args.get("voice", "")).strip()

    if not text:
        return jsonify(error="missing text"), 400

    if jarvis_assistant is not None:
        audio_bytes = jarvis_assistant.text_to_speech(text, lang=lang, voice_override=voice)
        return Response(audio_bytes, mimetype="audio/mpeg")

    return jsonify(error="TTS unavailable"), 500


@app.route("/api/listen", methods=["POST", "GET"])
def api_listen_mic():
    """Triggers live hardware microphone listening on the host PC / Pi.
    
    Records 3.5s of audio, transcribes, routes the command, and returns the result.
    """
    if jarvis_assistant is None or not jarvis_assistant.mic_available:
        return jsonify(error="Hardware microphone unavailable", ok=False), 503

    global last_vision_description
    vision_ctx = last_vision_description if (time.time() - last_frame_timestamp < 45.0) else ""
    lang = request.args.get("lang", "en")

    # Record from hardware mic
    audio_raw = jarvis_assistant.record_mic_audio(duration=3.5)
    if not audio_raw:
        return jsonify(error="Failed to record audio", ok=False), 500

    turn = jarvis_assistant.process_full_voice_turn(
        audio_raw,
        vision_context=vision_ctx,
        lang=lang,
        play_local=True  # also speak out PC speakers
    )

    return jsonify({
        "ok": True,
        "transcript": turn.get("transcript", ""),
        "reply": turn.get("reply", ""),
        "wake_detected": turn.get("wake_detected", False),
        "tts_url": f"/api/tts?text={quote(turn.get('reply', ''))}&lang={lang}"
    })


@app.route("/analyse", methods=["POST"])
def analyse_frame():
    """Receives camera frame from ESP32-CAM and runs YOLOv8n obstacle detection."""
    global last_vision_description, last_frame_timestamp

    image_data = request.get_data()
    if not image_data or len(image_data) < 500:
        return jsonify(error="Empty image payload"), 400

    latest_path = RUNTIME_DIR / "latest.jpg"
    try:
        with open(latest_path, "wb") as f:
            f.write(image_data)
        last_frame_timestamp = time.time()

        # Run local YOLOv8n ONNX inference
        if vision_ai is not None:
            model_path = vision_ai.get_model_path()
            if model_path:
                stats = {}
                detections = vision_ai.run_inference(latest_path, model_path, stats)
                instruction = vision_ai.generate_sentence(detections)
                last_vision_description = instruction
                return jsonify({
                    "success": True,
                    "instruction": instruction,
                    "detections": len(detections),
                })
    except Exception as e:
        print(f"Vision inference error: {e}")

    last_vision_description = "Path ahead appears clear."
    return jsonify({
        "success": True,
        "instruction": "Path ahead appears clear.",
        "detections": 0,
    })


@app.route("/api/latest-frame", methods=["GET"])
def latest_frame():
    """Serves the latest camera frame captured by the smart glasses."""
    latest_path = RUNTIME_DIR / "latest.jpg"
    if latest_path.is_file():
        return send_file(latest_path, mimetype="image/jpeg", max_age=0)
    return jsonify(error="No frame captured yet"), 404


if __name__ == "__main__":
    port = int(os.getenv("PORT", "5000"))
    print(f"Starting BlindSight Open.Jarvis Gateway on http://0.0.0.0:{port}")
    app.run(host="0.0.0.0", port=port, debug=False)
