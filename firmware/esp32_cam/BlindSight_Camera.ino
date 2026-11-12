/* ============================================================
   BlindSight - ESP32-CAM (AI Thinker)  (canonical firmware, v1.5.0)
   ------------------------------------------------------------
   UPLOAD THIS SECOND. You must first run the MAIN ESP32 sketch
   and copy the "MAIN MAC" it prints into BLINDSIGHT_MAIN_ESP_MAC
   (include/secrets.h for PlatformIO, or a secrets.h next to this
   sketch for Arduino IDE).

   Board to select in Arduino IDE:  "AI Thinker ESP32-CAM"
   Upload Speed: 115200
   Before uploading: GPIO0 -> GND, then press RST.
   After uploading:  remove GPIO0 jumper, press RST.

   v1.5 — ZERO-REFLASH EXHIBIT SETUP
   --------------------------------
   The camera no longer hardcodes Wi-Fi SSID/password. It joins the
   same venue Wi-Fi as the main ESP32 via the tzapu/WiFiManager
   captive portal "BlindSight-Cam-Setup" (the camera has no edge-server
   IP of its own; it sends JPEG chunks to the main ESP32 over ESP-NOW).
   Credentials persist in ESP32 NVS across reboots and venue changes.

   Required library: WiFiManager by tzapu (>= 2.0.17).
   The PlatformIO entry point src/main.cpp #includes this file, so there
   is exactly ONE source of truth. See firmware/FIRMWARE_VARIANTS.md.
   ============================================================ */

#include <Arduino.h>
#include <WiFi.h>
#include <WiFiManager.h>   // tzapu/WiFiManager — captive configuration portal
#include <esp_now.h>
#include "esp_camera.h"

#define BLINDSIGHT_FIRMWARE_VERSION "1.5.0"

// Hardware pairing MAC only. Wi-Fi is configured via the portal, not here.
#if __has_include("secrets.h")
#include "secrets.h"
#endif
#ifndef BLINDSIGHT_MAIN_ESP_MAC
#define BLINDSIGHT_MAIN_ESP_MAC {0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF}
#endif
uint8_t MAIN_ESP_MAC[6] = BLINDSIGHT_MAIN_ESP_MAC;

const uint32_t CAPTURE_EVERY_MS = 10000;          // one photo every 10 s
const uint32_t WIFI_CONNECT_TIMEOUT_MS = 8000;    // fast saved-Wi-Fi attempt
const uint32_t PORTAL_TIMEOUT_S = 180;            // portal auto-closes after 3 min
const uint32_t WIFI_RETRY_INTERVAL_MS = 30000;

#define PWDN_GPIO_NUM  32
#define RESET_GPIO_NUM -1
#define XCLK_GPIO_NUM   0
#define SIOD_GPIO_NUM  26
#define SIOC_GPIO_NUM  27
#define Y9_GPIO_NUM    35
#define Y8_GPIO_NUM    34
#define Y7_GPIO_NUM    39
#define Y6_GPIO_NUM    36
#define Y5_GPIO_NUM    21
#define Y4_GPIO_NUM    19
#define Y3_GPIO_NUM    18
#define Y2_GPIO_NUM     5
#define VSYNC_GPIO_NUM 25
#define HREF_GPIO_NUM  23
#define PCLK_GPIO_NUM  22

constexpr uint16_t MAGIC = 0xB517;
constexpr size_t   CHUNK = 220;

struct __attribute__((packed)) ImagePacket {
  uint16_t magic, imageId, index, count, bytes, checksum;
  uint8_t data[CHUNK];
};

uint16_t imageId = 1;
uint32_t lastCapture = 0;
WiFiManager wm;

uint16_t checksum16(const uint8_t *d, size_t n) {
  uint16_t s = 0; for (size_t i = 0; i < n; ++i) s += d[i]; return s & 0xffff;
}

bool connectSavedWiFiFast() {
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);  // ESP-NOW drops frames with modem sleep on
  WiFi.begin();          // credentials stored in NVS by WiFiManager / ESP core
  Serial.print("Connecting to saved Wi-Fi");
  uint32_t started = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - started < WIFI_CONNECT_TIMEOUT_MS) {
    delay(150); Serial.print('.');
  }
  Serial.println();
  return WiFi.status() == WL_CONNECTED;
}

void startConfigPortal() {
  Serial.println();
  Serial.println("=====================================================");
  Serial.println("  BlindSight-Cam-Setup captive portal");
  Serial.println("  1. On your phone, join Wi-Fi: BlindSight-Cam-Setup");
  Serial.println("  2. Open http://192.168.4.1 if the page does not pop up");
  Serial.println("  3. Select the SAME venue Wi-Fi the main ESP32 uses.");
  Serial.println("=====================================================");
  wm.setConfigPortalTimeout(PORTAL_TIMEOUT_S);
  bool connected = wm.startConfigPortal("BlindSight-Cam-Setup");
  if (connected) {
    Serial.println("[portal] Configuration saved. Restarting to apply...");
    delay(500);
    ESP.restart();
  }
  Serial.println("[portal] Timed out. Continuing on default ESP-NOW channel.");
}

