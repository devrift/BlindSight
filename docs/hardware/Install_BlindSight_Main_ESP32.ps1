# BlindSight Main ESP32 one-click installer/uploader
# Run: Right-click -> Run with PowerShell, or open PowerShell and run:
# powershell -ExecutionPolicy Bypass -File .\Install_BlindSight_Main_ESP32.ps1
$ErrorActionPreference = 'Stop'
$Port = 'COM4'
$BoardUrl = 'https://espressif.github.io/arduino-esp32/package_esp32_index.json'

Write-Host "BlindSight Main ESP32 uploader" -ForegroundColor Cyan
Write-Host "This will install Arduino CLI, ESP32 board support, compile, and upload to $Port." -ForegroundColor Yellow
$ssid = Read-Host 'Enter your Wi-Fi / hotspot name (SSID)'
$passwordSecure = Read-Host 'Enter your Wi-Fi password' -AsSecureString
$password = [System.Net.NetworkCredential]::new('', $passwordSecure).Password
$piIp = Read-Host 'Enter Raspberry Pi IP address (example: 192.168.4.1)'
if ([string]::IsNullOrWhiteSpace($ssid) -or [string]::IsNullOrWhiteSpace($password) -or [string]::IsNullOrWhiteSpace($piIp)) {
    throw 'SSID, password, and Pi IP are all required.'
}

