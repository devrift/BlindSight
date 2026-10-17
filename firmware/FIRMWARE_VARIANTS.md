# BlindSight firmware entry points — keep these in sync

The main ESP32 firmware exists in **three** copies, for three different
audiences. They have drifted apart in the past (the `.ino` and the PowerShell
installer were missing five fixes that `src/main.cpp` already had, and the
PowerShell copy called a route that returns 404), so this file is the contract.

| # | File | For | Config style |
|---|---|---|---|
| 1 | `firmware/esp32_main/src/main.cpp` | PlatformIO / VS Code developers | `include/secrets.h` |
| 2 | `firmware/esp32_main/BlindSight_Main_ESP32.ino` | Arduino IDE | literals at the top |
| 3 | sketch embedded in `docs/hardware/Install_BlindSight_Main_ESP32.ps1` | non-technical builders, no terminal | injected by the installer |

**Rule: any behaviour change lands in all three in the same commit.** If you fix
a bug in one, you have fixed it in none until the other two match.

The ESP32-CAM has two copies with the same rule:
`firmware/esp32_cam/src/main.cpp` and `firmware/esp32_cam/BlindSight_Camera.ino`.

## Behaviours that must match across all copies

1. **Bounded Wi-Fi wait.** `while (WiFi.status() != WL_CONNECTED && millis() - started < WIFI_CONNECT_TIMEOUT_MS)`.
   Never an unbounded loop: `loop()` must run without a hotspot so the button
   and the HC-SR04 ultrasonic fallback work offline.
2. **ESP-NOW callback version guard.** `#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3`
   selects between the IDF 5.x (`esp_now_recv_info_t`) and IDF 4.x (`const uint8_t *mac`) signatures.
3. **Sender MAC filtering** via `isCamera()` — ignore ESP-NOW frames from any
   transmitter other than the camera. The camera MAC is configuration, not a
   leftover: the `.ino`'s old comment ("You do NOT need the camera MAC here")
   was wrong.
4. **Audio URL from the `/analyse` JSON reply** (`audioPathFromReply`). The bare
   `GET /audio` path is a 404; the route is `/audio/<16 hex chars>`.
5. **HTTP timeouts ≥ 60 s** (`HTTP_TIMEOUT_MS`). The gateway may spend ~25 s on
   inference plus ~20 s on TTS. The HTTPClient default of 5 s timed out on every
   real request.
6. **Stalled-frame recovery** after `FRAME_STALL_TIMEOUT_MS` (3 s) of chunk
   silence, otherwise a lost final chunk plus a camera reboot uploads a corrupt
   mixed JPEG.
7. **`channels` guard in `playWavStream`** (`if (channels < 1) channels = 1; if (channels > 2) channels = 2;`).
   Without it a header claiming 0 channels spins the decode loop forever while
   overflowing the 512-sample stack buffer.
8. **`memcpy` for WAV header fields**, never a cast through `uint32_t*`:
   strict-aliasing UB and an unaligned access on Xtensa.
9. **`playFile()` re-asserts `CLIP_SAMPLE_RATE` (22050 Hz)** so a streamed network
   response at another rate cannot leave the DAC mis-clocked for the next clip.
10. **No dead `"Stop"` branch.** `quickRule()` never returns a string containing
    "Stop"; the branch and `/stop.wav` never existed. (The clip generator still
    produces `stop.wav` for builds that add a stop condition later — harmless.)
11. **`WiFi.setSleep(false)`** — modem sleep drops ESP-NOW frames.
12. **Camera SCCB spelling**: `pin_sccb_sda` / `pin_sccb_scl` (current upstream
    spelling; `pin_sscb_*` is a deprecated alias in an anonymous union, present
    only in esp32-camera ≥ v2.0.2).

## Which entry point should a builder use?

- **You have VS Code and want a debugger or serial plots:** `src/main.cpp` (PlatformIO).
- **You have the Arduino IDE and no terminal:** the `.ino`. Open the *file*; the
  IDE will offer to move it into a folder named `BlindSight_Main_ESP32` — accept,
  then build from there. The folder layout in git is arranged for PlatformIO,
  which requires the sketch in the project root's `src/`.
- **You have neither and just want it on the board:** the PowerShell installer.

## Platform pins

Both `platformio.ini` files pin `platform = espressif32@6.13.0`
(Arduino core 2.0.17 / IDF 4.4). The `#if` guards above mean the code also
compiles on core 3.x; the comments in each `platformio.ini` document the
one-line change to move there. Do not unpin without re-reading those comments.
