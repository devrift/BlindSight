# BlindSight — Click-by-Click Build, Upload & Test Guide

> ⚠️ **SUPERSEDED WIRING NOTICE**
> Any legacy references in early revisions to dual isolated Arduino rails are superseded by [WIRING_V2_BUCK_AND_TTL.md](WIRING_V2_BUCK_AND_TTL.md).
> All microcontrollers and sensors must share a single 5V 3A buck converter rail and a common ground.

This guide takes you from hardware wiring to a working first demo. Do not wear the system until all bench tests pass.

---

## 1. Prepare the software

### 1.1 Raspberry Pi 3B+ Gateway
1. Install **Raspberry Pi OS (64-bit)** with Raspberry Pi Imager.
2. In Raspberry Pi Imager, click the **gear icon** before writing the SD card.
3. Set username (`pi`) and password, configure Wi-Fi if desired, and enable SSH.
4. Boot the Pi. On its desktop or via SSH, open Terminal.
5. Run:
   ```bash
   sudo apt update
   sudo apt install -y python3-pip espeak-ng
   ```
6. Get the project onto the Pi and install the gateway dependencies:
   ```bash
   git clone https://github.com/devrift/BlindSight.git ~/BlindSight
   cd ~/BlindSight
   python3 -m pip install -r edge_server/requirements.txt
   ```
   (The old `blindsight/pi_server/` directory no longer exists; the server is
   `edge_server/server.py`.)
7. Configure it:
   ```bash
   cp edge_server/.env.example edge_server/.env
   nano edge_server/.env      # add OPENROUTER_API_KEY for the cloud companion
   ```
8. Start the test server:
   ```bash
   python3 edge_server/server.py
   ```
9. In another Pi terminal, test it:
   ```bash
   curl http://127.0.0.1:5000/health
   ```
   The reply is a JSON report of every component. Before you export a model it
   will say `"status":"degraded"` or `"status":"error"` and list the reasons in
   `"issues"` — that is the gateway being honest, not a fault. Once the model
   and `espeak-ng` are in place it says `"status":"ok"`.

> The supplied server speaks a safe fallback message until a local vision model
> is connected. Do not use it as a real mobility aid before the model and all
> safety behaviour are validated. A missing model is deliberately reported as a
> failure: the gateway never announces "path clear" when it could not see.

### 1.2 Create a fixed Wi-Fi network
Use either your normal router or the Raspberry Pi as a Wi-Fi access point (AP). A Pi access point is recommended for wearable mobility.

1. On the Pi desktop, click the **network icon** in the top-right corner.
2. Click **Advanced Options** → **Create Wireless Hotspot**.
3. Set the name to `BlindSight` and set a strong password.
4. Keep the Wi-Fi channel fixed (channel 1 or 6).
5. Note the Pi IP address with `hostname -I`.
6. Example gateway IP: `192.168.4.1`.

Both ESP32 boards join the **same Wi-Fi network** so they share a radio channel for ESP-NOW and can reach the Pi HTTP endpoints.

---

> **WIRING VERSION NOTE.** This guide's power section and `PIN_CONNECTION_CHART.md`
> describe the original two-Arduino rail design (v1). That design is
> **superseded**: use `WIRING_V2_BUCK_AND_TTL.md`, which replaces both Arduinos
> with one 5 V 3 A buck converter and a single common ground. The pin table
> below (which GPIO goes where) is still correct; only the *power rails*
> differ. If you printed an older copy of this page, the line "Do not connect
> the separate 5 V rails together" no longer applies — v2 has exactly one rail.

## 2. Wire the hardware

Follow `WIRING_V2_BUCK_AND_TTL.md` for power, and the pin table in
`PIN_CONNECTION_CHART.md` for signals. Before connecting power, check these essential points:

- **Common Ground**: All components (Main ESP32, ESP32-CAM, DAC, 3x HC-SR04, Buck regulator) share one common ground.
- **5V 3A Buck Rail**: Powers the sensors and microcontrollers.
- **ECHO Voltage Dividers**: Never connect HC-SR04 ECHO directly to an ESP32 GPIO. Use a 1kΩ / 2kΩ divider to step 5V down to ~3.3V.
- **Main ESP32 DAC**: GPIO 26 → BCLK, GPIO 25 → LRCK/WS, GPIO 22 → DIN.
- **Ultrasonic TRIG pins**: GPIO 13 (L), GPIO 14 (C), GPIO 27 (R).
- **Ultrasonic divided ECHO pins**: GPIO 34 (L), GPIO 35 (C), GPIO 39 (R).
- **Button**: GPIO 32 to GND (internal pull-up enabled).
- **ESP32-CAM**: 470–1000 µF low-ESR electrolytic capacitor across 5V and GND directly at the camera pins.

---

1. On a computer, install **Visual Studio Code** from `code.visualstudio.com`.
2. Open Visual Studio Code.
3. Click the **Extensions** icon on the left sidebar (four squares).
4. Search for **PlatformIO IDE**.
5. Click **Install**.
6. When installation finishes, click **Reload / Restart VS Code** if asked.
7. Click **File** → **Open Folder**.
8. Open `firmware/esp32_main` first.

## 4. Configure and upload the main ESP32

1. In VS Code, copy the example secrets file and open it:
   ```
   include/secrets.example.h  ->  include/secrets.h
   ```
   `src/main.cpp` will not compile until `include/secrets.h` exists; it stops
   with a clear `#error` telling you to create it.
