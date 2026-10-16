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
   sudo apt install -y python3-flask python3-pil espeak-ng
   ```
6. Create folders and copy the project server file:
   ```bash
   mkdir -p ~/blindsight/pi_server
   ```
   Copy `blindsight/pi_server/server.py` into `~/blindsight/pi_server/`.
7. Start the test server:
   ```bash
   cd ~/blindsight/pi_server
   python3 server.py
   ```
8. In another Pi terminal, test it:
   ```bash
   curl http://127.0.0.1:5000/health
   ```
   Expected result: `{"status":"ok"}`.

> The supplied server has a safe test placeholder message until you connect your own local vision model. Do not use it as a real mobility aid before the model and all safety behaviour are validated.

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

## 2. Wire the hardware

Follow `PIN_CONNECTION_CHART.md` exactly. Before connecting power, check these essential points:

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
8. Open `blindsight/main_esp32` first.

## 4. Configure and upload the main ESP32

1. In VS Code, click `src` → `main.cpp`.
2. At the top, replace:
   ```cpp
   const char *WIFI_SSID = "BlindSight";
   const char *WIFI_PASSWORD = "CHANGE_ME";
   const char *PI_URL = "http://192.168.4.1:5000";
   ```
   with your actual hotspot/router details and Pi IP.
3. Connect the **main ESP32 DevKit V1** to the computer using a data-capable USB cable.
4. At the bottom PlatformIO toolbar, click the **right-arrow Upload button**.
5. Click the **plug/serial-monitor icon** after upload. Set serial speed to **115200**.
6. Copy the printed `MAIN MAC` address.

## 5. Configure and upload the ESP32-CAM

1. Click **File** → **Open Folder** and open `blindsight/camera_esp32cam`.
2. Open `src/main.cpp`.
3. Enter the same Wi-Fi name/password.
4. Replace `MAIN_ESP_MAC` with the six hexadecimal bytes from the main board’s serial monitor.
5. Connect the ESP32-CAM to a USB-TTL adapter:
   - 5 V → 5 V
   - GND → GND
   - Adapter TX → ESP32-CAM U0R
   - Adapter RX → ESP32-CAM U0T
   - GPIO 0 → GND **only for uploading**
6. Click PlatformIO **Upload**.
7. When upload completes, remove GPIO 0 from GND and press the ESP32-CAM **RST** button.
8. Open Serial Monitor at 115200 and copy the printed `CAM MAC` address.
9. Return to the main ESP32 project, paste it into `CAMERA_MAC`, then upload main ESP32 firmware again.

## 6. Make and upload the local voice clips

The main ESP32 speaks critical fallback instructions from its own memory. On the Pi or Linux computer, open Terminal and run:

```bash
cd /path/to/blindsight/main_esp32/data
espeak-ng -v en -s 155 -w analysing.wav "Analysing"
espeak-ng -v en -s 155 -w left.wav "Obstacle ahead. Move left."
espeak-ng -v en -s 155 -w right.wav "Obstacle ahead. Move right."
espeak-ng -v en -s 155 -w stop.wav "Stop. Obstacle very close ahead."
espeak-ng -v en -s 155 -w careful.wav "Object ahead. Proceed carefully."
espeak-ng -v en -s 155 -w clear.wav "Path clear."
```

1. Reopen `blindsight/main_esp32` in VS Code.
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
