# BlindSight — Voice-First Navigation Companion

<!-- version: 1.5.0 -->

**An accessible, voice-first navigation companion for blind and low-vision users.**

BlindSight provides hands-free environmental awareness, wake word detection, multimodal camera scene descriptions, persistent memory, multi-language speech output, and Tier 0 ultrasonic reflex obstacle avoidance alerts.

Licensed MIT — see [LICENSE](LICENSE).

---

## Key Features

1. **Voice-First Interaction & Wake Word**:
   - **Wake Word Mode**: Listens continuously for `"hey BlindSight"`, `"ok BlindSight"`, or `"BlindSight"`.
   - **Hands-Free Mode**: Continuously listens for commands and conversation turns with silence segmentation.
   - **Button Only Mode**: Push-to-talk using the primary 112px accessible button.
   - **Noise Gate**: Calibrates room background levels and filters out ambient noise (fans, traffic, background chatter).

2. **Multimodal Scene Description & Vision**:
   - Spoken commands such as `"describe"`, `"what is around me"`, or `"look for me"` trigger camera frame analysis.
   - Camera viewer mode provides live feedback for sighted helpers.

3. **Persistent Companion Memory (Works Offline)**:
   - Teach facts: `"remember that my dog is Bruno"` / `"remember that the elevator is on the right"`.
   - Recall facts: `"what do you remember?"` / `"list memories"`.
   - Forget facts: `"forget Bruno"` or `"forget everything"`.
   - Conversation history: Maintains recent conversational turns for contextual follow-up questions.
   - Reset: `"clear chat"` / `"start over"` resets dialogue history while safely keeping taught facts.

4. **Multi-Language Speech & Voice Selection**:
   - 7 supported languages: English (`en`), Hindi (`hi`), Bengali (`bn`), Tamil (`ta`), Telugu (`te`), Spanish (`es`), and French (`fr`).
   - Switch language at any time by saying `"language hindi"` or selecting from the settings dropdown.

5. **Tier 0 Ultrasonic Reflex Telemetry**:
   - Real-time simulation and monitoring of the 3-sensor HC-SR04 ultrasonic array:
     - **Distance < 25 cm**: Critical obstacle stop alert (`stop.wav`).
     - **Distance 25–45 cm**: Steer left / steer right directional guidance (`left.wav`, `right.wav`).
     - **Distance < 100 cm**: Proceed carefully caution (`careful.wav`).
     - **Distance >= 100 cm**: Path clear confirmation (`clear.wav`).

6. **Accessibility & WCAG AAA Design**:
   - High contrast dark palette (near-black `#05070c`, slate `#0f172a`, amber accent `#f59e0b`).
   - Sizable touch targets (minimum 56px, mic button 112px).
   - Single assertive screen-reader live region (`aria-live="assertive"`).
   - Dynamic text size switching: Normal (A), Large (A+), Extra Large (A++).
   - Web Audio earcons for wake, listening, thinking, confirmation, error, and tick.

---

## Moving to a new venue / exhibit (v1.5 — zero reflash)

Since **v1.5** the ESP32 never needs reflashing when you change Wi-Fi networks
or move between science-centre exhibits. Wi-Fi credentials and the edge-server
IP/port are configured from a phone browser and stored on the chip.

**One-time firmware dependency:** the [tzapu **WiFiManager**](https://github.com/tzapu/WiFiManager)
library (≥ 2.0.17). PlatformIO installs it automatically (`lib_deps`); in the
Arduino IDE install **"WiFiManager by tzapu"** from Library Manager.

**At each new venue:**

1. Start the edge server (`python edge_server/server.py`). Its startup banner
   prints the LAN address(es) to use, e.g. `http://192.168.1.42:5000`. The
   server binds `0.0.0.0`, so it accepts the ESP32 over any hotspot/router.
2. Power the ESP32. If it can't reach the saved Wi-Fi **or** the saved server
   (fast ~8 s check + 3 probes of `/health`), it automatically opens a Wi-Fi
   access point named **`BlindSight-Setup`**.
   - You can also open the portal any time by **holding the reflex button for
     3 seconds** (boot or runtime). A normal short press still triggers the
     ultrasonic reflex.
3. On your phone, join **`BlindSight-Setup`**. A setup page pops up — or open
   **`http://192.168.4.1`**.
4. Select the venue Wi-Fi, enter its password, and type the **Edge Server IP**
   (default `192.168.1.100`) and **Edge Server Port** (default `5000`) from the
   server banner.
5. Save. The ESP32 reboots, connects, and remembers everything in non-volatile
   storage (`Preferences.h`). Done — no laptop, no reflash.

The ESP32-CAM works the same way via its own **`BlindSight-Cam-Setup`** portal
(select the *same* venue Wi-Fi as the main board so ESP-NOW shares a channel).

---

## Development & Build

```bash
npm install
npm run dev      # Launches dev server at 0.0.0.0:3000
npm run build    # Compiles production bundle via Vite
```

---

## Project Structure

```
├── public/                 # Icons, PWA manifest, and authentic ultrasonic reflex audio clips
├── src/
│   ├── components/         # Header, StatusCard, MicCard, ControlsCard, ConversationLog, Modals
│   ├── services/           # AudioService, CompanionStateService, ReflexService, VisionAiService
│   ├── styles/             # WCAG AAA accessible styling (app.css)
│   ├── types.ts            # Type definitions
│   ├── App.tsx             # Main companion app
│   └── main.tsx            # Application entrypoint
├── android/                # Native Android WebView shell wrapper
├── package.json            # Node.js / Vite build configuration
└── vite.config.ts          # Vite configuration
```
