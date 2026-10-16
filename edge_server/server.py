"""Local HTTP gateway for the BlindSight prototype.

Handles edge object detection, real-time spatial guidance, 
audio response generation, and multimodal cloud AI queries.
"""
from __future__ import annotations

import hashlib
import os
from pathlib import Path
import shlex
import subprocess
import threading
import time
from dataclasses import dataclass

from flask import Flask, jsonify, render_template, request, send_file
from PIL import Image
from dotenv import find_dotenv, load_dotenv

SERVER_DIR = Path(__file__).resolve().parent
ROOT_DIR = SERVER_DIR.parent

# Robust .env Discovery (checks edge_server/.env then root .env)
dotenv_path = SERVER_DIR / ".env"
if not dotenv_path.exists():
    if (ROOT_DIR / ".env").exists():
        dotenv_path = ROOT_DIR / ".env"
    else:
        found = find_dotenv(usecwd=True)
        dotenv_path = Path(found) if found else dotenv_path

if dotenv_path.exists():
    load_dotenv(dotenv_path=dotenv_path, override=True)

try:
    from companion_ai import ask_companion
except ImportError:
    try:
        from edge_server.companion_ai import ask_companion
    except ImportError:
        def ask_companion(prompt: str, image_path: str | None = None) -> str:
            return "I'm having trouble connecting to my vision brain right now."

MAX_UPLOAD_BYTES = 1_500_000
MAX_RESPONSE_CHARS = 240
SAFE_FALLBACK = "Vision unavailable. Use obstacle sensors and seek assistance."


@dataclass(frozen=True)
class Settings:
    data_dir: Path
    uploads_dir: Path
    models_dir: Path
    vision_command: list[str]
    espeak_binary: str


def load_settings() -> Settings:
    local_vision = SERVER_DIR / "vision_ai.py"
    prod_vision = Path("/home/pi/BlindSight/edge_server/vision_ai.py")
    default_vision = str(prod_vision) if prod_vision.is_file() else local_vision.as_posix()
    vision_cmd = os.getenv("VISION_COMMAND", f"python3 {default_vision}")

    default_data_dir = ROOT_DIR / "runtime"
    data_dir_env = os.getenv("BLINDSIGHT_DATA")
    data_dir = Path(data_dir_env) if data_dir_env else default_data_dir

    return Settings(
        data_dir=data_dir,
        uploads_dir=ROOT_DIR / "uploads",
        models_dir=SERVER_DIR / "models",
        vision_command=shlex.split(vision_cmd),
        espeak_binary=os.getenv("ESPEAK_BINARY", "espeak-ng"),
    )


def clean_text(value: str) -> str:
    return " ".join(value.split())[:MAX_RESPONSE_CHARS] or SAFE_FALLBACK


class VisionService:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.settings.data_dir.mkdir(parents=True, exist_ok=True)
        self.settings.uploads_dir.mkdir(parents=True, exist_ok=True)
        self.lock = threading.Lock()
        self.last_success: float | None = None

    @property
    def configured(self) -> bool:
        return bool(self.settings.vision_command)

    def analyse(self, image_path: Path) -> str:
        if not self.configured:
            return "Vision model is not configured. Use obstacle sensors."
        try:
            with self.lock:
                result = subprocess.run(
                    [*self.settings.vision_command, str(image_path)],
                    text=True,
                    capture_output=True,
                    timeout=25,
                    check=True,
                )
            self.last_success = time.time()
            return clean_text(result.stdout)
        except (OSError, subprocess.SubprocessError) as error:
            print(f"vision model error: {error}", flush=True)
            return SAFE_FALLBACK

    def speak(self, text: str, output_path: Path) -> None:
        subprocess.run(
            [self.settings.espeak_binary, "-v", "en", "-s", "155", "-w", str(output_path), text],
            timeout=20,
            check=True,
        )


def create_app(settings: Settings | None = None) -> Flask:
    settings = settings or load_settings()
    service = VisionService(settings)

    template_dir = str(SERVER_DIR / "templates")
    static_dir = str(SERVER_DIR / "static")

    app = Flask(__name__, template_folder=template_dir, static_folder=static_dir)
    app.config["MAX_CONTENT_LENGTH"] = MAX_UPLOAD_BYTES

    @app.errorhandler(413)
    def too_large(_error):
        return jsonify(error="image exceeds upload limit"), 413

    @app.get("/")
    def index():
        return render_template("index.html")

    @app.get("/health")
    def health():
        return jsonify(
            status="ok",
            vision_model_configured=service.configured,
            last_vision_success=service.last_success,
        )

    @app.post("/analyse")
    def analyse():
        photo = request.files.get("image")
        if photo is None or not photo.filename:
            return jsonify(error="missing multipart field: image"), 400
        payload = photo.read(MAX_UPLOAD_BYTES + 1)
        if len(payload) > MAX_UPLOAD_BYTES:
            return jsonify(error="image exceeds upload limit"), 413

        request_id = hashlib.sha256(payload).hexdigest()[:16]
        image_path = settings.data_dir / f"{request_id}.jpg"
        audio_path = settings.data_dir / f"{request_id}.wav"
        image_path.write_bytes(payload)

        try:
            with Image.open(image_path) as image:
                image.verify()
        except Exception:
            image_path.unlink(missing_ok=True)
            return jsonify(error="invalid JPEG"), 400

        # Maintain latest snapshot for companion viewer in runtime & uploads
        try:
            (settings.data_dir / "latest.jpg").write_bytes(payload)
            (settings.uploads_dir / "latest.jpg").write_bytes(payload)
        except Exception:
            pass

        text = service.analyse(image_path)
        try:
            service.speak(text, audio_path)
        except (OSError, subprocess.SubprocessError) as error:
            print(f"text-to-speech error: {error}", flush=True)
            return jsonify(error="text-to-speech unavailable"), 503

        print(f"{time.strftime('%F %T')} {request_id} {text}", flush=True)
        return jsonify(status="complete", summary=text, audio_url=f"/audio/{request_id}")

    @app.get("/audio/<request_id>")
    def audio(request_id: str):
        if not request_id.isalnum() or len(request_id) != 16:
            return jsonify(error="invalid audio identifier"), 404
        audio_path = settings.data_dir / f"{request_id}.wav"
        if not audio_path.is_file():
            return jsonify(error="audio not ready"), 404
        return send_file(audio_path, mimetype="audio/wav", download_name="response.wav", conditional=True)

    @app.post("/api/chat")
    def api_chat():
        data = request.get_json(silent=True) or {}
        prompt = str(data.get("prompt", "")).strip()
        use_camera = bool(data.get("use_camera", False))
        if not prompt:
            return jsonify(error="missing prompt"), 400

        image_path = None
        if use_camera:
            candidate_paths = [
                settings.data_dir / "latest.jpg",
                settings.uploads_dir / "latest.jpg",
                settings.uploads_dir / "image.png",
            ]
            for c in candidate_paths:
                if c.is_file():
                    image_path = str(c)
                    break

        reply = ask_companion(prompt, image_path=image_path)
        return jsonify(reply=reply)

    @app.get("/api/latest-frame")
    def api_latest_frame():
        candidates = [
            (settings.data_dir / "latest.jpg", "image/jpeg"),
            (settings.uploads_dir / "latest.jpg", "image/jpeg"),
            (settings.uploads_dir / "image.png", "image/png"),
        ]
        for path, mime in candidates:
            if path.is_file():
                return send_file(path.resolve(), mimetype=mime, max_age=0)
        return jsonify(error="no frame captured yet"), 404

    return app


app = create_app()

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, threaded=True)