# Find Arduino CLI; install it via winget if it is not already available.
$cliCmd = Get-Command arduino-cli -ErrorAction SilentlyContinue
if (-not $cliCmd) {
  Write-Host 'Installing Arduino CLI using winget. Approve prompts if Windows asks.' -ForegroundColor Yellow
  winget install --id ArduinoSA.CLI --exact --accept-package-agreements --accept-source-agreements
  $possible = @(
    "$env:LOCALAPPDATA\Programs\Arduino CLI\arduino-cli.exe",
    "$env:ProgramFiles\Arduino CLI\arduino-cli.exe",
    "$env:USERPROFILE\AppData\Local\Microsoft\WinGet\Packages"
  )
  $cliPath = $null
  foreach ($p in $possible[0..1]) { if (Test-Path $p) { $cliPath=$p; break } }
  if (-not $cliPath -and (Test-Path $possible[2])) {
    $cliPath=(Get-ChildItem $possible[2] -Filter arduino-cli.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
  }
  if (-not $cliPath) {
    $cliCmd = Get-Command arduino-cli -ErrorAction SilentlyContinue
    if ($cliCmd) { $cliPath=$cliCmd.Source }
  }
  if (-not $cliPath) {
    throw 'Arduino CLI was installed but was not found. Close PowerShell, reopen it, then run this file again.'
  }
} else {
  $cliPath = $cliCmd.Source
}
Write-Host "Using: $cliPath" -ForegroundColor Green

$work = Join-Path ([System.IO.Path]::GetTempPath()) ("BlindSight_Main_ESP32_" + [System.Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $work | Out-Null
$ino = Join-Path $work 'BlindSight_Main_ESP32.ino'

$code = @'
#include <Arduino.h>
#include <WiFi.h>
#include <esp_now.h>
#include <HTTPClient.h>
#include <LittleFS.h>
#include <driver/i2s.h>

// ===== CHANGE THESE =====
const char *WIFI_SSID = "__SSID__";
const char *WIFI_PASSWORD = "__PASSWORD__";
const char *PI_URL = "__PI_URL__";
uint8_t CAMERA_MAC[6] = {0xAA,0xBB,0xCC,0xDD,0xEE,0xFF}; // printed by camera
// ========================

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

constexpr uint16_t MAGIC = 0xB517;
constexpr size_t CHUNK = 220;
constexpr size_t MAX_JPEG = 66000;

struct __attribute__((packed)) ImagePacket {
  uint16_t magic, imageId, index, count, bytes, checksum;
  uint8_t data[CHUNK];
};

uint8_t imageBuf[MAX_JPEG];
bool received[300];
volatile bool imageReady = false, acceptingFrame = true;
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

static void handlePacket(const uint8_t *data, int len) {
  if (len != (int)sizeof(ImagePacket)) return;
  auto *p = (const ImagePacket *)data;
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

#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3
void onData(const esp_now_recv_info_t *info, const uint8_t *data, int len) {
  if (!acceptingFrame || !info || !isCamera(info->src_addr)) return;
  handlePacket(data, len);
}
#else
void onData(const uint8_t *mac, const uint8_t *data, int len) {
  if (!acceptingFrame || !isCamera(mac)) return;
  handlePacket(data, len);
}
#endif

void initI2S() {
  i2s_config_t cfg = {
    .mode = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_TX),
    .sample_rate = 22050,
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
  i2s_driver_install(I2S_NUM_0, &cfg, 0, nullptr);
  i2s_set_pin(I2S_NUM_0, &pins);
}

void playWavStream(HTTPClient &http) {
  WiFiClient *s = http.getStreamPtr();
  uint8_t h[44];
  if (s->readBytes(h, 44) != 44 || memcmp(h, "RIFF", 4) || memcmp(h + 8, "WAVE", 4)) {
    Serial.println("Bad WAV"); return;
  }
  uint32_t rate; memcpy(&rate, h + 24, 4);
  uint16_t bits, channels; memcpy(&bits, h + 34, 2); memcpy(&channels, h + 22, 2);
  if (bits != 16) { Serial.println("Need 16-bit WAV"); return; }
  if (channels < 1) channels = 1;
  i2s_set_sample_rates(I2S_NUM_0, rate);
  uint8_t in[512]; int16_t out[512];
  while (http.connected() || s->available()) {
    int n = s->readBytes(in, sizeof(in));
    if (n <= 0) break;
    int samples = n / 2, outn = 0;
    for (int i = 0; i < samples; i += channels) {
      int16_t v; memcpy(&v, in + i * 2, 2);
      out[outn++] = v; out[outn++] = v;
    }
    size_t done; i2s_write(I2S_NUM_0, out, outn * 2, &done, portMAX_DELAY);
  }
}

void playFile(const char *path) {
  File f = LittleFS.open(path, "r");
  if (!f) return;
  uint8_t h[44];
  if (f.read(h, 44) != 44) { f.close(); return; }
  uint8_t b[512];
  while (f.available()) {
    int n = f.read(b, sizeof(b));
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
  if (c < 25) return "Obstacle critical. Stop.";
  if (c < 45) return l > r ? "Obstacle ahead. Move left." : "Obstacle ahead. Move right.";
  if (l < 45) return "Obstacle on left. Move right.";
  if (r < 45) return "Obstacle on right. Move left.";
  if (c < 100) return "Object ahead. Proceed carefully.";
  return "Path clear.";
}

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
  http.setTimeout(60000);
  http.begin(String(PI_URL) + "/analyse");
  String boundary = "BlindSightBoundary";
  http.addHeader("Content-Type", "multipart/form-data; boundary=" + boundary);
  String pre = "--" + boundary +
    "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"image.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n";
  String post = "\r\n--" + boundary + "--\r\n";
  size_t total = pre.length() + imageLen + post.length();
  uint8_t *body = (uint8_t *)malloc(total);
  if (!body) { http.end(); acceptingFrame = true; return; }
  memcpy(body, pre.c_str(), pre.length());
  memcpy(body + pre.length(), imageBuf, imageLen);
  memcpy(body + pre.length() + imageLen, post.c_str(), post.length());
  int code = http.POST(body, total);
  free(body);
  String reply = http.getString();
  http.end();
  Serial.printf("AI %d: %s\n", code, reply.c_str());

  if (code == 200) {
    String audioPath = audioPathFromReply(reply);
    if (audioPath.length()) {
      HTTPClient audio;
      audio.setTimeout(60000);
      audio.begin(String(PI_URL) + audioPath);
      if (audio.GET() == 200) playWavStream(audio);
      audio.end();
    }
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
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  int wifiAttempts = 0;
  while (WiFi.status() != WL_CONNECTED && wifiAttempts < 66) {
    delay(300);
    Serial.print('.');
    wifiAttempts++;
  }
  WiFi.setSleep(false);
  Serial.printf("\nMAIN MAC: %s\n", WiFi.macAddress().c_str());
  if (esp_now_init() != ESP_OK) {
    Serial.println("ESP-NOW init failed");
    return;
  }
  esp_now_register_recv_cb(onData);
}

void loop() {
  if (digitalRead(BUTTON) == LOW) {
    const char *rule = quickRule();
    Serial.println(rule);
    if (strstr(rule, "Move left"))            playFile("/left.wav");
    else if (strstr(rule, "Move right"))      playFile("/right.wav");
    else if (strstr(rule, "Stop"))            playFile("/stop.wav");
    else if (strstr(rule, "carefully"))       playFile("/careful.wav");
    else                                      playFile("/clear.wav");
    delay(500);
  }
  if (expectedCount && !imageReady && millis() - lastPacketAt > 3000) {
    expectedCount = receivedCount = imageLen = 0;
    memset(received, 0, sizeof(received));
  }
  if (imageReady) {
    imageReady = false;
    uploadAndPlay();
  }
}
'@

# Escape values for C++ strings, then inject them into sketch placeholders.
$ssidC = $ssid.Replace('\','\\').Replace('"','\"')
$passC = $password.Replace('\','\\').Replace('"','\"')
$piC = ('http://' + $piIp + ':5000').Replace('\','\\').Replace('"','\"')
$code = $code.Replace('__SSID__',$ssidC).Replace('__PASSWORD__',$passC).Replace('__PI_URL__',$piC)
Set-Content -Path $ino -Value $code -Encoding UTF8

try {
  Write-Host 'Installing ESP32 board package (first time can take several minutes)...' -ForegroundColor Yellow
  & $cliPath core update-index --additional-urls $BoardUrl
  & $cliPath core install esp32:esp32 --additional-urls $BoardUrl

  Write-Host 'Compiling...' -ForegroundColor Yellow
  & $cliPath compile --fqbn esp32:esp32:esp32 $work
  if ($LASTEXITCODE -ne 0) {
    throw "Compilation failed with exit code $LASTEXITCODE."
  }

  Write-Host "Uploading to $Port..." -ForegroundColor Yellow
  & $cliPath upload -p $Port --fqbn esp32:esp32:esp32 $work
  if ($LASTEXITCODE -ne 0) {
    throw "Upload failed with exit code $LASTEXITCODE."
  }

  Write-Host ''
  Write-Host 'SUCCESS: Main ESP32 firmware uploaded.' -ForegroundColor Green
  Write-Host 'Open Arduino IDE Serial Monitor at 115200 baud, reset the ESP32, and copy the printed MAIN MAC into the ESP32-CAM sketch.' -ForegroundColor Cyan
} finally {
  # Clean up temporary build files containing plaintext credentials
  if (Test-Path $work) {
    Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
  }
}

Read-Host 'Press Enter to close'
