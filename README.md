# BlindSight

**A voice-first navigation companion for blind and low-vision users.**
A Raspberry Pi runs local object detection and speaks what it sees; an ESP32
pair keeps a network-independent ultrasonic reflex; a phone PWA adds a
conversational cloud vision companion with a wake word, human-sounding neural
voices, a small persistent memory and seven reply languages.

Licensed MIT — see [LICENSE](LICENSE).

---

## Works on

| Device | Talking **to** it | Talking **back** | Wake word | Install |
|---|---|---|---|---|
| **Android** (Chrome/Edge) | wake word, hands-free, or big button | neural human voices | ✅ | install prompt / “Add to Home screen” |
| **iPhone / iPad** (Safari) | big button → keyboard’s dictation mic | neural human voices | ❌ Apple allows no web app to listen continuously | Share → Add to Home Screen |
| **Windows** (Chrome/Edge) | full voice on `http://localhost:5000` | neural human voices | ✅ | desktop shortcut — ideal for testing |

Voice **output** is identical everywhere: replies are rendered once on the
gateway with Microsoft’s free neural voices and streamed to every device as
audio. Voice **input** differs by what each OS permits a web app to do — and
BlindSight degrades honestly: on an iPhone it *says out loud* how to dictate
instead of showing a dead microphone button, audio is unlocked on your first
tap (iOS blocks sound before any touch), and the Stop button silences the
neural audio too.

---

## Android app (APK)

A real installed Android app ships with the repo: `android/` is a thin,
auditable WebView shell (launcher icon, native mic/camera permission prompts,
self-signed-cert trust scoped to your saved gateway host, friendly
"gateway not reachable" retry screen). The **Android APK** workflow compiles
it on every push — download `blindsight-debug-apk` from the Actions run page,
no Android Studio required. Install guide, honest limits and the
first-run gateway-address flow: [docs/ANDROID_APP.md](docs/ANDROID_APP.md).

iPhone stays a home-screen PWA (Apple forbids sideloaded wrappers without a
paid developer account); it received the same voice fixes in the PWA itself.

---

## How it works

```
   HEAD UNIT (Main ESP32)          RASPBERRY PI 3B+               PHONE / EARPIECE
 ┌───────────────────────────┐   ┌────────────────────────────┐   ┌──────────────────────────┐
 │ Tier 0: ultrasonic reflex │   │ Tier 1: offline edge reflex│   │ Tier 2: cloud companion  │
 │ 3× HC-SR04, no network:   │   │ YOLOv8n ONNX 320×320 in an │   │ PWA with wake word       │
 │ <25 cm  -> "Stop"         │   │ isolated subprocess,       │   │ (Android/desktop),       │
 │ <45 cm  -> steer left/    │   │ ~1.5 s/frame on a 3B+;     │   │ dictation on iPhone,     │
 │           right clip      │   │ fails closed, never claims │   │ memory, 7 languages,     │
 │ ESP-NOW JPEG uplink from  │   │ "clear" it did not see     │   │ neural TTS via           │
 │ the ESP32-CAM head cam    │   │ espeak-ng / edge-tts voice │   │ OpenRouter               │
 └───────────────────────────┘   └────────────────────────────┘   └──────────────────────────┘
```

Design rule of the whole project: **this is a mobility aid, so every component
fails loudly, never optimistically.** `/health` reports the real state of each
tier; no endpoint ever serves placeholder, stale or synthesised data as a live
observation; a missing model, missing TTS or missing API key produces an honest
error the user can hear and understand.

---

## Quick start

See [QUICKSTART.md](QUICKSTART.md) for the three-step version. In short:

| Where | Command |
|---|---|
| Windows | double-click `start_blindsight.bat` (opens Chrome/Edge on localhost) |
| Pi / Linux | `./start_blindsight.sh` (venv, deps, prints the phone URL, HTTPS when openssl exists) |
| Anywhere | `python -m venv .venv` → install `edge_server/requirements.txt` → `python edge_server/server.py` |

Then open `http://localhost:5000` in **Chrome or Edge** (microphone APIs need
localhost or HTTPS), allow the mic, and say **“hey BlindSight”** — or switch to
*Hands-free*, or tap the big button. On a phone, use the `https://` address the
Pi prints and accept the self-signed certificate once.

---

## Using the companion

Everything below works by voice on Android/desktop; the same phrases can be
dictated or typed anywhere.

| Say | Effect |
|---|---|
| `hey BlindSight, <question>` | wake word, then one spoken question (camera attached if the toggle is on) |
| `describe` / `what is around me` | forces a camera-frame description |
| `remember that …` | stores a fact (max 20, 200 chars each) in `state/companion.json` — **works offline** |
| `what do you remember?` / `forget …` / `forget everything` | recall / remove facts — **offline** |
| `language hindi` / `switch language to spanish` | changes reply language **and** the spoken voice — **offline** |
| `read status` | speaks the live `/health` summary — **offline** |
| `clear chat` / `start over` | resets conversation history (facts are kept) — **offline** |
| `stop` / `help` | silence the assistant / hear the command list — **offline** |

