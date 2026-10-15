# BlindSight firmware and Pi server

## Files
- `PIN_CONNECTION_CHART.md` — all power and signal connections.
- `camera_esp32cam` — AI Thinker camera captures QVGA JPEGs and sends numbered ESP-NOW packets.
- `main_esp32` — receives packets, uploads a completed JPEG to Pi, plays a WAV through PCM5102, and reads the three ultrasonic sensors.
- `pi_server/server.py` — receives JPEG at `/analyse`, calls a local model command, creates a 16-bit WAV TTS result, and serves it at `/audio`.

## Mandatory configuration
1. Start the main ESP32 once and note `MAIN MAC` in its serial monitor.
2. Put that value into `MAIN_ESP_MAC` in `camera_esp32cam/src/main.cpp`.
3. Start the camera once and note `CAM MAC`.
4. Put it into `CAMERA_MAC` in `main_esp32/src/main.cpp`. (It is retained as an explicit allow-list/configuration value.)
5. In **both** firmware files set the exact same `WIFI_SSID` and `WIFI_PASSWORD`.
6. Set `PI_URL` to the Raspberry Pi's fixed Wi-Fi address, such as `http://192.168.4.1:5000`.
7. The Pi AP must use a **fixed Wi-Fi channel**. Both ESP boards join it before ESP-NOW starts, so ESP-NOW follows that channel.

## Build and upload (PlatformIO)
Install VS Code + PlatformIO, open either board folder, then use Upload. Or:

```bash
cd main_esp32
pio run -t upload
pio run -t uploadfs       # uploads data/*.wav

cd ../camera_esp32cam
pio run -t upload
```

For ESP32-CAM programming: connect GPIO 0 to GND, reset, upload; then disconnect GPIO 0 from GND and reset.

## Create the local speech clips

The firmware needs these **16-bit PCM WAV** files in `main_esp32/data/`: `analysing.wav`, `left.wav`, `right.wav`, `stop.wav`, `careful.wav`, and `clear.wav`. On the Pi (or any Linux computer with espeak-ng), create them before `pio run -t uploadfs`:

```bash
cd /path/to/blindsight/main_esp32/data
espeak-ng -v en -s 155 -w analysing.wav "Analysing"
espeak-ng -v en -s 155 -w left.wav "Obstacle ahead. Move left."
espeak-ng -v en -s 155 -w right.wav "Obstacle ahead. Move right."
espeak-ng -v en -s 155 -w stop.wav "Stop. Obstacle very close ahead."
espeak-ng -v en -s 155 -w careful.wav "Object ahead. Proceed carefully."
espeak-ng -v en -s 155 -w clear.wav "Path clear."
```

## Pi setup

```bash
sudo apt update
sudo apt install -y python3-flask python3-pil espeak-ng
mkdir -p ~/blindsight/pi_server
# copy pi_server files into this directory
cd ~/blindsight/pi_server
python3 server.py
```

Test with `curl http://PI_IP:5000/health`.

### Attach your real local AI model
The server deliberately does not invent a model. Provide a program/command that takes the JPEG path as its final argument and prints one concise safe navigation sentence. Then launch, for example:

```bash
export VISION_COMMAND='/home/pi/blindsight/my_local_vision_model'
python3 server.py
```

The Pi 3B+ is limited; use a small quantized local model and always retain ultrasonic fallback. The included default is a test message only.

## Behaviour
- Camera sends one QVGA JPEG every 10 seconds (adjust `CAPTURE_EVERY_MS`).
- Main ESP plays `/analysing.wav` immediately after complete image receipt, then uploads the JPEG.
- Pi makes 16-bit PCM WAV via `espeak-ng`.
- Main ESP streams it to I2S DAC.
- A fallback-button press executes the local ultrasonic rule. The main ESP plays a matching pre-recorded urgent WAV clip from LittleFS, so ultrasonic warnings do not wait for Pi/model latency.

## Important limitations and test plan
ESP-NOW is not an ideal image transport. This build uses QVGA, checksummed numbered packets and duplicate transmission; test it stationary first. It does not use an application ACK/retry protocol. If packets are regularly lost, lower JPEG size/quality or switch camera-to-Pi transfer to ordinary Wi-Fi HTTP.

Never use the vision result as the only mobility safety mechanism. Verify voltage dividers on **all** HC-SR04 ECHO pins before connecting to ESP32.
