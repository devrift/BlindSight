/* ============================================================
   BlindSight - MAIN ESP32 DevKit V1  (canonical firmware, v1.5.0)
   ------------------------------------------------------------
   UPLOAD THIS FIRST. It prints "MAIN MAC: xx:xx:..." in the
   Serial Monitor. You need that MAC for the camera sketch.

   Board to select in Arduino IDE:  "ESP32 Dev Module"
   Upload Speed: 115200

   v1.5 — ZERO-REFLASH EXHIBIT SETUP
   --------------------------------
   Wi-Fi SSID/password and the edge-server IP/port are NO LONGER
   hardcoded. They are configured once from a phone browser via the
   tzapu/WiFiManager captive portal "BlindSight-Setup" and persisted
   in ESP32 NVS (Preferences.h). Moving the glasses to a new venue /
   hotspot never requires reflashing.

   Required library (Arduino IDE: Sketch > Include Library > Manage Libraries):
     - WiFiManager by tzapu  (>= 2.0.17)
   PlatformIO installs it automatically via platformio.ini lib_deps.

   NOTE: Arduino IDE expects this file to live in a folder named
   BlindSight_Main_ESP32. Open this FILE via File -> Open and let the
   IDE propose moving it into its own folder when it asks.
   The PlatformIO entry point src/main.cpp simply #includes this file,
   so there is exactly ONE source of truth. See firmware/FIRMWARE_VARIANTS.md.
   ============================================================ */

#include <Arduino.h>
#include <WiFi.h>
#include <WiFiManager.h>   // tzapu/WiFiManager — captive configuration portal
#include <Preferences.h>   // ESP32 non-volatile storage for server_ip / server_port
#include <esp_now.h>
#include <HTTPClient.h>
#include <LittleFS.h>
#include <driver/i2s.h>

#define BLINDSIGHT_FIRMWARE_VERSION "1.5.0"

// ##############################################################
// Hardware pairing MAC. Wi-Fi credentials and the edge-server IP are
// configured at runtime via the BlindSight-Setup portal, NOT here.
// PlatformIO: put #define BLINDSIGHT_CAMERA_MAC {..} in include/secrets.h.
// Arduino IDE: either create a secrets.h next to this sketch or edit the
// default below. Paste the CAM MAC printed by the camera sketch.
// ##############################################################
#if __has_include("secrets.h")
#include "secrets.h"
#endif
#ifndef BLINDSIGHT_CAMERA_MAC
#define BLINDSIGHT_CAMERA_MAC {0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF}
#endif
uint8_t CAMERA_MAC[6] = BLINDSIGHT_CAMERA_MAC;

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

constexpr uint32_t CLIP_SAMPLE_RATE = 22050;
constexpr uint32_t WIFI_CONNECT_TIMEOUT_MS = 8000;    // fast saved-Wi-Fi attempt
constexpr uint32_t SERVER_PROBE_TIMEOUT_MS = 2000;    // per /health probe
constexpr int      SERVER_PROBE_RETRIES = 3;          // ~6-8 s total
constexpr uint32_t PORTAL_TIMEOUT_S = 180;            // portal auto-closes after 3 min
constexpr uint32_t BUTTON_HOLD_PORTAL_MS = 3000;      // hold reflex button 3 s -> portal
constexpr uint32_t WIFI_RETRY_INTERVAL_MS = 30000;    // background reconnect cadence
constexpr int HTTP_TIMEOUT_MS = 60000;
constexpr uint32_t FRAME_STALL_TIMEOUT_MS = 3000;
constexpr uint16_t MAGIC = 0xB517;
constexpr size_t   CHUNK = 220;
constexpr size_t   MAX_JPEG = 66000;

// ---------------- Persistent edge-server configuration ----------------
Preferences prefs;
char serverIp[16] = "192.168.1.100";   // default; overridden by NVS / portal
uint16_t serverPort = 5000;
char serverPortBuf[6] = "5000";

WiFiManager wm;
WiFiManagerParameter serverIpParam("server_ip", "Edge Server IP (e.g. 192.168.1.100)", serverIp, 15);
WiFiManagerParameter serverPortParam("server_port", "Edge Server Port (e.g. 5000)", serverPortBuf, 5);

String baseUrl() {
  return String("http://") + serverIp + ":" + String(serverPort);
}