2. In `include/secrets.h`, set your real values:
   ```cpp
   #define BLINDSIGHT_WIFI_SSID "BlindSight"
   #define BLINDSIGHT_WIFI_PASSWORD "your password"
   #define BLINDSIGHT_PI_HOST "192.168.4.1"
   #define BLINDSIGHT_PI_PORT 5000
   #define BLINDSIGHT_CAMERA_MAC {0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF}
   ```
   `secrets.h` is git-ignored, so your password never lands in a commit.
   (If you use the Arduino IDE `.ino` instead of PlatformIO, the same values are
   plain lines at the top of the sketch — see `firmware/FIRMWARE_VARIANTS.md`.)
3. Connect the **main ESP32 DevKit V1** to the computer using a data-capable USB cable.
4. At the bottom PlatformIO toolbar, click the **right-arrow Upload button**.
5. Click the **plug/serial-monitor icon** after upload. Set serial speed to **115200**.
6. Copy the printed `MAIN MAC` address.

## 5. Configure and upload the ESP32-CAM

1. Click **File** → **Open Folder** and open `firmware/esp32_cam`.
2. Open `src/main.cpp`.
3. Enter the same Wi-Fi name/password.
4. Create `include/secrets.h` from `include/secrets.example.h` (the sketch
    `#error`s without it) and set `BLINDSIGHT_MAIN_ESP_MAC` to the six
    hexadecimal bytes from the main board’s serial monitor.
5. Connect the ESP32-CAM to a USB-TTL adapter:
   - 5 V → 5 V
   - GND → GND
   - Adapter TX → ESP32-CAM U0R
   - Adapter RX → ESP32-CAM U0T
   - GPIO 0 → GND **only for uploading**
6. Click PlatformIO **Upload**.
7. When upload completes, remove GPIO 0 from GND and press the ESP32-CAM **RST** button.
8. Open Serial Monitor at 115200 and copy the printed `CAM MAC` address.
9. Return to the main ESP32 project and paste it as `BLINDSIGHT_CAMERA_MAC` in
   `include/secrets.h` (PlatformIO) or as `CAMERA_MAC` at the top of the
   `.ino` (Arduino IDE), then upload the main ESP32 firmware again. Packets
   from any other transmitter are ignored.

### Option B: Using Arduino IDE
1. Open `firmware/esp32_cam/BlindSight_Camera.ino`.
2. Select board: `AI Thinker ESP32-CAM`.
3. Configure `WIFI_SSID`, `WIFI_PASSWORD`, and `MAIN_ESP_MAC`.
4. Connect with GPIO 0 grounded, click **Upload**, remove GPIO 0 jumper, and reset.

---

## 6. Upload Local Voice Clips to LittleFS

The main ESP32 plays pre-recorded voice clips for instant, zero-latency offline feedback.
Pre-generated 16-bit 22050 Hz WAV clips are shipped in `firmware/esp32_main/data/`:
- `analysing.wav`
- `left.wav`
- `right.wav`
- `stop.wav`
- `careful.wav`
- `clear.wav`

To generate or re-verify these files:
```bash
cd /path/to/BlindSight
./firmware/esp32_main/data/tools/generate_clips.py
```

The script runs exactly those `espeak-ng` commands for you and then verifies
every clip is 16-bit mono 22050 Hz WAV with `tools/verify_wav.py`, because
`playFile()` streams raw PCM to a DAC clocked at that rate. If it reports a
failure, do not upload the clips.

Without these clips the button/ultrasonic fallback is **silent**: `playFile()`
returns quietly when a file is missing.

1. Reopen `firmware/esp32_main` in VS Code.
2. Connect the main ESP32 by USB.
3. Press `Ctrl+Shift+P`.
4. Type **PlatformIO: Upload Filesystem Image**.
5. Click that command and wait for success.

## 7. Bench-test in this order

1. **Pi Health Check:** Open `http://PI_IP:5000/health` in a browser; verify `status` is `ok`.
2. **Camera Stream Check:** Power ESP32-CAM; verify packet transmission in Serial Monitor.
3. **Main Gateway Check:** Verify main ESP32 receives ESP-NOW packets and uploads frames to `/analyse`.
4. **Audio Playback:** Connect earphones to the PCM5102 DAC output; verify local chime/speech on upload, followed by Pi AI guidance audio.
5. **Ultrasonic Sensor Check:** Wave a hand in front of left, center, and right HC-SR04 sensors; press the button and confirm directional voice prompts ("Move left", "Move right", "Stop", "Proceed carefully").
6. **Thermal & Stability Test:** Run the system continuously for 15 minutes to verify power stability and absence of camera reboots.

---

## 8. Common Troubleshooting

| Problem | Check first |
|---|---|
| ESP32-CAM resets | 5V 3A buck rail, 470–1000 µF capacitor, short power leads |
| No ESP-NOW image | Matching Wi-Fi SSID/channel, correct MAIN MAC address, sender MAC validation |
| Pi upload 404/500 | Verify PI_URL, check `python3 server.py` logs in terminal |
| No DAC sound | Verify I2S pins (26, 25, 22), check LittleFS filesystem upload, common ground |
| Wrong ultrasonic readings | Verify 1k/2k voltage dividers on ECHO lines, ensure sensors face forward |
| ESP32-CAM upload timeout | Ensure GPIO 0 is grounded during power-up before clicking upload |
