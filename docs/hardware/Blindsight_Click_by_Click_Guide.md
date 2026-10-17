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
   sudo apt install -y python3-flask python3-pil python3-numpy espeak-ng git
   ```
6. Clone the repository and install requirements:
   ```bash
   git clone https://github.com/lavyanshg/BlindSight.git ~/BlindSight
   cd ~/BlindSight/edge_server
   pip3 install -r requirements.txt
   cp .env.example .env
   ```
7. Start the server:
   ```bash
   python3 server.py
   ```
8. In another terminal, test it:
   ```bash
   curl http://127.0.0.1:5000/health
   ```
   Expected result: `{"status":"ok", ...}` when model and espeak are ready.

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

## 2. Wire the hardware

Follow [WIRING_V2_BUCK_AND_TTL.md](WIRING_V2_BUCK_AND_TTL.md) for the active production wiring:

- **Common Ground**: All components (Main ESP32, ESP32-CAM, DAC, 3x HC-SR04, Buck regulator) share one common ground.
- **5V 3A Buck Rail**: Powers the sensors and microcontrollers.
- **ECHO Voltage Dividers**: Never connect HC-SR04 ECHO directly to an ESP32 GPIO. Use a 1kΩ / 2kΩ divider to step 5V down to ~3.3V.
- **Main ESP32 DAC**: GPIO 26 → BCLK, GPIO 25 → LRCK/WS, GPIO 22 → DIN.
- **Ultrasonic TRIG pins**: GPIO 13 (L), GPIO 14 (C), GPIO 27 (R).
- **Ultrasonic divided ECHO pins**: GPIO 34 (L), GPIO 35 (C), GPIO 39 (R).
- **Button**: GPIO 32 to GND (internal pull-up enabled).
- **ESP32-CAM**: 470–1000 µF low-ESR electrolytic capacitor across 5V and GND directly at the camera pins.

---

## 3. Toolchains & Firmware Options

You can build and flash the firmware via either **PlatformIO** (recommended) or **Arduino IDE**. See [firmware/FIRMWARE_VARIANTS.md](../firmware/FIRMWARE_VARIANTS.md) for detailed comparisons.

---

## 4. Configure and upload the main ESP32

### Option A: Using PlatformIO (VS Code)
1. Open VS Code and open the workspace folder `firmware/esp32_main`.
2. Copy `include/secrets.example.h` to `include/secrets.h`:
   ```bash
   cp include/secrets.example.h include/secrets.h
   ```
3. Edit `include/secrets.h` with your Wi-Fi SSID, password, and Pi IP:
   ```cpp
   #define BLINDSIGHT_WIFI_SSID     "BlindSight"
   #define BLINDSIGHT_WIFI_PASSWORD "your_password"
   #define BLINDSIGHT_PI_HOST       "192.168.4.1"
   #define BLINDSIGHT_PI_PORT       "5000"
   #define BLINDSIGHT_CAMERA_MAC    {0xAA,0xBB,0xCC,0xDD,0xEE,0xFF}
   ```
4. Connect the main ESP32 to your PC via USB.
5. Click the PlatformIO **Upload** button.
6. Open Serial Monitor (115200 baud), press the ESP32 RST button, and note the printed `MAIN MAC`.

### Option B: Using Arduino IDE
1. Open `firmware/esp32_main/BlindSight_Main_ESP32.ino`.
2. Select board: `ESP32 Dev Module`.
3. Configure `WIFI_SSID`, `WIFI_PASSWORD`, and `PI_URL` in lines 20–22.
4. Click **Upload**, open Serial Monitor at 115200 baud, and note the printed `MAIN MAC`.

---

## 5. Configure and upload the ESP32-CAM

### Option A: Using PlatformIO (VS Code)
1. Open the workspace folder `firmware/esp32_cam`.
2. Copy `include/secrets.example.h` to `include/secrets.h`:
   ```bash
   cp include/secrets.example.h include/secrets.h
   ```
3. Configure `BLINDSIGHT_WIFI_SSID`, `BLINDSIGHT_WIFI_PASSWORD`, and set `BLINDSIGHT_MAIN_ESP_MAC` to the bytes noted from step 4.
4. Connect the ESP32-CAM via USB-TTL programmer:
   - 5V → 5V, GND → GND, TX → U0R, RX → U0T, **GPIO 0 → GND** (for flash mode).
5. Click **Upload**.
6. When complete, disconnect GPIO 0 from GND and press RST.
7. Note the printed `CAM MAC` in Serial Monitor. Update `CAMERA_MAC` in the main ESP32 firmware if MAC filtering is active.

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
python tools/generate_clips.py
python tools/verify_wav.py
```

To upload the clips to the ESP32 LittleFS flash partition:
1. Open `firmware/esp32_main` in VS Code with PlatformIO.
2. Connect the main ESP32 via USB.
3. Open the command palette (`Ctrl+Shift+P`) and execute **PlatformIO: Upload Filesystem Image**.

---

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
