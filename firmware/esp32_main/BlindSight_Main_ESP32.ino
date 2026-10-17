/* ============================================================
   BlindSight - MAIN ESP32 DevKit V1   (Arduino IDE entry point)
   ------------------------------------------------------------
   UPLOAD THIS FIRST. It prints "MAIN MAC: xx:xx:..." in the
   Serial Monitor. You need that MAC for the camera sketch.

   Board to select in Arduino IDE:  "ESP32 Dev Module"
   Upload Speed: 115200

   NOTE: Arduino IDE expects this file to live in a folder named
   BlindSight_Main_ESP32. It is kept in firmware/esp32_main/ for the
   PlatformIO build instead, so open this FILE via File -> Open and let
   the IDE propose moving it into its own folder when it asks.
   See firmware/FIRMWARE_VARIANTS.md.
   ============================================================ */

#include <Arduino.h>
#include <WiFi.h>
#include <esp_now.h>
#include <HTTPClient.h>
#include <LittleFS.h>
#include <driver/i2s.h>

// ##############################################################
// ###############  EDIT THESE 4 LINES ONLY  ####################
const char *WIFI_SSID     = "BlindSight";        // your hotspot name
const char *WIFI_PASSWORD = "CHANGE_ME";         // your hotspot password
const char *PI_URL        = "http://192.168.4.1:5000";  // Pi IP + :5000
// Paste the CAM MAC printed by the camera sketch, e.g. {0x3C,0x71,0xBF,0x12,0x34,0x56}
// Packets from any other transmitter are ignored.
uint8_t CAMERA_MAC[6] = {0xAA,0xBB,0xCC,0xDD,0xEE,0xFF};
// ##############################################################

#define TRIG_L 13
#define TRIG_C 14
#define TRIG_R 27
#define ECHO_L 34
#define ECHO_C 35
#define ECHO_R 39
#define BUTTON 32
#define I2S_BCK 26
#define I2S_WS  25
#define I2S_DIN 22

// Must stay identical to src/main.cpp - see firmware/FIRMWARE_VARIANTS.md.
constexpr uint32_t CLIP_SAMPLE_RATE = 22050;
constexpr uint32_t WIFI_CONNECT_TIMEOUT_MS = 20000;
constexpr int HTTP_TIMEOUT_MS = 60000;
constexpr uint32_t FRAME_STALL_TIMEOUT_MS = 3000;
constexpr uint16_t MAGIC = 0xB517;
constexpr size_t   CHUNK = 220;
constexpr size_t   MAX_JPEG = 66000;

struct __attribute__((packed)) ImagePacket {
  uint16_t magic, imageId, index, count, bytes, checksum;
  uint8_t data[CHUNK];
};

uint8_t imageBuf[MAX_JPEG];
bool received[300];
volatile bool imageReady = false;
bool acceptingFrame = true;
uint16_t activeId = 0, expectedCount = 0, receivedCount = 0;
size_t imageLen = 0;
uint32_t lastPacketAt = 0;

uint16_t sum16(const uint8_t *d, size_t n) {
  uint32_t s = 0; for (size_t i = 0; i < n; i++) s += d[i]; return s & 0xffff;
}

bool isCamera(const uint8_t *mac) { return mac && memcmp(mac, CAMERA_MAC, 6) == 0; }

void handlePacket(const uint8_t *srcMac, const uint8_t *data, int len) {
  if (!acceptingFrame || !isCamera(srcMac)) return;
  if (len != (int)sizeof(ImagePacket)) return;
  const ImagePacket *p = (const ImagePacket *)data;
  if (p->magic != MAGIC || p->count == 0 || p->count > 300 ||
      p->index >= p->count || p->bytes > CHUNK ||
      p->checksum != sum16(p->data, p->bytes)) return;

  if (p->imageId != activeId) {
    activeId = p->imageId; expectedCount = p->count;
    receivedCount = 0; imageLen = 0; memset(received, 0, sizeof(received));
  }
  size_t off = p->index * CHUNK;
  if (off + p->bytes > MAX_JPEG || p->count != expectedCount) return;
  if (!received[p->index]) {
    memcpy(imageBuf + off, p->data, p->bytes);
    received[p->index] = true; receivedCount++;
    if (p->index == p->count - 1) imageLen = off + p->bytes;
  }
  lastPacketAt = millis();
  if (receivedCount == expectedCount && imageLen > 0) imageReady = true;
}

