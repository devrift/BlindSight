# BlindSight — Voice-First Navigation Companion

**An accessible, voice-first navigation companion and mobility system for blind and low-vision users.**

BlindSight combines zero-latency ultrasonic obstacle reflexes on an ESP32 (&lt;25 cm critical stop protection), offline edge vision, and an accessible native Android companion with hands-free wake word detection, multimodal camera scene descriptions, persistent personal memory, and 7 spoken languages.

Licensed MIT — see [LICENSE](LICENSE).

---

## Architecture Overview (v1.5)

BlindSight partitions system responsibilities across three independent tiers so that critical obstacle avoidance never depends on network connectivity:

```
┌────────────────────────────────────────────────────────┐
│  Tier 0 • Ultrasonic Reflex (Hardware Autonomy)         │
│  ESP32 DevKit V1 + 3x HC-SR04 Sonar + PCM5102 I2S DAC   │
│  < 25 cm Stop • 25–45 cm Steer • Zero Network Required │
└──────────────────────────┬─────────────────────────────┘
                           │ ESP-NOW (220-byte chunks)
                           ▼
┌────────────────────────────────────────────────────────┐
│  Hardware Camera (Glasses Mounted)                     │
│  ESP32-CAM (AI Thinker) • Captures QVGA JPEG           │
└──────────────────────────┬─────────────────────────────┘
                           │ Wi-Fi HTTP POST (/analyse)
                           ▼
┌────────────────────────────────────────────────────────┐
│  Tier 1 • Edge Intelligence Gateway                     │
│  Python Flask Server (:5000) • Local YOLOv8n ONNX       │
│  Stores `runtime/latest.jpg` • Serves `/api/latest-frame│
└──────────────────────────┬─────────────────────────────┘
                           │ Wi-Fi / Hotspot HTTP GET
                           ▼
┌────────────────────────────────────────────────────────┐
│  Tier 2 • Native Android Companion App (v1.5)          │
│  112px Giant Mic • Hands-Free Wake Word • Memory Bank   │
│  Fetches Glasses Cam Frame • Multimodal Vision AI      │
└────────────────────────────────────────────────────────┘
```

---

## Key Capabilities

### 1. Voice-First Interaction & Accessible UI
- **Wake Word Detection:** Continuously listens for `"BlindSight"` or `"hey BlindSight"`.
- **Giant Touch Target:** 112px accessible microphone button with distinct haptic feedback.
- **Multimodal Scene Vision:** Say *"describe"*, *"what is around me"*, or *"look for me"* to capture the glasses' camera frame and speak an instant situational description.
- **Sighted Helper Live View:** The Android Camera tab displays the live snapshot streamed from the glasses' camera.

### 2. Persistent Offline Memory Bank
- **Teach Facts:** *"remember that my keys are on the desk"* / *"remember the door is on the right"*.
- **Recall Facts:** *"where are my keys?"* / *"what do you remember?"*.
- **Forget Facts:** *"forget keys"* or *"forget everything"*.
- All memories persist securely on-device in `SharedPreferences` and remain accessible offline.

### 3. Multi-Language Support
Supports 7 spoken languages: English (`en`), Hindi (`hi`), Bengali (`bn`), Tamil (`ta`), Telugu (`te`), Spanish (`es`), and French (`fr`). Switch anytime by saying *"language hindi"* or via the Settings menu.

### 4. Tier 0 Ultrasonic Reflex Audio
Direct hardware obstacle reflex thresholds clocked at 22,050 Hz via the PCM5102 I2S stereo DAC:
- **Distance &lt; 25 cm:** Critical Stop (`stop.wav`)
- **Distance 25–45 cm:** Steer Left / Steer Right (`left.wav`, `right.wav`)
- **Distance &lt; 100 cm:** Caution Guidance (`careful.wav`)
- **Distance &ge; 100 cm:** Path Clear Confirmation (`clear.wav`)

---

## Hardware Specifications & Wiring Matrix

Configured in `firmware/esp32_main/BlindSight_Main_ESP32.ino`:

| Component | Function | ESP32 GPIO | Notes |
|---|---|---|---|
| **Left Sonar** (HC-SR04) | Trigger / Echo | `TRIG 13` / `ECHO 34` | 1kΩ / 2kΩ voltage divider on Echo line (GPIO 34 input-only) |
| **Center Sonar** (HC-SR04) | Trigger / Echo | `TRIG 14` / `ECHO 35` | 1kΩ / 2kΩ voltage divider on Echo line (GPIO 35 input-only) |
| **Right Sonar** (HC-SR04) | Trigger / Echo | `TRIG 27` / `ECHO 39` | 1kΩ / 2kΩ voltage divider on Echo line (GPIO 39 input-only) |
| **PCM5102 I2S DAC** | BCK (Bit Clock) | `GPIO 26` | Direct 16-bit 44.1kHz / 22.05kHz PCM audio |
| **PCM5102 I2S DAC** | WS / LRCK (Word Select) | `GPIO 25` | Left / Right audio framing |
| **PCM5102 I2S DAC** | DIN (Data In) | `GPIO 22` | Zero-latency reflex audio playback to earphones |
| **Tactile Push Button** | Push-To-Talk / Setup | `GPIO 32` | Active LOW; short press fires reflex, hold &ge;3s opens setup portal |
| **Status LED** | System Beacon | `GPIO 2` | Onboard status indicator |

---

## Connecting the Android App to the Hardware Cam

1. **Start the Edge Gateway:**
   ```bash
   python edge_server/server.py
   ```
   Note the local IP address printed in the terminal (e.g. `http://192.168.1.100:5000` or `http://192.168.29.173:5000`).