bool initCamera() {
  camera_config_t c = {};
  c.ledc_channel = LEDC_CHANNEL_0; c.ledc_timer = LEDC_TIMER_0;
  c.pin_d0 = Y2_GPIO_NUM; c.pin_d1 = Y3_GPIO_NUM;
  c.pin_d2 = Y4_GPIO_NUM; c.pin_d3 = Y5_GPIO_NUM;
  c.pin_d4 = Y6_GPIO_NUM; c.pin_d5 = Y7_GPIO_NUM;
  c.pin_d6 = Y8_GPIO_NUM; c.pin_d7 = Y9_GPIO_NUM;
  c.pin_xclk = XCLK_GPIO_NUM; c.pin_pclk = PCLK_GPIO_NUM;
  c.pin_vsync = VSYNC_GPIO_NUM; c.pin_href = HREF_GPIO_NUM;
  // pin_sccb_* is the current upstream spelling; pin_sscb_* is a deprecated alias.
  c.pin_sccb_sda = SIOD_GPIO_NUM; c.pin_sccb_scl = SIOC_GPIO_NUM;
  c.pin_pwdn = PWDN_GPIO_NUM; c.pin_reset = RESET_GPIO_NUM;
  c.xclk_freq_hz = 20000000;
  c.pixel_format = PIXFORMAT_JPEG;
  c.frame_size = FRAMESIZE_QVGA;
  c.jpeg_quality = 14;
  c.fb_count = 1;
  return esp_camera_init(&c) == ESP_OK;
}

void sendPhoto() {
  camera_fb_t *fb = esp_camera_fb_get();
  if (!fb) { Serial.println("Camera capture failed"); return; }
  uint16_t count = (fb->len + CHUNK - 1) / CHUNK;
  if (count == 0 || count > 300) {
    Serial.println("JPEG too large"); esp_camera_fb_return(fb); return;
  }
  Serial.printf("Sending JPEG: %u bytes, %u packets\n", (unsigned)fb->len, count);
  for (uint16_t i = 0; i < count; i++) {
    ImagePacket p = {};
    size_t offset = i * CHUNK;
    size_t n = (fb->len - offset) < CHUNK ? (fb->len - offset) : CHUNK;
    p.magic = MAGIC; p.imageId = imageId; p.index = i;
    p.count = count; p.bytes = n;
    memcpy(p.data, fb->buf + offset, n);
    p.checksum = checksum16(p.data, n);
    // Sent twice to survive packet loss; receiver ignores duplicates.
    for (int attempt = 0; attempt < 2; attempt++) {
      if (esp_now_send(MAIN_ESP_MAC, (uint8_t *)&p, sizeof(p)) != ESP_OK) {
        Serial.printf("esp_now_send failed for chunk %u\n", i);
      }
      delay(4);
    }
  }
  imageId++;
  esp_camera_fb_return(fb);
}

void setup() {
  Serial.begin(115200); delay(300);
  Serial.println();
  Serial.printf("BlindSight camera firmware v%s\n", BLINDSIGHT_FIRMWARE_VERSION);
  Serial.printf("CAM MAC: %s\n", WiFi.macAddress().c_str());

  if (!connectSavedWiFiFast()) {
    Serial.println("[boot] Saved Wi-Fi unavailable.");
    startConfigPortal();
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("Wi-Fi channel: %d (matches AP)\n", WiFi.channel());
  } else {
    Serial.println("Wi-Fi not found. Continuing in offline mode on default channel.");
  }

  if (!initCamera()) { Serial.println("Camera init FAILED"); while (true) delay(1000); }
  Serial.println("Camera OK");
  if (esp_now_init() != ESP_OK) { Serial.println("ESP-NOW init failed"); while (true) delay(1000); }
  esp_now_peer_info_t peer = {};
  memcpy(peer.peer_addr, MAIN_ESP_MAC, 6);
  peer.channel = 0; peer.encrypt = false;
  if (esp_now_add_peer(&peer) != ESP_OK) { Serial.println("Peer add failed"); while (true) delay(1000); }
  Serial.println("Ready. Capturing every 10 seconds.");
}

void loop() {
  static uint32_t lastWifiRetry = 0;
  if (WiFi.status() != WL_CONNECTED && millis() - lastWifiRetry > WIFI_RETRY_INTERVAL_MS) {
    lastWifiRetry = millis();
    Serial.println("[net] Wi-Fi dropped; attempting reconnect...");
    WiFi.begin();
  }
  if (millis() - lastCapture > CAPTURE_EVERY_MS) {
    lastCapture = millis();
    sendPhoto();
  }
}
