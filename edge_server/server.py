"""Local HTTP gateway for the BlindSight prototype.

Handles edge object detection, real-time spatial guidance,
audio response generation, and multimodal cloud AI queries.
"""
from __future__ import annotations

import hashlib
import os
import py_compile
import shlex
import shutil
import subprocess
import tempfile
import threading
import time
from dataclasses import dataclass
from pathlib import Path

from dotenv import find_dotenv, load_dotenv
from flask import Flask, jsonify, render_template, request, send_file
from PIL import Image

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
    from companion_ai import FALLBACK_REPLY, ask_companion
except ImportError:
    try:
        from edge_server.companion_ai import FALLBACK_REPLY, ask_companion
    except ImportError:
        FALLBACK_REPLY = "I'm having trouble connecting to my vision brain right now."

        def ask_companion(prompt: str, image_path: str | None = None) -> str:
            return FALLBACK_REPLY

MAX_UPLOAD_BYTES = 1_500_000
MAX_CONTENT_LENGTH = 2 * 1024 * 1024  # 2MB to accommodate multipart envelope
MAX_RESPONSE_CHARS = 240
SAFE_FALLBACK = "Vision unavailable. Use obstacle sensors and seek assistance."

chat_rate_limit: dict[str, list[float]] = {}
chat_rate_lock = threading.Lock()


@dataclass(frozen=True)
class Settings:
    data_dir: Path
    uploads_dir: Path
    models_dir: Path
    vision_script: Path
    vision_command: list[str]
    espeak_binary: str


def load_settings() -> Settings:
    local_vision = SERVER_DIR / "vision_ai.py"
    vision_script_env = os.getenv("VISION_SCRIPT")
    if vision_script_env:
        vision_script = Path(vision_script_env)
    elif local_vision.is_file():
        vision_script = local_vision
    else:
        prod_vision = Path("/home/pi/BlindSight/edge_server/vision_ai.py")
        vision_script = prod_vision if prod_vision.is_file() else local_vision

    vision_cmd_env = os.getenv("VISION_COMMAND")
    if vision_cmd_env:
        vision_command = shlex.split(vision_cmd_env)
        if not vision_script_env and len(vision_command) >= 2:
            for part in vision_command[1:]:
                if part.endswith(".py"):
                    vision_script = Path(part)
                    break
    else:
        vision_command = ["python3", vision_script.as_posix()]

    default_data_dir = ROOT_DIR / "runtime"
    data_dir_env = os.getenv("BLINDSIGHT_DATA")
    data_dir = Path(data_dir_env) if data_dir_env else default_data_dir

    return Settings(
        data_dir=data_dir,
        uploads_dir=ROOT_DIR / "uploads",
        models_dir=SERVER_DIR / "models",
        vision_script=vision_script,
        vision_command=vision_command,
        espeak_binary=os.getenv("ESPEAK_BINARY", "espeak-ng"),
    )


def clean_text(value: str) -> str:
    return " ".join(value.split())[:MAX_RESPONSE_CHARS] or SAFE_FALLBACK


def cleanup_runtime_dir(
    data_dir: Path, max_age_seconds: int = 3600, max_total_bytes: int = 50 * 1024 * 1024
) -> None:
    """Removes stale runtime files older than max_age_seconds and enforces folder size quota."""
    try:
        if not data_dir.is_dir():
            return
        now = time.time()
        tracked_files: list[tuple[float, int, Path]] = []
        total_bytes = 0

        for entry in data_dir.iterdir():
            if not entry.is_file() or entry.name in ("latest.jpg", ".gitkeep"):
                continue
            try:
                stat = entry.stat()
                if now - stat.st_mtime > max_age_seconds:
                    entry.unlink(missing_ok=True)
                else:
                    tracked_files.append((stat.st_mtime, stat.st_size, entry))
                    total_bytes += stat.st_size
            except OSError:
                pass

        if total_bytes > max_total_bytes:
            tracked_files.sort(key=lambda item: item[0])
            for _, size, entry in tracked_files:
                entry.unlink(missing_ok=True)
                total_bytes -= size
                if total_bytes <= max_total_bytes:
                    break
    except Exception as exc:
        print(f"cleanup error: {exc}", flush=True)


