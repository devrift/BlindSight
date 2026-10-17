"""Local HTTP gateway for the BlindSight prototype.

Handles edge object detection, real-time spatial guidance,
audio response generation, and multimodal cloud AI queries.

Failure policy: this gateway serves a mobility aid for blind users. Every
degraded component is reported honestly by /health and no endpoint ever
presents stale, placeholder or synthesized data as a live observation.
"""
from __future__ import annotations

import contextlib
import hashlib
import os
import shutil
import subprocess
import sys
import threading
import time
from dataclasses import dataclass
from pathlib import Path

from flask import Flask, jsonify, render_template, request, send_file
from PIL import Image
from dotenv import find_dotenv, load_dotenv

SERVER_DIR = Path(__file__).resolve().parent
ROOT_DIR = SERVER_DIR.parent

# Make sibling modules importable regardless of cwd or WSGI entry point
# (gunicorn, systemd, pytest all start from different working directories).
if str(SERVER_DIR) not in sys.path:
    sys.path.insert(0, str(SERVER_DIR))

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
    import companion_state  # sibling module; sys.path fixed above, try mirrors companion_ai
    from companion_ai import CompanionResult, ask_companion_detailed, build_system_prompt
except ImportError:  # pragma: no cover - defensive import fallback
    class CompanionResult:  # type: ignore[no-redef]
        def __init__(self, reply, ok, reason=""):
            self.reply, self.ok, self.reason = reply, ok, reason

    def ask_companion_detailed(prompt: str, image_path: str | None = None) -> CompanionResult:
        return CompanionResult(
            "I'm having trouble connecting to my vision brain right now.", ok=False, reason="module_missing"
        )

    def build_system_prompt(language_label: str = "English", memory: list | None = None) -> str:
        return "You are BlindSight, a warm, concise visual AI companion for a visually impaired user."

    import types

    companion_state = types.SimpleNamespace(
        LANGUAGES={"en": ("en", "English")},
        get_language=lambda: "en",
        set_language=lambda code: "en" if code == "en" else None,
        language_options=lambda: [{"code": "en", "label": "English"}],
        espeak_voice=lambda code=None: "en",
        recent_history=lambda limit=8: [],
        append_turn=lambda role, content: None,
        clear_history=lambda: None,
        add_fact=lambda fact: fact,
        forget_fact=lambda query: None,
        clear_memory=lambda: 0,
        list_facts=lambda: [],
    )

MAX_UPLOAD_BYTES = 1_500_000
# Multipart framing adds a few hundred bytes around the image bytes, so the
# whole-request cap has to sit above the image cap or Flask rejects the body
# with an HTML 413 before the handler can return its own JSON 413.
MAX_CONTENT_LENGTH = MAX_UPLOAD_BYTES + 64 * 1024
MAX_RESPONSE_CHARS = 240
SAFE_FALLBACK = "Vision unavailable. Use obstacle sensors and seek assistance."

# A camera frame older than this is not "live". Serving a stale frame as the
# current view is how a placeholder image ends up being described to the user
# as their real surroundings.
FRAME_MAX_AGE_SECONDS = float(os.getenv("BLINDSIGHT_FRAME_MAX_AGE", "30"))

# Runtime frames and their audio are deleted after this long. Without a sweep
# the Pi's SD card fills, and every spoken response stays replayable forever.
RUNTIME_TTL_SECONDS = float(os.getenv("BLINDSIGHT_RUNTIME_TTL", "3600"))
RUNTIME_SWEEP_SECONDS = 300.0

# Simple in-process rate limit for the cloud endpoint, which spends real money.
CHAT_RATE_LIMIT = int(os.getenv("BLINDSIGHT_CHAT_RATE_LIMIT", "20"))
CHAT_RATE_WINDOW = 60.0


@dataclass(frozen=True)
class Settings:
    data_dir: Path
    uploads_dir: Path
    models_dir: Path
    vision_command: list[str]
    vision_script: Path | None
    espeak_binary: str
    api_token: str | None