* **Language follows your script:** typing Devanagari answers in Hindi even if
  the saved preference differs; the header badge always shows the live language.
* **Voices:** replies are rendered on the gateway with neural voices
  (`edge-tts`, e.g. `en-IN-NeerjaNeural`, `hi-IN-SwaraNeural`) and travel with
  the response as audio, so every device hears the same human voice.
  Fallbacks, in order: `espeak-ng` WAV, then the browser's own speech.
  The Voice picker lists gateway neural voices first, browser voices second.
* **iPhone flow:** tap the big button → the keyboard opens → tap its
  microphone → dictate → Ask. The app speaks this guidance on first open and
  disables the wake/hands-free options rather than pretending they work.
* **Password-protected gateways:** an inline token field appears in settings
  (no browser popups — they don’t exist in iOS home-screen mode).
* **Noise gate (optional toggle):** calibrates your room's background level and
  only reacts above it; the level bar shows what the mic hears. Off by default
  so the speech recognizer always owns the microphone exclusively.
* **Conversation memory:** the last 24 turns are stored (8 are sent per
  request), so follow-ups like “and on the left?” have context. State writes are
  atomic; a corrupt file is quarantined, never fatal.

---

## Production deployment (Raspberry Pi)

1. **Code & service**
   ```bash
   git clone https://github.com/devrift/BlindSight.git && cd BlindSight
   ./start_blindsight.sh          # venv + deps; or install the systemd unit:
   sudo cp edge_server/blindsight.service /etc/systemd/system/
   sudo systemctl daemon-reload && sudo systemctl enable --now blindsight
   journalctl -u blindsight -f    # logs
   ```
   The unit runs gunicorn (1 worker, 4 threads) bound to `0.0.0.0:5000`.
2. **Secrets & access control** — copy `edge_server/.env.example` to
   `edge_server/.env` and set:
   * `OPENROUTER_API_KEY` — enables Tier-2 cloud answers (spends real money).
   * `BLINDSIGHT_API_TOKEN` — **strongly recommended**: the gateway serves your
     LAN; with a token set, `/api/chat`, `/api/latest-frame` and `POST /api/prefs`
     require `Authorization: Bearer <token>`, and the PWA asks for it in settings.
3. **HTTPS for phones** — browsers expose the microphone only on localhost or
   HTTPS. Either set `BLINDSIGHT_TLS=cert.pem,key.pem` (the Pi starter creates a
   self-signed pair in `.tls/` automatically; phones accept it once), or put
   nginx/Caddy in front. Without HTTPS, phone voice features stay blocked by
   the browser and the app says so out loud.
4. **Speech stack** — `sudo apt install espeak-ng` for offline speech; install
   extra language voices with `espeak-ng-data`. `edge-tts` (in requirements)
   provides the neural voices and needs outbound internet.
5. **Vision model** — drop `yolov8n.onnx` into `edge_server/models/` (see
   `edge_server/models/README.md`) or point `BLINDSIGHT_MODEL_PATH` at it.
   Without it, Tier 1 reports itself not-ready and the reflex tiers carry on.
6. **Housekeeping** — per-request frames/audio live in `runtime/`, swept hourly
   and capped at 50 MB; conversation state lives in `state/`. Back up `state/`
   and `edge_server/.env` to migrate a user's memory. Updates: `git pull` and
   `sudo systemctl restart blindsight`.

### Windows development

`start_blindsight.bat` creates `.venv`, installs requirements and opens the
app in your browser. Voice input works on `http://localhost:5000` in Chrome or
Edge; for LAN phones run the gateway on the Pi (its starter enables HTTPS).

---

## HTTP API

| Route | Method | Auth | Purpose |
|---|---|---|---|
| `/` | GET | – | PWA shell (voice-first, screen-reader first-class) |
| `/health` | GET | – | honest tier status, `issues[]`, `version`; 503 when degraded |
| `/api/config` | GET | – | `auth_required`, `frame_max_age_seconds` (never the token) |
| `/api/prefs` | GET / POST | POST: token | reply language + voice preference, language options |
| `/api/tts/voices` | GET | – | neural voice catalogue for the current language |
| `/api/chat` | POST | token | `{prompt, use_camera}` → reply + `audio_url`; 502 + `degraded:true` when the provider is down; 429 rate limit (20/min default) |
| `/api/latest-frame` | GET | token | freshest camera JPEG (≤30 s); **404 when none — never a placeholder** |
| `/analyse` | POST | – | multipart `image`; runs Tier 1, returns summary + `audio_url`; 503 if no TTS |
| `/audio/<id>` | GET | – | rendered reply audio (neural mp3 preferred, espeak wav fallback) |
| `/sw.js`, `/static/sw.js` | GET | – | service worker with `Service-Worker-Allowed: /` |

