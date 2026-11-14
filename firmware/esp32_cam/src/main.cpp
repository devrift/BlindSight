// BlindSight ESP32-CAM — PlatformIO entry point.
//
// Single source of truth: the canonical firmware lives in the Arduino IDE
// sketch one directory up. We include it directly so PlatformIO and Arduino
// IDE builds can never drift apart. See firmware/FIRMWARE_VARIANTS.md.
//
// Wi-Fi credentials are configured at runtime via the tzapu/WiFiManager
// "BlindSight-Cam-Setup" captive portal and persisted in NVS. No secrets.h
// Wi-Fi defines are required anymore.

#include "../BlindSight_Camera.ino"
