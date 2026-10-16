"""Local HTTP gateway for the BlindSight prototype.

This is safer than the first demo server: each request has its own files,
uploads are size-limited, and model runs are serialised on the small Pi.
It is still not a certified mobility aid.
"""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import hashlib
import os
import shlex
import subprocess
import threading
import time

from flask import Flask, jsonify, request, send_file
from PIL import Image

MAX_UPLOAD_BYTES = 1_500_000
MAX_RESPONSE_CHARS = 240
SAFE_FALLBACK = "Vision unavailable. Use obstacle sensors and seek assistance."


@dataclass(frozen=True)
class Settings:
    data_dir: Path
    vision_command: list[str]
    espeak_binary: str


def load_settings() -> Settings:
    default_vision = "/home/pi/blindsight/pi_server/vision_ai.py"
    if not Path(default_vision).exists():
        local_script = Path(__file__).resolve().parent / "vision_ai.py"
        if local_script.is_file():
            default_vision = local_script.as_posix()
    vision_cmd = os.getenv("VISION_COMMAND", f"python3 {default_vision}")
    return Settings(
        data_dir=Path(os.getenv("BLINDSIGHT_DATA", "/var/lib/blindsight")),
        vision_command=shlex.split(vision_cmd),
        espeak_binary=os.getenv("ESPEAK_BINARY", "espeak-ng"),
    )


def clean_text(value: str) -> str:
    return " ".join(value.split())[:MAX_RESPONSE_CHARS] or SAFE_FALLBACK


class VisionService:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.settings.data_dir.mkdir(parents=True, exist_ok=True)
        self.lock = threading.Lock()
        self.last_success: float | None = None

    @property
    def configured(self) -> bool:
        return bool(self.settings.vision_command)

    def analyse(self, image_path: Path) -> str:
        if not self.configured:
            return "Vision model is not configured. Use obstacle sensors."
        try:
            # Pi 3B+ models normally cannot run in parallel.
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
    app = Flask(__name__)
    app.config["MAX_CONTENT_LENGTH"] = MAX_UPLOAD_BYTES

    @app.errorhandler(413)
    def too_large(_error):
        return jsonify(error="image exceeds upload limit"), 413

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

    return app


app = create_app()

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, threaded=True)
