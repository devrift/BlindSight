/* ============================================================
   BlindSight - ESP32-CAM (AI Thinker)
   ------------------------------------------------------------
   UPLOAD THIS SECOND. You must first run the MAIN ESP32 sketch
   and copy the "MAIN MAC" it prints into MAIN_ESP_MAC below.

   Board to select in Arduino IDE:  "AI Thinker ESP32-CAM"
   Upload Speed: 115200
   Before uploading: GPIO0 -> GND, then press RST.
   After uploading:  remove GPIO0 jumper, press RST.
   ============================================================ */

#include <Arduino.h>
#include <WiFi.h>
#include <esp_now.h>
#include "esp_camera.h"

// ##############################################################
// ###############  EDIT THESE 3 LINES ONLY  ####################
const char *WIFI_SSID     = "BlindSight";   // same hotspot as main ESP32
const char *WIFI_PASSWORD = "CHANGE_ME";    // same password

// Paste the MAIN MAC here. If Serial Monitor showed
//     MAIN MAC: 3C:71:BF:12:34:56
// then write it as:  {0x3C,0x71,0xBF,0x12,0x34,0x56}
uint8_t MAIN_ESP_MAC[6] = {0xAA,0xBB,0xCC,0xDD,0xEE,0xFF};
// ##############################################################

const uint32_t CAPTURE_EVERY_MS = 10000;   // one photo every 10 s
const uint32_t WIFI_CONNECT_TIMEOUT_MS = 20000;  // bounded association

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

uint16_t checksum16(const uint8_t *d, size_t n) {
  uint32_t s = 0; for (size_t i = 0; i < n; ++i) s += d[i]; return s & 0xffff;
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
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.println();
  Serial.println("Connecting to WiFi...");
  uint32_t started = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - started < WIFI_CONNECT_TIMEOUT_MS) { delay(300); Serial.print('.'); }
  Serial.println();
  Serial.printf("CAM MAC: %s\n", WiFi.macAddress().c_str());
  Serial.printf("Wi-Fi channel: %d (must match main ESP32)\n", WiFi.channel());
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
  if (millis() - lastCapture > CAPTURE_EVERY_MS) {
    lastCapture = millis();
    sendPhoto();
  }
}