2. **Connect the ESP32 Glasses:**
   - Power on the Main ESP32. On first boot (or by holding the button for 3 seconds), join the Wi-Fi AP **`BlindSight-Setup`**.
   - Navigate to `http://192.168.4.1` on your phone browser.
   - Enter your venue Wi-Fi credentials and the **Edge Server IP** (e.g. `192.168.1.100`) and port (`5000`). Tap Save.
   - The glasses will now stream camera frames to `http://<server_ip>:5000/analyse` and play audio alerts via I2S.

3. **Configure the Android App:**
   - Open the **BlindSight** app on your Android phone.
   - Tap the **Settings** icon (top right) and enter your **Glasses Gateway URL** (e.g. `http://192.168.1.100:5000`).
   - Switch to the **Camera Vision** tab: the live snapshot from the glasses camera will display automatically.
   - Say *"describe"* or tap the mic button to hear immediate multimodal scene descriptions.

---

## Sideloading the Android APK

1. **Direct Download:** Download [`BlindSight-testing.apk`](https://blindsightai.vercel.app/BlindSight-testing.apk) (v1.7.6 • 22.5 MB) onto your Android phone (supports Android 7.0 to 15).
2. **Allow Installation:** When prompted by Android security, allow installation from Unknown Sources for your browser or file manager.
3. **Launch & Speak:** Grant Microphone and Camera permissions, then tap the center button or say *"BlindSight"* to start navigating.

---

## Development & Build Commands

### Android Companion App
```bash
./gradlew assembleDebug      # Compiles app/build/outputs/apk/debug/app-debug.apk
```

### Edge Intelligence Gateway
```bash
pip install -r edge_server/requirements.txt
python edge_server/server.py # Starts Flask gateway on 0.0.0.0:5000
```

### Autonomous Verification & Benchmark Suite
```bash
python run_continuous_debug_and_train.py  # Runs 1000-frame vision benchmark & 10-cycle API tests
```

### Production Web Portal
```bash
npm install
npm run build                # Compiles production distribution to dist/
```

---

## Authors & License

- **Authors:** Lavyansh & Agastaya Verma
- **License:** Open Source under the [MIT License](LICENSE).
