// BlindSight - MAIN ESP32 DevKit V1 (PlatformIO entry point)
//
// This file is one of three copies of the main firmware that must stay
// behaviourally identical. See firmware/FIRMWARE_VARIANTS.md:
//   1. firmware/esp32_main/src/main.cpp                 <- this file (PlatformIO)
//   2. firmware/esp32_main/BlindSight_Main_ESP32.ino    (Arduino IDE)
//   3. the sketch embedded in docs/hardware/Install_BlindSight_Main_ESP32.ps1
//
// Hardware: 3x HC-SR04 ultrasonic, PCM5102 I2S DAC, tactile button, ESP-NOW
// receiver for JPEG chunks from the ESP32-CAM. See README.md pin matrix.

#include <Arduino.h>
#include <WiFi.h>
#include <esp_now.h>
#include <HTTPClient.h>
#include <LittleFS.h>
#include <driver/i2s.h>

#if __has_include("secrets.h")
#include "secrets.h"
#else
#error "Copy include/secrets.example.h to include/secrets.h and configure the device."
#endif

const char *WIFI_SSID = BLINDSIGHT_WIFI_SSID;
const char *WIFI_PASSWORD = BLINDSIGHT_WIFI_PASSWORD;
const String PI_BASE_URL = String("http://") + BLINDSIGHT_PI_HOST + ":" + BLINDSIGHT_PI_PORT;
const uint8_t CAMERA_MAC[6] = BLINDSIGHT_CAMERA_MAC;

#define TRIG_L 13
#define TRIG_C 14
#define TRIG_R 27
#define ECHO_L 34
#define ECHO_C 35
#define ECHO_R 39
#define BUTTON 32
#define I2S_BCK 26
#define I2S_WS 25
#define I2S_DIN 22

// The LittleFS voice clips are authored at this rate (see
// firmware/esp32_main/data/generate_clips.sh), and playFile() re-asserts it so a
// streamed network response at a different rate cannot leave the DAC mis-
// clocked for the next local clip.
constexpr uint32_t CLIP_SAMPLE_RATE = 22050;

// Bounded Wi-Fi association. The old code spun forever here, which meant the
// button and the HC-SR04 fallback never ran without a hotspot - the exact
// opposite of the offline-safety story in the README.
constexpr uint32_t WIFI_CONNECT_TIMEOUT_MS = 20000;

// The gateway may spend up to ~25s on inference plus ~20s on TTS, so the HTTP
// client timeout must clear that with margin. The 5s HTTPClient default timed
// out on every real request.
constexpr int HTTP_TIMEOUT_MS = 60000;

// A frame whose chunks stop arriving is abandoned after this long; otherwise a
// lost final chunk left expectedCount/received[] dirty and a camera reboot
// (which restarts imageId at 1) uploaded a corrupt mixed JPEG as valid.
constexpr uint32_t FRAME_STALL_TIMEOUT_MS = 3000;

constexpr uint16_t MAGIC = 0xB517;
constexpr size_t CHUNK = 220;
constexpr size_t MAX_JPEG = 66000;

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
  uint32_t s = 0;
  for (size_t i = 0; i < n; i++) s += d[i];
  return s & 0xffff;
}

bool isCamera(const uint8_t *mac) {
  return mac && memcmp(mac, CAMERA_MAC, 6) == 0;
}

void handlePacket(const uint8_t *srcMac, const uint8_t *data, int len) {
  if (!acceptingFrame || !isCamera(srcMac)) return;
  if (len != (int)sizeof(ImagePacket)) return;
  const ImagePacket *p = (const ImagePacket *)data;
  if (p->magic != MAGIC || p->count == 0 || p->count > 300 ||
      p->index >= p->count || p->bytes > CHUNK ||
      p->checksum != sum16(p->data, p->bytes)) return;
  if (p->imageId != activeId) {
    activeId = p->imageId;
    expectedCount = p->count;
    receivedCount = 0;
    imageLen = 0;
    memset(received, 0, sizeof(received));
  }
  size_t off = p->index * CHUNK;
  if (off + p->bytes > MAX_JPEG || p->count != expectedCount) return;
  if (!received[p->index]) {
    memcpy(imageBuf + off, p->data, p->bytes);
    received[p->index] = true;
    receivedCount++;
    if (p->index == p->count - 1) imageLen = off + p->bytes;
  }
  lastPacketAt = millis();
  if (receivedCount == expectedCount && imageLen > 0) imageReady = true;
}