def _resolve_vision_script() -> Path | None:
    """Locate the vision script that will actually be executed.

    Prefers, in order: an explicit VISION_SCRIPT override, the script extracted
    from VISION_COMMAND, then the copy sitting next to this file. A hardcoded
    absolute path is deliberately NOT preferred: doing so let a stale copy at
    /home/pi/BlindSight shadow the file the operator was editing.
    """
    override = os.getenv("VISION_SCRIPT")
    if override:
        return Path(override)

    cmd = os.getenv("VISION_COMMAND")
    if cmd:
        import shlex

        for token in shlex.split(cmd):
            if token.endswith(".py"):
                return Path(token)
        return None

    return SERVER_DIR / "vision_ai.py"


def load_settings() -> Settings:
    vision_script = _resolve_vision_script()

    if vision_script is not None:
        vision_cmd = os.getenv("VISION_COMMAND", f"python3 {vision_script.as_posix()}")
    else:
        vision_cmd = os.getenv("VISION_COMMAND", "")

    import shlex

    default_data_dir = ROOT_DIR / "runtime"
    data_dir_env = os.getenv("BLINDSIGHT_DATA")
    data_dir = Path(data_dir_env) if data_dir_env else default_data_dir

    token = (os.getenv("BLINDSIGHT_API_TOKEN") or "").strip()

    return Settings(
        data_dir=data_dir,
        uploads_dir=ROOT_DIR / "uploads",
        models_dir=SERVER_DIR / "models",
        vision_command=shlex.split(vision_cmd),
        vision_script=vision_script,
        espeak_binary=os.getenv("ESPEAK_BINARY", "espeak-ng"),
        api_token=token or None,
    )


def clean_text(value: str) -> str:
    return " ".join(value.split())[:MAX_RESPONSE_CHARS] or SAFE_FALLBACK


def _local_command(prompt: str) -> tuple[str, str] | None:
    """Handles memory/language/chat-housekeeping phrases without any API key.

    A blind user drives this app by voice, so teaching the companion ("remember
    that my dog is Bruno"), switching language or starting over must work even
    when the cloud is unreachable. Returns (reply, note) or None when the prompt
    is an ordinary question for the model.
    """
    low = prompt.strip().lower()

    if low.startswith("remember "):
        fact = companion_state.add_fact(prompt.strip()[len("remember "):])
        if not fact:
            return "Say remember followed by what I should keep, for example: remember that my dog is Bruno.", "ignored"
        return f"Remembered: {fact}", "memory_added"

    if low in ("forget everything", "forget all", "forget everything."):
        count = companion_state.clear_memory()
        return f"I forgot the {count} thing{'s' if count != 1 else ''} you taught me.", "memory_cleared"

    if low.startswith("forget "):
        hit = companion_state.forget_fact(prompt.strip()[len("forget "):])
        if hit is None:
            return "I could not find that in my memory.", "memory_missing"
        return f"Forgotten: {hit}", "memory_removed"

    if low.rstrip("?!.") in ("what do you remember", "list memories", "your memory"):
        facts = companion_state.list_facts()
        if not facts:
            return "I don't remember anything yet. Say remember followed by a fact to teach me.", "memory_empty"
        return "I remember: " + "; ".join(facts), "memory_list"

    if low.rstrip("?!.") in ("clear chat", "start over", "new chat"):
        companion_state.clear_history()
        return "Started a fresh conversation. I still keep the facts you taught me.", "history_cleared"

    if low.startswith("switch language to "):
        want = low[len("switch language to "):]
    elif low.startswith("language "):
        want = low[len("language "):]
    else:
        want = None
    if want is not None:
        want = want.strip().rstrip(".!?")
        code = want if want in companion_state.LANGUAGES else next(
            (c for c, (_voice, label) in companion_state.LANGUAGES.items() if label.lower() == want), None
        )
        if code is None:
            labels = ", ".join(label for _voice, label in companion_state.LANGUAGES.values())
            return f"I don't know that language yet. I can reply in: {labels}.", "language_unknown"
        companion_state.set_language(code)
        label = companion_state.LANGUAGES[code][1]
        return f"Switched to {label}. I will answer in {label} from now on.", "language_set"

    return None


def _locate_model(settings: Settings) -> Path | None:
    """Ask the real vision module where it would find a model.

    Importing it is cheap (numpy/PIL/onnxruntime are imported lazily inside its
    functions) and it guarantees /health reports the same search the subprocess
    performs, rather than a second copy of the logic drifting out of sync.
    """
    try:
        import vision_ai

        return vision_ai.get_model_path()
    except Exception:
        return None