void loadServerConfig() {
  prefs.begin("blindsight", true);  // read-only
  String ip = prefs.getString("server_ip", "192.168.1.100");
  uint16_t port = prefs.getUShort("server_port", 5000);
  prefs.end();
  ip.trim();
  IPAddress parsed;
  if (ip.length() > 0 && parsed.fromString(ip)) {
    ip.toCharArray(serverIp, sizeof(serverIp));
  }
  if (port >= 1 && port <= 65535) serverPort = port;
  snprintf(serverPortBuf, sizeof(serverPortBuf), "%u", serverPort);
  serverIpParam.setValue(serverIp, 15);
  serverPortParam.setValue(serverPortBuf, 5);
}

void saveParamsCallback() {
  // Called by WiFiManager when the user submits the portal form.
  const char *ip = serverIpParam.getValue();
  const char *portStr = serverPortParam.getValue();
  IPAddress parsed;
  String ipStr = String(ip ? ip : "");
  ipStr.trim();
  int port = atoi(portStr ? portStr : "5000");
  if (ipStr.length() == 0 || !parsed.fromString(ipStr)) {
    Serial.println("[portal] Invalid server IP; keeping previous value.");
  } else {
    ipStr.toCharArray(serverIp, sizeof(serverIp));
  }
  if (port >= 1 && port <= 65535) serverPort = (uint16_t)port;
  snprintf(serverPortBuf, sizeof(serverPortBuf), "%u", serverPort);

  prefs.begin("blindsight", false);
  prefs.putString("server_ip", serverIp);
  prefs.putUShort("server_port", serverPort);
  prefs.end();
  Serial.printf("[portal] Saved edge server %s:%u\n", serverIp, serverPort);
}

bool connectSavedWiFiFast() {
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);  // ESP-NOW drops frames badly with modem sleep on
  WiFi.begin();          // use credentials stored in NVS by WiFiManager / ESP core
  Serial.print("Connecting to saved Wi-Fi");
  uint32_t started = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - started < WIFI_CONNECT_TIMEOUT_MS) {
    delay(150);
    Serial.print('.');
  }
  Serial.println();
  return WiFi.status() == WL_CONNECTED;
}

bool edgeServerReachable() {
  for (int attempt = 0; attempt < SERVER_PROBE_RETRIES; attempt++) {
    HTTPClient http;
    http.setConnectTimeout(SERVER_PROBE_TIMEOUT_MS);
    http.setTimeout(SERVER_PROBE_TIMEOUT_MS);
    http.begin(baseUrl() + "/health");
    int code = http.GET();
    http.end();
    if (code == 200) {
      Serial.printf("[net] Edge server OK at %s (HTTP %d)\n", baseUrl().c_str(), code);
      return true;
    }
    Serial.printf("[net] Edge server probe %d/%d failed (HTTP %d)\n",
                  attempt + 1, SERVER_PROBE_RETRIES, code);
    delay(300);
  }
  return false;
}

void startConfigPortal() {
  Serial.println();
  Serial.println("=====================================================");
  Serial.println("  BlindSight-Setup captive portal");
  Serial.println("  1. On your phone, join Wi-Fi network: BlindSight-Setup");
  Serial.println("  2. Open http://192.168.4.1 if the page does not pop up");
  Serial.println("  3. Pick the venue Wi-Fi and enter the Edge Server IP");
  Serial.println("     shown in the edge server terminal, plus its port.");
  Serial.println("=====================================================");
  wm.setConfigPortalTimeout(PORTAL_TIMEOUT_S);
  bool connected = wm.startConfigPortal("BlindSight-Setup");
  if (connected) {
    Serial.println("[portal] Configuration saved. Restarting to apply...");
    delay(500);
    ESP.restart();
  }
  Serial.println("[portal] Timed out. Continuing with offline ultrasonic fallback.");
}

// ---------------- ESP-NOW camera frame receiver ----------------
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

// ---------------- Audio ----------------
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

// ---------------- Ultrasonic reflex ----------------
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

void triggerReflex() {
  const char *rule = quickRule();
  Serial.println(rule);
  if (strstr(rule, "Stop"))                 playFile("/stop.wav");
  else if (strstr(rule, "Move left"))       playFile("/left.wav");
  else if (strstr(rule, "Move right"))      playFile("/right.wav");
  else if (strstr(rule, "carefully"))       playFile("/careful.wav");
  else                                      playFile("/clear.wav");
}

// ---------------- Edge upload ----------------
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
  Serial.printf("Uploading %u bytes to %s\n", (unsigned)imageLen, baseUrl().c_str());
  playFile("/analysing.wav");

  HTTPClient http;
  http.setTimeout(HTTP_TIMEOUT_MS);
  http.begin(baseUrl() + "/analyse");
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
    audio.begin(baseUrl() + audioPath);
    if (audio.GET() == 200) playWavStream(audio);
    audio.end();
  }
  acceptingFrame = true;
}