// The ESP-NOW receive callback signature changed between Arduino core 2.x and
// 3.x (IDF 4.4 vs IDF 5.x). Without this guard the file only compiles on one
// of the two, and platformio.ini used to leave that to chance.
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
    .fixed_mclk = 0,
  };
  i2s_pin_config_t pins = {
    .bck_io_num = I2S_BCK,
    .ws_io_num = I2S_WS,
    .data_out_num = I2S_DIN,
    .data_in_num = I2S_PIN_NO_CHANGE,
  };
  if (i2s_driver_install(I2S_NUM_0, &cfg, 0, nullptr) != ESP_OK) {
    Serial.println("I2S driver install failed");
  }
  i2s_set_pin(I2S_NUM_0, &pins);
}

// Stream a 16-bit PCM WAV response from the gateway straight to the DAC.
void playWavStream(HTTPClient &http) {
  WiFiClient *s = http.getStreamPtr();
  uint8_t h[44];
  if (s->readBytes(h, 44) != 44 || memcmp(h, "RIFF", 4) || memcmp(h + 8, "WAVE", 4)) {
    Serial.println("Bad WAV header");
    return;
  }
  // memcpy, not a cast: the header is a byte buffer, and a 32-bit read through
  // a mis-typed pointer is strict-aliasing UB and an unaligned access on Xtensa.
  uint32_t rate;
  uint16_t bits, channels;
  memcpy(&rate, h + 24, 4);
  memcpy(&bits, h + 34, 2);
  memcpy(&channels, h + 22, 2);
  if (bits != 16) {
    Serial.println("Need 16-bit WAV");
    return;
  }
  // A zero/absurd channel count would make the decode loop below spin forever
  // while writing past the end of `out`. The WAV header arrives off the network.
  if (channels < 1) channels = 1;
  if (channels > 2) channels = 2;
  i2s_set_sample_rates(I2S_NUM_0, rate);

  uint8_t in[512];
  int16_t out[512];
  while (http.connected() || s->available()) {
    int n = s->readBytes(in, sizeof(in));
    if (n <= 0) break;
    int samples = n / 2;
    int outn = 0;
    for (int i = 0; i < samples; i += channels) {
      int16_t v;
      memcpy(&v, in + i * 2, 2);
      if (outn + 2 > (int)(sizeof(out) / sizeof(out[0]))) break;
      out[outn++] = v;
      out[outn++] = v;
    }
    size_t done;
    i2s_write(I2S_NUM_0, out, outn * 2, &done, portMAX_DELAY);
  }
  // Leave the DAC clocked for the local clips again.
  i2s_set_sample_rates(I2S_NUM_0, CLIP_SAMPLE_RATE);
}

// Play a LittleFS clip. Missing files are not an error: the clips are uploaded
// separately (PlatformIO: Upload Filesystem Image) and the sketch must still
// run before they exist.
void playFile(const char *path) {
  File f = LittleFS.open(path, "r");
  if (!f) return;
  uint8_t h[44];
  if (f.read(h, 44) != 44) {
    f.close();
    return;
  }
  i2s_set_sample_rates(I2S_NUM_0, CLIP_SAMPLE_RATE);
  uint8_t b[512];
  while (f.available()) {
    int n = f.read(b, sizeof(b));
    if (n <= 0) break;
    size_t w;
    i2s_write(I2S_NUM_0, b, n, &w, portMAX_DELAY);
  }
  f.close();
}

float distanceCM(int t, int e) {
  digitalWrite(t, LOW);
  delayMicroseconds(3);
  digitalWrite(t, HIGH);
  delayMicroseconds(10);
  digitalWrite(t, LOW);
  long d = pulseIn(e, HIGH, 30000);
  return d ? d * 0.0343 / 2 : 999;
}

