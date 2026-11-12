#pragma once

// BlindSight main ESP32 — optional hardware-pairing overrides.
//
// v1.5: Wi-Fi SSID/password and the edge-server IP/port are NO LONGER set
// here. They are entered once in the "BlindSight-Setup" captive portal on the
// device and stored in ESP32 NVS. Copy this file to secrets.h ONLY if you want
// to override the ESP-NOW camera pairing MAC at compile time.

#define BLINDSIGHT_CAMERA_MAC {0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF}