---

## Firmware

* `firmware/esp32_main/` ships **three synchronised copies** of the main
  firmware (`src/main.cpp` for PlatformIO, the `.ino` for the Arduino IDE, and
  the copy embedded in `docs/hardware/Install_BlindSight_Main_ESP32.ps1`).
  `firmware/FIRMWARE_VARIANTS.md` is the contract: every behaviour change must
  land in all three. Both `platformio.ini` pin `espressif32@6.13.0`, and the
  ESP-NOW receive callback is dual-supported via
  `#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3`.
* Reflex bands: `<25 cm` → “Obstacle critical. Stop.” (`/stop.wav`),
  `25–45 cm` → steer to the clearer side, `<100 cm` → proceed carefully,
  else path clear. Thresholds are bench-pending; see Known limitations.
* Voice clips are **generated, not authored**: `tools/generate_clips.py`
  (espeak-ng on Linux, SAPI on Windows) writes 16-bit mono 22050 Hz WAVs into
  `firmware/esp32_main/data/`; `tools/verify_wav.py` rejects wrong formats,
  implausible durations *and* harmonic-tone fakes (spectral gate). CI runs it.
* `firmware/esp32_cam/` captures JPEGs and streams them over ESP-NOW in
  checksummed, de-duplicated 220-byte chunks with bounded Wi-Fi wait and
  send-failure logging.

---

## Project layout

```
edge_server/          gateway: server.py, vision_ai.py, companion_ai.py,
                      companion_state.py, PWA (templates/ + static/),
                      blindsight.service, .env.example, models/README.md
firmware/             esp32_main (3 copies + data clips), esp32_cam,
                      FIRMWARE_VARIANTS.md
tools/                generate_clips.py, verify_wav.py, generate_icons.py,
                      tts_win.ps1
tests/                pytest suite (fail-closed vision, API contracts,
                      companion memory, WAV gates, PWA shell, mobile platform
                      contract, repo hygiene)
docs/                 hardware guides, REPO_AUDIT.md (full audit trail)
start_blindsight.*    one-click starters (Windows / Pi-Linux)
state/, runtime/,     personal data + per-request media: git-ignored,
uploads/              swept/capped, never committed
```

---

## Development & CI

```bash
python -m venv .venv && .venv/bin/pip install -r edge_server/requirements.txt pytest ruff
.venv/bin/python -m pytest -q          # full suite
ruff check .                           # strict baseline in pyproject.toml
node --check edge_server/static/app.js # PWA syntax (CI does this too)
.venv/bin/python tools/verify_wav.py   # LittleFS clip gates
```

GitHub Actions (`.github/workflows/ci.yml`) runs a Node syntax check on the
PWA, ruff, the WAV verifier and the pytest suite on every push and PR.

---

## Security & privacy

* The gateway binds `0.0.0.0` by design (the Pi serves the LAN) — set
  `BLINDSIGHT_API_TOKEN` and HTTPS in production.
* Camera frames leave the device **only** when a chat request sets
  `use_camera`, and only to OpenRouter. Reply text goes to Microsoft edge-tts
  for neural speech. If you use wake-word/hands-free mode, your browser sends
  speech audio to its recognition service (Google on Chrome) — *Button only*
  mode plus typing avoids that entirely; iPhone dictation goes through Apple's
  keyboard.
* `state/companion.json` (memory, prefs, history) never leaves the device.
* `.env`, `state/`, `runtime/`, `uploads/` are git-ignored; CI guards against
  committing secrets, merge markers and backup artifacts.

---

## Known limitations & roadmap

* **No wake word on iPhone/iPad** — Apple grants no web app continuous
  listening; dictation via the keyboard mic is the supported flow there. A
  chip-level always-on wake word on the ESP32 is the intended hardware answer.
* ~1.5 s/frame Tier-1 latency is measured-class, not bench-certified; the
  ultrasonic band thresholds (25/45/100 cm) likewise await a bench session.
* Browser wake word needs a Chromium browser and network, and only while the
  page is open.
* Neural voices need outbound internet on the gateway; offline you get
  espeak-ng or browser voices.
* Self-signed HTTPS shows a one-time certificate warning on phones; a real
  certificate (or a tunnel like Tailscale) removes it.
* ESP-NOW range is room-scale; the JPEG uplink is best-effort with stall
  recovery, not a video stream.

Full audit history (35 original findings, the parallel-remediation cross-review
and the push audit) lives in [docs/REPO_AUDIT.md](docs/REPO_AUDIT.md).