// Works on BOTH ESP32 core 2.x and 3.x (the callback signature changed).
#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3
void onData(const esp_now_recv_info_t *info, const uint8_t *data, int len) {
  handlePacket(info->src_addr, data, len);
}
#else
void onData(const uint8_t *mac, const uint8_t *data, int len) {
  handlePacket(mac, data, len);
}
#endif

void initI2S() {
  i2s_config_t cfg = {
    .mode = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_TX),
    .sample_rate = CLIP_SAMPLE_RATE,
    .bits_per_sample = I2S_BITS_PER_SAMPLE_16BIT,
    .channel_format = I2S_CHANNEL_FMT_RIGHT_LEFT,
    .communication_format = I2S_COMM_FORMAT_STAND_I2S,
    .intr_alloc_flags = 0,
    .dma_buf_count = 8,
    .dma_buf_len = 256,
    .use_apll = false,
    .tx_desc_auto_clear = true,
    .fixed_mclk = 0
  };
  i2s_pin_config_t pins = {
    .bck_io_num = I2S_BCK,
    .ws_io_num = I2S_WS,
    .data_out_num = I2S_DIN,
    .data_in_num = I2S_PIN_NO_CHANGE
  };
  if (i2s_driver_install(I2S_NUM_0, &cfg, 0, nullptr) != ESP_OK) {
    Serial.println("I2S driver install failed");
  }
  i2s_set_pin(I2S_NUM_0, &pins);
}

void playWavStream(HTTPClient &http) {
  WiFiClient *s = http.getStreamPtr();
  uint8_t h[44];
  if (s->readBytes(h, 44) != 44 || memcmp(h, "RIFF", 4) || memcmp(h + 8, "WAVE", 4)) {
    Serial.println("Bad WAV header"); return;
  }
  uint32_t rate; uint16_t bits, channels;
  memcpy(&rate, h + 24, 4);
  memcpy(&bits, h + 34, 2);
  memcpy(&channels, h + 22, 2);
  if (bits != 16) { Serial.println("Need 16-bit WAV"); return; }
  if (channels < 1) channels = 1;
  if (channels > 2) channels = 2;
  i2s_set_sample_rates(I2S_NUM_0, rate);
  uint8_t in[512]; int16_t out[512];
  while (http.connected() || s->available()) {
    int n = s->readBytes(in, sizeof(in));
    if (n <= 0) break;
    int samples = n / 2, outn = 0;
    for (int i = 0; i < samples; i += channels) {
      int16_t v; memcpy(&v, in + i * 2, 2);
      if (outn + 2 > (int)(sizeof(out) / sizeof(out[0]))) break;
      out[outn++] = v; out[outn++] = v;
    }
    size_t done; i2s_write(I2S_NUM_0, out, outn * 2, &done, portMAX_DELAY);
  }
  i2s_set_sample_rates(I2S_NUM_0, CLIP_SAMPLE_RATE);
}

// Plays a local clip. If the file is missing it simply does nothing.
void playFile(const char *path) {
  File f = LittleFS.open(path, "r");
  if (!f) return;
  uint8_t h[44];
  if (f.read(h, 44) != 44) { f.close(); return; }
  i2s_set_sample_rates(I2S_NUM_0, CLIP_SAMPLE_RATE);
  uint8_t b[512];
  while (f.available()) {
    int n = f.read(b, sizeof(b));
    if (n <= 0) break;
    size_t w; i2s_write(I2S_NUM_0, b, n, &w, portMAX_DELAY);
  }
  f.close();
}

float distanceCM(int t, int e) {
  digitalWrite(t, LOW); delayMicroseconds(3);
  digitalWrite(t, HIGH); delayMicroseconds(10);
  digitalWrite(t, LOW);
  long d = pulseIn(e, HIGH, 30000);
  return d ? d * 0.0343 / 2 : 999;
}

const char *quickRule() {
  float l = distanceCM(TRIG_L, ECHO_L); delay(55);
  float c = distanceCM(TRIG_C, ECHO_C); delay(55);
  float r = distanceCM(TRIG_R, ECHO_R);
  Serial.printf("L=%.0f C=%.0f R=%.0f cm\n", l, c, r);
  if (c < 25) return "Obstacle critical. Stop.";
  if (c < 45) return l > r ? "Obstacle ahead. Move left." : "Obstacle ahead. Move right.";
  if (l < 45) return "Obstacle on left. Move right.";
  if (r < 45) return "Obstacle on right. Move left.";
  if (c < 100) return "Object ahead. Proceed carefully.";
  return "Path clear.";
}

