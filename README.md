# BlindSight — Voice-First Navigation Companion

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
