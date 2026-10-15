"""BlindSight Raspberry Pi server.
Default vision result is deliberately a safe test placeholder. Set VISION_COMMAND
for your installed local model; it receives the JPEG pathname as its final argument
and must print only a short navigational sentence.
"""
from pathlib import Path
import os, subprocess, time
from flask import Flask, request, jsonify, send_file
from PIL import Image

app = Flask(__name__)
BASE = Path(os.getenv("BLINDSIGHT_DATA", "/home/pi/blindsight-data"))
BASE.mkdir(parents=True, exist_ok=True)
IMAGE = BASE / "latest.jpg"
AUDIO = BASE / "response.wav"
VISION_COMMAND = os.getenv("VISION_COMMAND", "").strip()


def analyse_image(path: Path) -> str:
    if not VISION_COMMAND:
        # TEST ONLY: replace with a real local vision model before relying on it.
        return "Image received. Vision model is not configured. Use obstacle sensors."
    try:
        result = subprocess.run(VISION_COMMAND.split() + [str(path)], text=True,
                                capture_output=True, timeout=25, check=True)
        answer = result.stdout.strip().replace("\n", " ")[:240]
        return answer or "I could not interpret the image. Use obstacle sensors."
    except Exception as error:
        print("Vision model error:", error)
        return "Vision analysis unavailable. Use obstacle sensors."


def make_wav(text: str, output: Path):
    # espeak produces 16-bit PCM WAV, which main_esp32 firmware expects.
    subprocess.run(["espeak-ng", "-v", "en", "-s", "155", "-w", str(output), text],
                   timeout=20, check=True)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/analyse")
def analyse():
    photo = request.files.get("image")
    if photo is None:
        return jsonify(error="missing multipart field image"), 400
    photo.save(IMAGE)
    try:
        with Image.open(IMAGE) as im:
            im.verify()
    except Exception:
        return jsonify(error="invalid JPEG"), 400

    text = analyse_image(IMAGE)
    try:
        make_wav(text, AUDIO)
    except Exception as error:
        return jsonify(error=f"TTS failed: {error}"), 500
    print(time.strftime("%F %T"), text)
    return jsonify(status="complete", summary=text, audio_url="/audio")


@app.get("/audio")
def audio():
    if not AUDIO.exists():
        return jsonify(error="audio not ready"), 404
    return send_file(AUDIO, mimetype="audio/wav", download_name="response.wav")


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, threaded=True)