class VisionService:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.settings.data_dir.mkdir(parents=True, exist_ok=True)
        self.settings.uploads_dir.mkdir(parents=True, exist_ok=True)
        self.lock = threading.Lock()
        self.last_success: float | None = None

    @property
    def configured(self) -> bool:
        return bool(self.settings.vision_command) and bool(
            self.settings.vision_script and self.settings.vision_script.is_file()
        )

    def check_health(self) -> dict:
        vision_script_ok = False
        if self.settings.vision_script and self.settings.vision_script.is_file():
            try:
                py_compile.compile(str(self.settings.vision_script), doraise=True)
                vision_script_ok = True
            except Exception:
                vision_script_ok = False

        model_path: str | None = None
        imported_get_model_path = False
        try:
            try:
                from vision_ai import get_model_path
            except ImportError:
                from edge_server.vision_ai import get_model_path
            mp = get_model_path()
            imported_get_model_path = True
            if mp and mp.is_file():
                model_path = str(mp)
        except Exception:
            pass

        if not imported_get_model_path:
            env_path = os.getenv("BLINDSIGHT_MODEL_PATH")
            if env_path:
                p = Path(env_path)
                model_path = str(p) if p.is_file() else None
            else:
                for candidate in [
                    self.settings.models_dir / "yolov8n.onnx",
                    self.settings.models_dir / "yolov8n_int8.onnx",
                    SERVER_DIR / "models" / "yolov8n.onnx",
                    ROOT_DIR / "edge_server" / "models" / "yolov8n.onnx",
                ]:
                    if candidate.is_file():
                        model_path = str(candidate)
                        break

        espeak_ok = False
        bin_path = shutil.which(self.settings.espeak_binary)
        if bin_path:
            try:
                with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tf:
                    temp_wav = Path(tf.name)
                try:
                    res = subprocess.run(
                        [bin_path, "-v", "en", "-s", "155", "-w", str(temp_wav), "test"],
                        capture_output=True,
                        timeout=5,
                    )
                    if res.returncode == 0 and temp_wav.is_file() and temp_wav.stat().st_size > 0:
                        espeak_ok = True
                finally:
                    temp_wav.unlink(missing_ok=True)
            except Exception:
                espeak_ok = False

        vision_ready = bool(vision_script_ok and model_path)
        if vision_ready and espeak_ok:
            status = "ok"
        elif vision_ready or espeak_ok:
            status = "degraded"
        else:
            status = "error"

        return {
            "status": status,
            "vision_script_ok": vision_script_ok,
            "model_path": model_path,
            "espeak_ok": espeak_ok,
            "last_vision_success": self.last_success,
            "vision_model_configured": vision_ready,
        }

    def check_tts_ready(self) -> bool:
        return bool(shutil.which(self.settings.espeak_binary))

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
    app.config["MAX_CONTENT_LENGTH"] = MAX_CONTENT_LENGTH

    @app.errorhandler(413)
    def too_large(_error):
        return jsonify(error="image exceeds upload limit"), 413

    @app.get("/")
    def index():
        return render_template("index.html")

    @app.get("/sw.js")
    def service_worker():
        response = send_file(SERVER_DIR / "static" / "sw.js", mimetype="application/javascript")
        response.headers["Service-Worker-Allowed"] = "/"
        return response

    @app.get("/health")
    def health():
        health_info = service.check_health()
        status_code = 200 if health_info["status"] in ("ok", "degraded") else 503
        return jsonify(health_info), status_code

    @app.post("/analyse")
    def analyse():
        cleanup_runtime_dir(settings.data_dir)

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

        # Check TTS readiness before running vision inference
        if not service.check_tts_ready():
            image_path.unlink(missing_ok=True)
            return jsonify(error="text-to-speech unavailable"), 503

        text = service.analyse(image_path)
        try:
            service.speak(text, audio_path)
        except (OSError, subprocess.SubprocessError) as error:
            print(f"text-to-speech error: {error}", flush=True)
            image_path.unlink(missing_ok=True)
            audio_path.unlink(missing_ok=True)
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
        # Optional BLINDSIGHT_API_TOKEN authorization
        api_token = os.getenv("BLINDSIGHT_API_TOKEN")
        if api_token:
            auth_header = request.headers.get("Authorization", "")
            x_token = request.headers.get("X-API-Token", "")
            token = (
                auth_header[7:].strip()
                if auth_header.startswith("Bearer ")
                else x_token.strip()
            )
            if token != api_token:
                return jsonify(error="unauthorized"), 401

        # In-memory IP rate limiting (max 30 requests / 60 seconds)
        client_ip = request.remote_addr or "127.0.0.1"
        now = time.time()
        with chat_rate_lock:
            timestamps = [t for t in chat_rate_limit.get(client_ip, []) if now - t < 60.0]
            if len(timestamps) >= 30:
                chat_rate_limit[client_ip] = timestamps
                return jsonify(error="rate limit exceeded"), 429
            timestamps.append(now)
            chat_rate_limit[client_ip] = timestamps

        data = request.get_json(silent=True) or {}
        prompt = str(data.get("prompt", "")).strip()
        use_camera = bool(data.get("use_camera", False))
        if not prompt:
            return jsonify(error="missing prompt"), 400

        image_path: str | None = None
        if use_camera:
            candidate_paths = [
                settings.data_dir / "latest.jpg",
                settings.uploads_dir / "latest.jpg",
            ]
            for candidate in candidate_paths:
                if candidate.is_file():
                    mtime = candidate.stat().st_mtime
                    if time.time() - mtime <= 30.0:
                        image_path = str(candidate)
                    break

        reply = ask_companion(prompt, image_path=image_path)
        is_fallback = reply == FALLBACK_REPLY
        status_code = 503 if is_fallback else 200
        return jsonify(reply=reply, degraded=is_fallback), status_code

    @app.get("/api/latest-frame")
    def api_latest_frame():
        candidates = [
            settings.data_dir / "latest.jpg",
            settings.uploads_dir / "latest.jpg",
        ]
        for path in candidates:
            if path.is_file():
                return send_file(path.resolve(), mimetype="image/jpeg", max_age=0)
        return jsonify(error="no frame captured yet"), 404

    return app


app = create_app()

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, threaded=True)
