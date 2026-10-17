# BlindSight Firmware Variants Guide

This directory contains the firmware for the BlindSight wearable assistance device across two microcontrollers:
1. **ESP32 DevKit V1 (Main Unit)** — located in `firmware/esp32_main/`
2. **AI-Thinker ESP32-CAM (Vision Capture)** — located in `firmware/esp32_cam/`

---

## Firmware Entry Points & Toolchains

The repository supports three workflows to accommodate developers, makers, and non-technical builders. All three variants implement identical safety contracts, network protocols, packetization schemes, and audio drivers:

### 1. PlatformIO (Recommended for Production & CI)
- **Files**:
  - `firmware/esp32_main/src/main.cpp` + `firmware/esp32_main/platformio.ini`
  - `firmware/esp32_cam/src/main.cpp` + `firmware/esp32_cam/platformio.ini`
- **Configuration**: Copy `include/secrets.example.h` to `include/secrets.h` and fill in Wi-Fi SSID, password, Pi IP, and MAC addresses.
- **Platform Pinning**: Pins `platform = espressif32@6.13.0` (Arduino-ESP32 2.0.17 / ESP-IDF 4.4) for reproducible deterministic builds.
- **Dual-Core Guard**: Includes `#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3` so code compiles cleanly on both ESP32 Core 2.x and Core 3.x.

### 2. Arduino IDE (Beginner & Visual Workflow)
- **Files**:
  - `firmware/esp32_main/BlindSight_Main_ESP32.ino` (select `ESP32 Dev Module`)
  - `firmware/esp32_cam/BlindSight_Camera.ino` (select `AI Thinker ESP32-CAM`)
- **Configuration**: Edit the top 4 lines in each `.ino` sketch directly.
- **Folder Convention**: When opening in Arduino IDE, ensure the `.ino` file is in a directory of the exact same name or open via Arduino IDE file picker.

### 3. PowerShell One-Click Script (Automated Windows Setup)
- **File**: `docs/hardware/Install_BlindSight_Main_ESP32.ps1`
- **Usage**: Automatically downloads `arduino-cli`, installs the ESP32 core, prompts securely for Wi-Fi credentials, compiles, and flashes the ESP32 via USB serial COM port.
- **Security**: Cleans up temporary credential files from disk after upload.

---

## Core Safety Architecture Maintained Across All Variants

1. **ESP-NOW Frame Reception**:
   - 220-byte chunks with 16-bit packet checksum verification (`sum16`).
   - Transmitter MAC filtering (`isCamera` / `CAMERA_MAC` checking) to reject rogue frames on the Wi-Fi channel.
   - Partial-frame timeout (3-second silence reset via `lastPacketAt`) preventing corrupted partial JPEGs from reaching the inference engine.
2. **Audio Streaming & I2S**:
   - Bounded channel count (`channels < 1` guard) and alignment-safe header reading (`memcpy`).
   - Dynamic `/audio/<request_id>` routing parsed from `/analyse` JSON reply (`audioPathFromReply`).
   - 60-second HTTP client timeouts to allow edge model inference and espeak-ng generation to complete cleanly.
3. **Fail-Closed Offline Ultrasonic Fallback**:
   - Bounded 20-second Wi-Fi connection attempt in `setup()` with `WiFi.setSleep(false)`.
   - Never hangs in setup: if Wi-Fi or gateway Pi is absent, HC-SR04 obstacle sensors and LittleFS pre-recorded WAV voice clips operate 100% locally and immediately on button press.