// ---------------- Setup / loop ----------------
void setup() {
  Serial.begin(115200);
  pinMode(TRIG_L, OUTPUT); pinMode(TRIG_C, OUTPUT); pinMode(TRIG_R, OUTPUT);
  pinMode(ECHO_L, INPUT);  pinMode(ECHO_C, INPUT);  pinMode(ECHO_R, INPUT);
  pinMode(BUTTON, INPUT_PULLUP);
  LittleFS.begin(true);
  initI2S();

  Serial.println();
  Serial.printf("BlindSight main firmware v%s\n", BLINDSIGHT_FIRMWARE_VERSION);
  Serial.println("=====================================");
  Serial.printf("  MAIN MAC: %s\n", WiFi.macAddress().c_str());
  Serial.println("  ^^ COPY THIS INTO THE CAMERA SKETCH");
  Serial.println("=====================================");

  loadServerConfig();
  wm.addParameter(&serverIpParam);
  wm.addParameter(&serverPortParam);
  wm.setSaveParamsCallback(saveParamsCallback);
  wm.setConfigPortalTimeout(PORTAL_TIMEOUT_S);
  wm.setConnectTimeout(WIFI_CONNECT_TIMEOUT_MS / 1000);
  wm.setConnectRetries(2);

  // Manual portal: hold the reflex button for 3+ seconds during boot.
  bool forcePortal = false;
  if (digitalRead(BUTTON) == LOW) {
    uint32_t holdStart = millis();
    while (digitalRead(BUTTON) == LOW && millis() - holdStart < BUTTON_HOLD_PORTAL_MS) delay(20);
    forcePortal = (digitalRead(BUTTON) == LOW);
  }

  if (forcePortal) {
    Serial.println("[boot] Button held 3 s — opening setup portal.");
    startConfigPortal();
  } else {
    bool wifiOk = connectSavedWiFiFast();
    bool serverOk = wifiOk && edgeServerReachable();
    if (!wifiOk || !serverOk) {
      Serial.println(wifiOk ? "[boot] Edge server unreachable." : "[boot] Saved Wi-Fi unavailable.");
      startConfigPortal();
    }
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("Wi-Fi channel: %d\n", WiFi.channel());
    Serial.printf("Edge server: %s\n", baseUrl().c_str());
    Serial.printf("ESP IP: %s\n", WiFi.localIP().toString().c_str());
  } else {
    Serial.println("Wi-Fi not connected. Ultrasonic fallback remains available.");
  }

  if (esp_now_init() != ESP_OK) { Serial.println("ESP-NOW init failed"); return; }
  esp_now_register_recv_cb(onData);
  Serial.println("Ready. Short-press the button to test sensors; hold 3 s for setup portal.");
}

void loop() {
  // --- Button: short press = reflex, hold >= 3 s = setup portal ---
  static bool buttonWasDown = false;
  static bool portalFired = false;
  static uint32_t buttonDownAt = 0;
  bool down = (digitalRead(BUTTON) == LOW);
  if (down && !buttonWasDown) { buttonDownAt = millis(); portalFired = false; }
  if (down && !portalFired && millis() - buttonDownAt >= BUTTON_HOLD_PORTAL_MS) {
    portalFired = true;
    Serial.println("[button] Hold detected — opening setup portal.");
    startConfigPortal();  // restarts on save; otherwise returns after timeout
  }
  if (!down && buttonWasDown && !portalFired) {
    uint32_t held = millis() - buttonDownAt;
    if (held > 30 && held < BUTTON_HOLD_PORTAL_MS) triggerReflex();
  }
  buttonWasDown = down;

  // --- Background Wi-Fi reconnect (does not block the reflex path) ---
  static uint32_t lastWifiRetry = 0;
  if (WiFi.status() != WL_CONNECTED && millis() - lastWifiRetry > WIFI_RETRY_INTERVAL_MS) {
    lastWifiRetry = millis();
    Serial.println("[net] Wi-Fi dropped; attempting reconnect...");
    WiFi.begin();
  }

  if (expectedCount && !imageReady && millis() - lastPacketAt > FRAME_STALL_TIMEOUT_MS) {
    Serial.println("Abandoning stalled frame");
    expectedCount = receivedCount = imageLen = 0;
    memset(received, 0, sizeof(received));
  }
  if (imageReady) { imageReady = false; uploadAndPlay(); }
}