// POST /analyse answers with JSON containing "audio_url":"/audio/<id>".
// The bare "/audio" path is a 404 - the route needs the request id.
String audioPathFromReply(const String &reply) {
  const char *key = "\"audio_url\":\"";
  int start = reply.indexOf(key);
  if (start < 0) return String();
  start += strlen(key);
  int end = reply.indexOf('"', start);
  return end > start ? reply.substring(start, end) : String();
}

void uploadAndPlay() {
  acceptingFrame = false;
  Serial.printf("Uploading %u bytes\n", (unsigned)imageLen);
  playFile("/analysing.wav");

  HTTPClient http;
  http.setTimeout(HTTP_TIMEOUT_MS);
  http.begin(String(PI_URL) + "/analyse");
  String boundary = "BlindSightBoundary";
  http.addHeader("Content-Type", "multipart/form-data; boundary=" + boundary);
  String pre = "--" + boundary +
    "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"image.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n";
  String post = "\r\n--" + boundary + "--\r\n";
  size_t total = pre.length() + imageLen + post.length();
  uint8_t *body = (uint8_t *)malloc(total);
  if (!body) { Serial.println("Out of memory building multipart body"); http.end(); acceptingFrame = true; return; }
  memcpy(body, pre.c_str(), pre.length());
  memcpy(body + pre.length(), imageBuf, imageLen);
  memcpy(body + pre.length() + imageLen, post.c_str(), post.length());
  int code = http.POST(body, total);
  free(body);
  String reply = http.getString();
  http.end();
  Serial.printf("AI %d: %s\n", code, reply.c_str());

  String audioPath = (code == 200) ? audioPathFromReply(reply) : String();
  if (audioPath.length()) {
    HTTPClient audio;
    audio.setTimeout(HTTP_TIMEOUT_MS);
    audio.begin(String(PI_URL) + audioPath);
    if (audio.GET() == 200) playWavStream(audio);
    audio.end();
  }
  acceptingFrame = true;
}

void setup() {
  Serial.begin(115200);
  pinMode(TRIG_L, OUTPUT); pinMode(TRIG_C, OUTPUT); pinMode(TRIG_R, OUTPUT);
  pinMode(ECHO_L, INPUT);  pinMode(ECHO_C, INPUT);  pinMode(ECHO_R, INPUT);
  pinMode(BUTTON, INPUT_PULLUP);
  LittleFS.begin(true);
  initI2S();

  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.println();
  Serial.println("Connecting to WiFi...");
  uint32_t started = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - started < WIFI_CONNECT_TIMEOUT_MS) { delay(300); Serial.print('.'); }
  Serial.println();
  Serial.println("=====================================");
  Serial.printf ("  MAIN MAC: %s\n", WiFi.macAddress().c_str());
  Serial.println("  ^^ COPY THIS INTO THE CAMERA SKETCH");
  Serial.println("=====================================");
  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("Wi-Fi channel: %d\n", WiFi.channel());
  } else {
    Serial.println("Wi-Fi not connected. Ultrasonic fallback remains available.");
  }
  if (esp_now_init() != ESP_OK) { Serial.println("ESP-NOW init failed"); return; }
  esp_now_register_recv_cb(onData);
  Serial.println("Ready. Press the button to test the sensors.");
}

void loop() {
  if (digitalRead(BUTTON) == LOW) {
    const char *rule = quickRule();
    Serial.println(rule);
    if (strstr(rule, "Move left"))            playFile("/left.wav");
    else if (strstr(rule, "Move right"))      playFile("/right.wav");
    else if (strstr(rule, "carefully"))       playFile("/careful.wav");
    else                                      playFile("/clear.wav");
    delay(500);
  }
  if (expectedCount && !imageReady && millis() - lastPacketAt > FRAME_STALL_TIMEOUT_MS) {
    Serial.println("Abandoning stalled frame");
    expectedCount = receivedCount = imageLen = 0;
    memset(received, 0, sizeof(received));
  }
  if (imageReady) { imageReady = false; uploadAndPlay(); }
}
