# BlindSight firmware entry points — single source of truth (v1.5)

Before v1.5 the main firmware existed in **three hand-maintained copies**
(PlatformIO `src/main.cpp`, the Arduino IDE `.ino`, and a sketch embedded in the
PowerShell installer). They drifted apart repeatedly. **v1.5 collapses them to
one canonical file per board:**

| Board | Canonical source | Other entry points |
|---|---|---|
| Main ESP32 | `firmware/esp32_main/BlindSight_Main_ESP32.ino` | `src/main.cpp` is a one-line `#include "../BlindSight_Main_ESP32.ino"`; the PowerShell installer **copies the canonical .ino** instead of embedding a sketch |
| ESP32-CAM | `firmware/esp32_cam/BlindSight_Camera.ino` | `src/main.cpp` is a one-line `#include "../BlindSight_Camera.ino"` |

**Rule: edit only the canonical `.ino`.** Every build path compiles the same
bytes, so a fix in one place is a fix everywhere.

## v1.5 networking: WiFiManager + Preferences (zero-reflash exhibits)

- Wi-Fi SSID/password are **never hardcoded**. The tzapu **WiFiManager**
  library (`>= 2.0.17`, declared in both `platformio.ini` files and installed
  by the PowerShell installer) stores them in ESP32 NVS.
- The **Edge Server IP** (`server_ip`, default `192.168.1.100`) and **Edge
  Server Port** (`server_port`, default `5000`) are custom
  `WiFiManagerParameter` fields persisted with **`Preferences.h`** (namespace
  `blindsight`). They survive reboots and power cycles.
- Boot sequence (main ESP32):
  1. Load `server_ip` / `server_port` from NVS.
  2. Try the saved Wi-Fi with a fast **8 s** timeout.
  3. Probe `http://<server_ip>:<port>/health` up to **3 times** (2 s each).
  4. If Wi-Fi fails **or** the server is unreachable, open the captive portal
     **`BlindSight-Setup`** (phone → join AP → `http://192.168.4.1`).
- The camera uses the same pattern with portal **`BlindSight-Cam-Setup`** (it
  has no server IP of its own; it sends JPEG chunks over ESP-NOW).
- **Manual portal trigger:** hold the reflex button (GPIO 32) **≥ 3 s** during
  boot or at runtime. A normal short press (< 3 s, debounced > 30 ms) still
  fires the ultrasonic reflex exactly as before.
- The only compile-time hardware setting left is the ESP-NOW pairing MAC
  (`BLINDSIGHT_CAMERA_MAC` / `BLINDSIGHT_MAIN_ESP_MAC`), optionally overridden
  via a git-ignored `secrets.h`. No Wi-Fi or IP secrets live in source control.

## Behaviours that must be preserved

1. **Bounded Wi-Fi wait.** Never an unbounded loop: `loop()` must run without a
   hotspot so the button and the HC-SR04 ultrasonic fallback work offline.
2. **ESP-NOW callback version guard.** `#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3`
   selects between the IDF 5.x (`esp_now_recv_info_t`) and IDF 4.x (`const uint8_t *mac`) signatures.
3. **Sender MAC filtering** via `isCamera()` — ignore ESP-NOW frames from any
   transmitter other than the paired camera.
4. **Audio URL from the `/analyse` JSON reply** (`audioPathFromReply`). The bare
   `GET /audio` path is a 404; the route is `/audio/<16 hex chars>`.
5. **HTTP timeouts ≥ 60 s** (`HTTP_TIMEOUT_MS`). The gateway may spend ~25 s on
   inference plus ~20 s on TTS.
6. **Stalled-frame recovery** after `FRAME_STALL_TIMEOUT_MS` (3 s) of chunk
   silence.
7. **`channels` guard in `playWavStream`** (`1..2`), otherwise a header claiming
   0 channels spins the decode loop forever.
8. **`memcpy` for WAV header fields**, never a cast through `uint32_t*`
   (strict-aliasing UB + unaligned Xtensa access).
9. **`playFile()` re-asserts `CLIP_SAMPLE_RATE` (22050 Hz)**.
10. **Audited close-range stop band:** `quickRule()` returns
    "Obstacle critical. Stop." under 25 cm and `loop()` maps it to `/stop.wav`.
11. **`WiFi.setSleep(false)`** — modem sleep drops ESP-NOW frames.
12. **Camera SCCB spelling**: `pin_sccb_sda` / `pin_sccb_scl` (esp32-camera ≥ v2.0.2).

## Which entry point should a builder use?

- **VS Code / debugger / serial plots:** open `firmware/esp32_main` (or
  `esp32_cam`) in PlatformIO; `src/main.cpp` includes the canonical sketch.
- **Arduino IDE, no terminal:** open the `.ino` file; accept the IDE's offer to
  move it into a folder of the same name. Install **WiFiManager by tzapu** first.
- **Neither:** run `docs/hardware/Install_BlindSight_Main_ESP32.ps1`, which
  copies the canonical sketch, installs WiFiManager, compiles, and uploads.

## Platform pins

Both `platformio.ini` files pin `platform = espressif32@6.13.0`
(Arduino core 2.0.17 / IDF 4.4) and set `lib_ldf_mode = deep+` so the Library
Dependency Finder follows the `#include "../....ino"` wrapper and discovers
`Preferences`, `LittleFS`, `HTTPClient`, and `WiFiManager`. The `#if` guards
mean the code also compiles on core 3.x; the comments document the one-line
change to move there. Do not unpin without re-reading those comments.

## Verified builds (v1.5.0)

- `pio run -d firmware/esp32_main` → SUCCESS (WiFiManager 2.0.17, Preferences 2.0.0)
- `pio run -d firmware/esp32_cam` → SUCCESS (WiFiManager 2.0.17)
