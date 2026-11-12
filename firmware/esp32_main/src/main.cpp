// BlindSight main ESP32 — PlatformIO entry point.
//
// Single source of truth: the canonical firmware lives in the Arduino IDE
// sketch one directory up. We include it directly so PlatformIO and Arduino
// IDE builds can never drift apart (the old three-copy layout shipped bugs
// that were fixed in only one copy). See firmware/FIRMWARE_VARIANTS.md.
//
// Wi-Fi credentials and the edge-server IP/port are configured at runtime via
// the tzapu/WiFiManager "BlindSight-Setup" captive portal and persisted with
// Preferences.h. No secrets.h Wi-Fi/IP defines are required anymore.

#include "../BlindSight_Main_ESP32.ino"