// Deterministic offline rule. Runs with no network at all.
const char *quickRule() {
  float l = distanceCM(TRIG_L, ECHO_L);
  delay(55);
  float c = distanceCM(TRIG_C, ECHO_C);
  delay(55);
  float r = distanceCM(TRIG_R, ECHO_R);
  Serial.printf("L=%.0f C=%.0f R=%.0f cm\n", l, c, r);
  if (c < 45) return l > r ? "Obstacle ahead. Move left." : "Obstacle ahead. Move right.";
  if (l < 45) return "Obstacle on left. Move right.";
  if (r < 45) return "Obstacle on right. Move left.";
  if (c < 100) return "Object ahead. Proceed carefully.";
  return "Path clear.";
}

// The gateway answers POST /analyse with JSON containing
// {"audio_url": "/audio/<16 hex chars>"}. Fetching the bare "/audio" path (as
// earlier revisions did) is a 404: the route needs the request id.
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
  http.begin(PI_BASE_URL + "/analyse");
  String boundary = "BlindSightBoundary";
  http.addHeader("Content-Type", "multipart/form-data; boundary=" + boundary);
  String pre = "--" + boundary +
      "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"image.jpg\""
      "\r\nContent-Type: image/jpeg\r\n\r\n";
  String post = "\r\n--" + boundary + "--\r\n";

  size_t total = pre.length() + imageLen + post.length();
  uint8_t *body = (uint8_t *)malloc(total);
  if (!body) {
    Serial.println("Out of memory building multipart body");
    http.end();
    acceptingFrame = true;
    return;
  }
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
    audio.begin(PI_BASE_URL + audioPath);
    if (audio.GET() == 200) playWavStream(audio);
    audio.end();
  }
  acceptingFrame = true;
}

void setup() {
  Serial.begin(115200);
  pinMode(TRIG_L, OUTPUT);
  pinMode(TRIG_C, OUTPUT);
  pinMode(TRIG_R, OUTPUT);
  pinMode(ECHO_L, INPUT);
  pinMode(ECHO_C, INPUT);
  pinMode(ECHO_R, INPUT);
  pinMode(BUTTON, INPUT_PULLUP);
  LittleFS.begin(true);
  initI2S();

  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);  // ESP-NOW drops frames badly with modem sleep on
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  Serial.println();
  Serial.println("Connecting to WiFi...");
  uint32_t started = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - started < WIFI_CONNECT_TIMEOUT_MS) {
    delay(300);
    Serial.print('.');
  }
  Serial.println();
  Serial.printf("MAIN MAC: %s\n", WiFi.macAddress().c_str());
  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("Wi-Fi channel: %d\n", WiFi.channel());
  } else {
    // Not fatal. The ultrasonic fallback and the button must still work; that
    // is the whole point of the local tier.
    Serial.println("Wi-Fi not connected. Ultrasonic fallback remains available.");
  }

  if (esp_now_init() != ESP_OK) {
    Serial.println("ESP-NOW init failed");
    return;
  }
  esp_now_register_recv_cb(onData);
  Serial.println("Ready. Press the button to test the sensors.");
}

void loop() {
  if (digitalRead(BUTTON) == LOW) {
    const char *rule = quickRule();
    Serial.println(rule);
    // Pre-recorded local clips keep this path independent of Pi/model latency.
    // (There is deliberately no "Stop" branch: quickRule() never returns a
    // string containing it, so it was dead code and /stop.wav never existed.)
    if (strstr(rule, "Move left")) playFile("/left.wav");
    else if (strstr(rule, "Move right")) playFile("/right.wav");
    else if (strstr(rule, "carefully")) playFile("/careful.wav");
    else playFile("/clear.wav");
    delay(500);
  }

  if (expectedCount && !imageReady && millis() - lastPacketAt > FRAME_STALL_TIMEOUT_MS) {
    Serial.println("Abandoning stalled frame");
    expectedCount = receivedCount = imageLen = 0;
    memset(received, 0, sizeof(received));
  }

  if (imageReady) {
    imageReady = false;
    uploadAndPlay();
  }
}
