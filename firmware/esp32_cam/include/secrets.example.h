#pragma once

// BlindSight ESP32-CAM — optional hardware-pairing overrides.
//
// v1.5: Wi-Fi SSID/password are NO LONGER set here. They are entered once in
// the "BlindSight-Cam-Setup" captive portal and stored in ESP32 NVS. Copy this
// file to secrets.h ONLY to override the ESP-NOW main-board pairing MAC.

#define BLINDSIGHT_MAIN_ESP_MAC {0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF}