def _script_parses(path: Path | None) -> tuple[bool, str]:
    if path is None:
        return False, "no vision script resolved"
    if not path.is_file():
        return False, f"vision script not found: {path}"
    try:
        compile(path.read_text(encoding="utf-8"), str(path), "exec")
        return True, str(path)
    except SyntaxError as error:
        return False, f"vision script has a SyntaxError at line {error.lineno}: {error.msg}"


class VisionService:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.settings.data_dir.mkdir(parents=True, exist_ok=True)
        self.settings.uploads_dir.mkdir(parents=True, exist_ok=True)
        self.lock = threading.Lock()
        self.last_success: float | None = None
        self._rate_hits: dict[str, list[float]] = {}
        self._rate_lock = threading.Lock()
        self._start_sweeper()

    # ------------------------------------------------------------------ health

    def diagnostics(self) -> dict:
        """Report the real state of every component the pipeline depends on.

        `configured` used to mean only "VISION_COMMAND split into a non-empty
        list", so /health reported a healthy vision pipeline while vision_ai.py
        was an unparseable merge conflict and no model existed. Each component
        is now actually probed.
        """
        script_ok, script_detail = _script_parses(self.settings.vision_script)
        model_path = _locate_model(self.settings) if script_ok else None
        espeak_path = shutil.which(self.settings.espeak_binary)
        api_key = bool((os.getenv("OPENROUTER_API_KEY") or "").strip())

        issues: list[str] = []
        if not script_ok:
            issues.append(f"vision: {script_detail}")
        if script_ok and model_path is None:
            issues.append(
                "vision: no yolov8n.onnx found (see edge_server/models/README.md "
                "or set BLINDSIGHT_MODEL_PATH)"
            )
        if espeak_path is None:
            issues.append(f"tts: {self.settings.espeak_binary!r} not on PATH")
        if not api_key:
            issues.append("companion: OPENROUTER_API_KEY not set")

        vision_ready = script_ok and model_path is not None
        if vision_ready and espeak_path:
            status = "ok"
        elif vision_ready or espeak_path:
            status = "degraded"
        else:
            status = "error"

        return {
            "status": status,
            "vision_ready": vision_ready,
            "vision_script_ok": script_ok,
            "vision_script": script_detail,
            "model_path": str(model_path) if model_path else None,
            "tts_ready": espeak_path is not None,
            "tts_binary": self.settings.espeak_binary,
            "companion_ready": api_key,
            "last_vision_success": self.last_success,
            "issues": issues,
        }

    # ------------------------------------------------------------------ vision

    def analyse(self, image_path: Path) -> str:
        if not self.settings.vision_command:
            return "Vision model is not configured. Use obstacle sensors."
        try:
            with self.lock:
                result = subprocess.run(  # noqa: S603 - command comes from operator config
                    [*self.settings.vision_command, str(image_path)],
                    text=True,
                    capture_output=True,
                    timeout=25,
                    check=True,
                )
        except (OSError, subprocess.SubprocessError) as error:
            # vision_ai.py fails closed: non-zero exit + reason on stderr means
            # "we do not know", never "the path is clear".
            detail = getattr(error, "stderr", "") or ""
            print(f"vision model error: {error} {detail}".strip(), flush=True)
            return SAFE_FALLBACK
        self.last_success = time.time()
        return clean_text(result.stdout)

    def tts_command(self, text: str, output_path: Path) -> list[str]:
        """espeak-ng argv in the voice matching the user's chosen reply language."""
        return [
            self.settings.espeak_binary,
            "-v",
            companion_state.espeak_voice(),
            "-s",
            "155",
            "-w",
            str(output_path),
            text,
        ]

    def speak(self, text: str, output_path: Path) -> None:
        subprocess.run(  # noqa: S603 - espeak-ng invoked with an argument list
            self.tts_command(text, output_path),
            timeout=20,
            check=True,
        )

    def tts_available(self) -> bool:
        return shutil.which(self.settings.espeak_binary) is not None

    # ------------------------------------------------------------------- frames

    def fresh_frame(self) -> Path | None:
        """Return the most recent camera frame, or None if there is no fresh one."""
        candidates = [
            self.settings.data_dir / "latest.jpg",
            self.settings.uploads_dir / "latest.jpg",
        ]
        newest: Path | None = None
        newest_mtime = 0.0
        for path in candidates:
            try:
                mtime = path.stat().st_mtime
            except OSError:
                continue
            if mtime > newest_mtime:
                newest, newest_mtime = path, mtime
        if newest is None:
            return None
        if time.time() - newest_mtime > FRAME_MAX_AGE_SECONDS:
            return None
        return newest

    # ---------------------------------------------------------------- rate limit

    def allow_chat(self, client_id: str) -> bool:
        now = time.time()
        with self._rate_lock:
            hits = [t for t in self._rate_hits.get(client_id, []) if now - t < CHAT_RATE_WINDOW]
            if len(hits) >= CHAT_RATE_LIMIT:
                self._rate_hits[client_id] = hits
                return False
            hits.append(now)
            self._rate_hits[client_id] = hits
            return True

    # ------------------------------------------------------------------ sweeper

    def _start_sweeper(self) -> None:
        if os.getenv("BLINDSIGHT_DISABLE_SWEEPER"):
            return
        thread = threading.Thread(target=self._sweep_loop, name="runtime-sweeper", daemon=True)
        thread.start()

    def _sweep_loop(self) -> None:
        while True:
            try:
                self.sweep_runtime()
            except Exception as error:  # pragma: no cover - never kill the thread
                print(f"runtime sweep error: {error}", flush=True)
            time.sleep(RUNTIME_SWEEP_SECONDS)

    def sweep_runtime(self) -> int:
        """Delete per-request frames and audio older than the TTL. Returns count removed."""
        cutoff = time.time() - RUNTIME_TTL_SECONDS
        removed = 0
        for path in self.settings.data_dir.iterdir():
            if path.name == ".gitkeep" or path.is_dir():
                continue
            try:
                if path.stat().st_mtime < cutoff:
                    path.unlink()
                    removed += 1
            except OSError:
                continue
        if removed:
            print(f"runtime sweep: removed {removed} expired file(s)", flush=True)
        return removed


