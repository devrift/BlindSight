# BlindSight 👁️🔊

> **A Dual-Tier Embedded Edge & Cloud Multimodal Mobility Assistant for the Visually Impaired.**

BlindSight is an open-source assistive mobility wearable designed to provide real-time spatial navigation awareness and conversational scene understanding. It pairs local edge reflex detection on a Raspberry Pi 3B+ (about 1.5 s per frame end-to-end today; see [Known limitations](#-known-limitations--roadmap)) with a high-level cloud visual companion accessible directly via phone or earphone.

---

## 🏛️ System Architecture

```
   ┌────────────────────┐
   │  ESP32-CAM (Head)  │  QVGA JPEG (320x240)
   │  220-byte Chunks   │  16-bit Checksum Verification
   └─────────┬──────────┘
             │ ESP-NOW (Zero-Latency Peer-to-Peer RF)
             ▼
   ┌────────────────────┐      HTTP Multipart POST      ┌─────────────────────────────────┐
   │  Main ESP32 DevKit ├──────────────────────────────►│    Raspberry Pi 3B+ Gateway     │
   │  • Packet Assembly │                               │  • Flask REST Server (:5000)    │
   │  • 3x HC-SR04 Sens │◄────────── Audio Stream ──────┤  • 16-bit WAV Out (espeak-ng)   │
   │  • I2S PCM5102 DAC │      GET /audio/<req_id>      └──────────────┬──────────────────┘
   │  • LittleFS Audio  │                                              │
   └────────────────────┘                                              │
             ▲                                                         │
             │                                                         │
    3x Ultrasonic Range                                                │
   (Hardware Low Fallback)                                             │
                                                                       │
             ┌─────────────────────────────────────────────────────────┴────────────────────────┐
             │                                                                                  │
             ▼                                                                                  ▼
   ┌────────────────────────────────┐                                         ┌──────────────────────────────────┐
   │  Tier 1: Offline Edge Reflex   │                                         │ Tier 2: Cloud Multimodal AI      │
   │  • YOLOv8n ONNX (320x320)      │                                         │ • OpenRouter API Gateway         │
   │  • onnxruntime (2-thread CPU)  │                                         │ • Gemini 2.5 Flash / GPT-4o-mini │
   │  • ~1.5s Inference (<150MB RAM)│                                         │ • Visual Scene Q&A               │
   │  • Spatial Sector Obstacle Nav │                                         │ • Natural Spoken Voice Output    │
   └────────────────────────────────┘                                         └────────────────┬─────────────────┘
                                                                                               │
                                                                                               ▼
                                                                              ┌──────────────────────────────────┐
                                                                              │   Progressive Web App (PWA)      │
                                                                              │   • Accessible Mobile UI (Phone) │
                                                                              │   • Native Web Speech Recognition│
                                                                              │   • Device Speech Synthesis      │
                                                                              │   • Haptic Low-Vision Feedback   │
                                                                              └──────────────────────────────────┘
```

---

## ⚡ Core Features

1. **Dual-Tier Intelligence**:
   * **Tier 1 (Offline Edge Reflex)**: Operates without internet connectivity. Runs quantized YOLOv8n ONNX on the Raspberry Pi 3B+ CPU with a lean memory footprint (<150MB) and roughly 1.5 s inference latency per frame, providing direct spatial instructions (*"Warning: Chair close ahead. Path clear on your right."*). When vision cannot run at all, the gateway says so and falls back to a generic safety message — it never claims the path is clear when it could not see.
   * **Tier 2 (Cloud Multimodal Companion)**: Accessible via phone PWA or API. Powered by OpenRouter (`google/gemini-2.5-flash` with `openai/gpt-4o-mini` fallback), giving users descriptive contextual answers to questions like *"What color is this shirt?"* or *"Read the sign in front of me"*.
2. **Deterministic Fallback Safety**:
   * If Wi-Fi drops, 3× HC-SR04 ultrasonic rangefinders trigger instant local LittleFS WAV clips over the I2S PCM5102 DAC directly on the Main ESP32 without depending on the network.
   * The clips are generated, not authored: run `firmware/esp32_main/data/generate_clips.sh` once (needs `espeak-ng`) and upload the filesystem image. Without them this fallback is silent — the firmware reports that clearly on the serial monitor.
3. **PWA Mobile Phone Companion**:
   * Fully installable standalone web application with high-contrast UI, large tactile touch targets, Web Speech voice input, and native device speech synthesis (zero Pi CPU load).
   * Tactile haptic vibration cues (`navigator.vibrate`) for low-vision users.

---

## 🔌 Hardware Pinouts & Power Distribution

### Power Topology

> Canonical wiring reference: [`docs/hardware/WIRING_V2_BUCK_AND_TTL.md`](docs/hardware/WIRING_V2_BUCK_AND_TTL.md).
> `PIN_CONNECTION_CHART.md` and the click-by-click guide still carry the older
> two-Arduino rail description for their signal tables; their power sections are
> superseded by WIRING_V2.
* **5V 3A Buck Rail**: Dedicated buck converter supplying 5.0–5.1V with a **shared common ground** for:
  * Main ESP32 (`VIN`)
  * ESP32-CAM (`5V` + 1000 µF low-ESR decoupling capacitor across 5V and GND)
  * PCM5102 I2S DAC (`VCC`)
  * 3× HC-SR04 Ultrasonic Sensors (`VCC`)
* **Galvanic Pi Isolation**: Raspberry Pi 3B+ is powered exclusively from its own isolated power source / power bank. **No power or GND wire joins the Pi to the buck converter** (communication is 100% wireless over Wi-Fi).

### Pin Wiring Matrix

| Subsystem | Peripheral Pin | ESP32 GPIO | Logic Level / Divider |
|---|---|---|---|
| **PCM5102 DAC** | BCK / BCLK | GPIO 26 | Direct 3.3V I2S Clock |
| | LRCK / WS | GPIO 25 | Direct 3.3V Word Select |
| | DIN | GPIO 22 | Direct 3.3V Serial Audio |
| **HC-SR04 Left** | TRIG | GPIO 13 | Direct 3.3V Output |
| | ECHO | GPIO 34 | **1kΩ / 2kΩ Divider** (5V → 3.3V) |
| **HC-SR04 Center**| TRIG | GPIO 14 | Direct 3.3V Output |
| | ECHO | GPIO 35 | **1kΩ / 2kΩ Divider** (5V → 3.3V) |
| **HC-SR04 Right** | TRIG | GPIO 27 | Direct 3.3V Output |
| | ECHO | GPIO 39 | **1kΩ / 2kΩ Divider** (5V → 3.3V) |
| **Tactile Button** | Pin 1 / Pin 2 | GPIO 32 ↔ GND | `INPUT_PULLUP` (Active LOW) |

> ⚠️ **Warning**: Never feed 5V ECHO signals directly to ESP32 GPIOs 34, 35, or 39. Always use 1kΩ in series and 2kΩ to GND.

---

## 🚀 Quickstart & Setup Guide

### 1. Raspberry Pi Gateway Setup

```bash
# Clone the repository
git clone https://github.com/<your-user>/BlindSight.git
cd BlindSight/edge_server

# Install Python dependencies
pip install -r requirements.txt

# Export or download the quantized YOLOv8n ONNX model (imgsz=320)
# (ultralytics is in requirements.txt; export on a machine with enough RAM)
python3 -c "from ultralytics import YOLO; YOLO('yolov8n.pt').export(format='onnx', imgsz=320, simplify=True)"
mkdir -p models && mv yolov8n.onnx models/

# Configure your environment
cp .env.example .env
nano .env  # Add your OPENROUTER_API_KEY

# Start the server (or enable the systemd service)
python3 server.py
```

`GET /health` reports the real state of every component (`vision_ready`,
`tts_ready`, `companion_ready`, plus an `issues` list). Until the model and
`espeak-ng` are in place it returns `503` with reasons instead of pretending to
be healthy — check it after any change.

### 2. Accessing the Mobile Phone Companion (PWA)

1. Connect your smartphone to the same Wi-Fi network as the Raspberry Pi.
2. Open your mobile browser and navigate to:
   ```
   http://<RASPBERRY_PI_IP>:5000
   ```
3. Tap your browser menu and choose **"Add to Home Screen"** or **"Install BlindSight"**.
4. Tap the large amber **"Tap & Speak"** button to start asking questions about your surroundings.

### 3. Flashing the ESP32 Firmware

#### Step A: Main ESP32

> The main firmware exists in three forms (PlatformIO `src/main.cpp`, the
> Arduino `.ino`, and the sketch inside the PowerShell installer). They must
> stay behaviourally identical — see
> [`firmware/FIRMWARE_VARIANTS.md`](firmware/FIRMWARE_VARIANTS.md).

1. Open [`firmware/esp32_main/BlindSight_Main_ESP32.ino`](file:///firmware/esp32_main/BlindSight_Main_ESP32.ino) in Arduino IDE or [`firmware/esp32_main`](file:///firmware/esp32_main) in PlatformIO.
   Arduino IDE requires a sketch to live in a folder of its own name, so it will
   offer to move the `.ino` into `BlindSight_Main_ESP32/` — accept that when
   building from the IDE. The in-repo layout is arranged for PlatformIO.
2. Set your Wi-Fi SSID, password, and the Raspberry Pi IP address.
3. Select board `ESP32 Dev Module` and flash.
4. Open the Serial Monitor (115200 baud) and note the printed `MAIN MAC: XX:XX:XX:XX:XX:XX`.

#### Step B: ESP32-CAM
1. Open [`firmware/esp32_cam/BlindSight_Camera.ino`](file:///firmware/esp32_cam/BlindSight_Camera.ino) in Arduino IDE or [`firmware/esp32_cam`](file:///firmware/esp32_cam) in PlatformIO.
2. Paste the `MAIN MAC` address into `MAIN_ESP_MAC`.
3. Select board `AI Thinker ESP32-CAM`.
4. Connect `GPIO0` to `GND`, press `RST`, and flash. Disconnect `GPIO0` and press `RST` to run.

---

## 🛠️ API Reference

| Endpoint | Method | Payload / Params | Description |
|---|---|---|---|
| `/` | `GET` | None | Serves the Accessible Voice Companion PWA |
| `/health` | `GET` | None | Per-component health report; `503` with an `issues` list when degraded |
| `/api/config` | `GET` | None | Tells the PWA whether a bearer token is required (never returns the token) |
| `/analyse` | `POST` | Multipart `image` (JPEG) | Ingests camera frame, executes YOLOv8n, generates speech |
| `/audio/<id>` | `GET` | Request ID in URL | Streams 16-bit PCM WAV generated by `espeak-ng` |
| `/api/chat` | `POST` | `{"prompt": str, "use_camera": bool}`, optional `Authorization: Bearer <token>` | Multimodal voice query to the OpenRouter assistant; `502` + `degraded:true` when the provider is unreachable |
| `/api/latest-frame` | `GET` | None | JPEG of the most recent camera frame (≤30 s old); `404` when there is no fresh frame — never a placeholder |

---

## ⚠️ Known Limitations & Roadmap

Honest status as of this revision. These are tracked, not aspirations:

* **Latency.** `edge_server/server.py` spawns a fresh interpreter per `/analyse`
  request, which re-imports NumPy/Pillow/onnxruntime and reloads the ONNX model
  every frame. Budget ~1.5 s per frame on a Pi 3B+, not the millisecond figures
  quoted in older revisions of this document. A persistent in-process inference
  worker (model loaded once, frames queued) is the planned fix and is the only
  route to a sub-second reflex tier.
* **Voice clips.** The offline ultrasonic fallback needs generated LittleFS
  clips (`firmware/esp32_main/data/generate_clips.sh`). A fresh clone is silent
  until you run it and upload the filesystem image.
* **Authentication.** Set `BLINDSIGHT_API_TOKEN` in `edge_server/.env`. Until you
  do, `/api/chat` is reachable by anyone on your LAN and spends your OpenRouter
  key; the server prints a loud warning at startup when the token is absent.
* **Not a certified aid.** BlindSight is a research prototype. Nothing here has
  been validated as a mobility aid; treat every "path clear" as unverified.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
