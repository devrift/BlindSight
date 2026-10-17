# BlindSight — Click-by-Click Build, Upload & Test Guide

This guide takes you from hardware wiring to a working first demo. Do not wear the system until all bench tests pass.

## 1. Prepare the software

### 1.1 Raspberry Pi 3B+
1. Install **Raspberry Pi OS (64-bit)** with Raspberry Pi Imager.
2. In Raspberry Pi Imager, click the **gear icon** before writing the SD card.
3. Set a username and password, configure Wi-Fi if desired, and enable SSH.
4. Boot the Pi. On its desktop, click the terminal icon.
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
Use either your normal router or the Raspberry Pi as a Wi-Fi access point. A Pi access point is recommended for a portable demo.

1. On the Pi desktop, click the **network icon** in the top-right corner.
2. Click **Advanced Options** → **Create Wireless Hotspot** (wording may vary by Raspberry Pi OS version).
3. Set the name to `BlindSight`.
4. Set a strong password.
5. Keep the Wi-Fi channel fixed if the settings page offers it; channel 6 is a good choice.
6. Note the Pi IP address. In Terminal run:
   ```bash
   hostname -I
   ```
7. Use the first local IPv4 address in `PI_URL` in the main ESP32 firmware. Example: `http://192.168.4.1:5000`.

Both ESP32 boards must join the **same Wi-Fi network** before ESP-NOW starts.

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

- Connect each HC-SR04 **ECHO** to the ESP32 only through its 1 kΩ / 2 kΩ voltage divider.
- Main ESP32: GPIO 26 → DAC BCLK, GPIO 25 → DAC LRCK, GPIO 22 → DAC DIN.
- Ultrasonic TRIG pins: GPIO 13, 14 and 27.
- Ultrasonic divided ECHO pins: GPIO 34, 35 and 39.
- Button: GPIO 32 to GND; the code uses its internal pull-up.
- ESP32-CAM: power at 5 V, with a 470–1000 µF capacitor directly across 5 V and GND.
- Do **not** connect the separate 5 V rails together.
- Verify polarity twice before power-on.

## 3. Install PlatformIO

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

## 6. Make and upload the local voice clips

The main ESP32 speaks critical fallback instructions from its own memory. On the Pi or Linux computer, open Terminal and run:

```bash
cd /path/to/BlindSight
./firmware/esp32_main/data/generate_clips.sh
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

1. **Pi test:** open `http://PI_IP:5000/health` in a browser; it must show `status: ok`.
2. **Camera test:** power ESP32-CAM; its Serial Monitor should show capture and packet-send messages every 10 seconds.
3. **Main receiver test:** main ESP32 Serial Monitor should print image upload status and an HTTP 200 response.
4. **Audio test:** connect a suitable headphone amplifier/earphones to DAC output and verify “Analysing,” then the Pi response.
5. **Sensor test:** hold an object approximately 20–80 cm in front of each HC-SR04. Press the fallback button and verify the relevant spoken warning.
6. **Power test:** leave the system on for 10 minutes. If camera resets, use shorter/thicker 5 V wiring and ensure the ESP32-CAM capacitor is installed.

## 8. Before mounting on the headband

- Keep sensors aimed forward: left, centre, and right, with enough separation to reduce cross-talk.
- Secure the ESP32-CAM to the spectacle frame without blocking its lens.
- Put main ESP32 and DAC in the 10 × 7 × 5 cm enclosure; do not allow loose wires to short.
- Keep antenna areas away from metal and large batteries where possible.
- Test with a sighted helper in an open, controlled indoor space.
- The ultrasonic fallback is for close obstacles only; it cannot detect every hazard, drop, moving vehicle, stair edge, or overhead obstacle.

## 9. Common fixes

| Problem | Check first |
|---|---|
| ESP32-CAM resets | 5 V supply, 470–1000 µF capacitor, short wires, adequate current |
| No ESP-NOW image | same Wi-Fi SSID/channel, correct main MAC, both boards connected before ESP-NOW starts |
| Pi upload fails | correct `PI_URL`, server running, Pi and ESP32 on same network |
| No DAC sound | DAC BCLK/LRCK/DIN pins, common GND on main rail, correct earphone amplifier |
| Wrong ultrasonic readings | ECHO voltage divider, sensor orientation, trigger one at a time |
| ESP32-CAM cannot upload | GPIO 0 to GND during upload; remove it and reset for normal operation |