def create_app(settings: Settings | None = None) -> Flask:
    settings = settings or load_settings()
    service = VisionService(settings)

    template_dir = str(SERVER_DIR / "templates")
    static_dir = str(SERVER_DIR / "static")

    app = Flask(__name__, template_folder=template_dir, static_folder=static_dir)
    app.config["MAX_CONTENT_LENGTH"] = MAX_CONTENT_LENGTH
    app.config["SERVICE"] = service
    app.config["SETTINGS"] = settings

    if settings.api_token is None:
        print(
            "WARNING: BLINDSIGHT_API_TOKEN is not set. /api/chat is open to anyone who can "
            "reach this host and spends a real OpenRouter key. Set it in edge_server/.env.",
            flush=True,
        )

    def _authorized() -> bool:
        """Token check for endpoints that spend money or expose camera frames."""
        if settings.api_token is None:
            return True
        header = request.headers.get("Authorization", "")
        if header.startswith("Bearer ") and header[7:].strip() == settings.api_token:
            return True
        return request.args.get("token", "") == settings.api_token

    @app.errorhandler(413)
    def too_large(_error):
        return jsonify(error="image exceeds upload limit"), 413

    @app.get("/")
    def index():
        return render_template("index.html")

    @app.get("/static/sw.js")
    def service_worker():
        """Serve the service worker from /static/ with an widened scope.

        A service worker's scope defaults to its own directory, so registering
        /static/sw.js controlled only /static/* and never the page at /. The
        precache list and the offline navigation fallback were therefore inert.
        Service-Worker-Allowed lifts the scope to the site root.
        """
        response = send_file(SERVER_DIR / "static" / "sw.js", mimetype="application/javascript")
        response.headers["Service-Worker-Allowed"] = "/"
        response.headers["Cache-Control"] = "no-cache"
        return response

    @app.get("/health")
    def health():
        report = service.diagnostics()
        code = 200 if report["status"] == "ok" else 503
        return jsonify(report), code

    @app.get("/api/config")
    def api_config():
        """Tells the PWA whether it needs to collect a token. Never returns the token."""
        return jsonify(
            auth_required=settings.api_token is not None,
            frame_max_age_seconds=FRAME_MAX_AGE_SECONDS,
        )

    @app.get("/api/prefs")
    def api_prefs_get():
        """Current reply language plus the choices the UI can offer."""
        return jsonify(language=companion_state.get_language(), options=companion_state.language_options())

    @app.post("/api/prefs")
    def api_prefs_set():
        if not _authorized():
            return jsonify(error="unauthorized"), 401
        data = request.get_json(silent=True) or {}
        code = companion_state.set_language(str(data.get("language", "")))
        if code is None:
            return jsonify(error="unsupported language", options=companion_state.language_options()), 400
        return jsonify(language=code)

    @app.post("/analyse")
    def analyse():
        photo = request.files.get("image")
        if photo is None or not photo.filename:
            return jsonify(error="missing multipart field: image"), 400
        payload = photo.read(MAX_UPLOAD_BYTES + 1)
        if len(payload) > MAX_UPLOAD_BYTES:
            return jsonify(error="image exceeds upload limit"), 413

        # Check TTS before paying for inference. The old order ran the model
        # first, then discovered espeak-ng was missing and threw the result away
        # with a 503.
        if not service.tts_available():
            return jsonify(error="text-to-speech unavailable"), 503

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

        # Maintain latest snapshot for the companion viewer.
        with contextlib.suppress(OSError):
            (settings.data_dir / "latest.jpg").write_bytes(payload)

        text = service.analyse(image_path)
        try:
            service.speak(text, audio_path)
        except (OSError, subprocess.SubprocessError) as error:
            print(f"text-to-speech error: {error}", flush=True)
            image_path.unlink(missing_ok=True)
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
        if not _authorized():
            return jsonify(error="unauthorized"), 401

        client_id = request.headers.get("X-Forwarded-For", request.remote_addr or "unknown").split(",")[0].strip()
        if not service.allow_chat(client_id):
            return jsonify(error="rate limit exceeded, try again shortly"), 429

        data = request.get_json(silent=True) or {}
        prompt = str(data.get("prompt", "")).strip()
        use_camera = bool(data.get("use_camera", False))
        if not prompt:
            return jsonify(error="missing prompt"), 400

        local = _local_command(prompt)
        if local is not None:
            reply, note = local
            return jsonify(
                reply=reply,
                degraded=False,
                reason="",
                local_command=True,
                note=note,
                image_attached=False,
                frame_stale=False,
                language=companion_state.get_language(),
            )

        image_path = None
        frame_stale = False
        if use_camera:
            frame = service.fresh_frame()
            if frame is not None:
                image_path = str(frame)
            else:
                frame_stale = True

        # Real conversation: earlier turns plus the user's language and taught
        # facts travel with every request, so follow-up questions ("and on the
        # left?", "what colour was it?") have context.
        history = companion_state.recent_history()
        system_prompt = build_system_prompt(
            companion_state.LANGUAGES[companion_state.get_language()][1],
            companion_state.list_facts(),
        )
        result = ask_companion_detailed(
            prompt, image_path=image_path, history=history, system_prompt=system_prompt
        )
        if result.ok:
            companion_state.append_turn("user", prompt)
            companion_state.append_turn("assistant", result.reply)

        # `degraded` lets the UI tell "the assistant answered" apart from "the
        # assistant is down". Previously the failure string came back as a plain
        # 200 and index.html spoke it aloud as if it were a real answer.
        payload = {
            "reply": result.reply,
            "degraded": not result.ok,
            "reason": result.reason,
            "image_attached": image_path is not None,
            "frame_stale": frame_stale,
            "local_command": False,
            "language": companion_state.get_language(),
        }
        if not result.ok:
            # Non-2xx so a client that ignores `degraded` still cannot mistake
            # this for an answer.
            return jsonify(payload), 502
        return jsonify(payload)

    @app.get("/api/latest-frame")
    def api_latest_frame():
        if not _authorized():
            return jsonify(error="unauthorized"), 401
        frame = service.fresh_frame()
        if frame is None:
            # Deliberately no fallback to a bundled placeholder image. Serving a
            # committed PNG here made the PWA show a stale placeholder in the
            # "Live Vision Feed" as though it were the current camera view.
            return jsonify(error="no fresh frame captured yet"), 404
        return send_file(frame.resolve(), mimetype="image/jpeg", max_age=0)

    return app


app = create_app()

if __name__ == "__main__":
    # Development server only. For deployment use the gunicorn command in
    # edge_server/blindsight.service.
    app.run(host="0.0.0.0", port=int(os.getenv("PORT", "5000")), threaded=True)
