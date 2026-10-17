# BlindSight Main ESP32 one-click installer/uploader (no-terminal path)
#
# Run: Right-click -> Run with PowerShell, or:
#   powershell -ExecutionPolicy Bypass -File .\Install_BlindSight_Main_ESP32.ps1
#
# The embedded sketch must stay behaviourally identical to
#   firmware/esp32_main/src/main.cpp   and
#   firmware/esp32_main/BlindSight_Main_ESP32.ino
# See firmware/FIRMWARE_VARIANTS.md.
#
# CHANGES FROM THE PREVIOUS VERSION OF THIS SCRIPT
#   * Every arduino-cli call is checked: the old script printed
#     "SUCCESS: Main ESP32 firmware uploaded." even when the compile failed,
#     because $ErrorActionPreference='Stop' does not catch native exit codes.
#   * The unused "arduino-cli lib install ArduinoJson" is gone; the sketch
#     never included it.
#   * The generated sketch (which contains your Wi-Fi password in plaintext)
#     is offered for deletion at the end instead of being left on the Desktop.
#   * The embedded sketch gained the fixes the other two firmware copies have:
#     bounded Wi-Fi wait, /audio/<id> from audio_url, MAC filtering, 60 s HTTP
#     timeout, stalled-frame recovery, and the channels==0 guard.
$ErrorActionPreference = 'Stop'
$Port = 'COM4'
$BoardUrl = 'https://espressif.github.io/arduino-esp32/package_esp32_index.json'

function Invoke-Cli {
  param([string[]]$Arguments)
  & $cliPath @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "arduino-cli failed (exit $LASTEXITCODE): arduino-cli $($Arguments -join ' ')"
  }
}

Write-Host "BlindSight Main ESP32 uploader" -ForegroundColor Cyan
Write-Host "This will install Arduino CLI + ESP32 board support, compile, and upload to $Port." -ForegroundColor Yellow
$ssid = Read-Host 'Enter your Wi-Fi / hotspot name (SSID)'
$passwordSecure = Read-Host 'Enter your Wi-Fi password' -AsSecureString
$password = [System.Net.NetworkCredential]::new('', $passwordSecure).Password
$piIp = Read-Host 'Enter Raspberry Pi IP address (example: 192.168.4.1)'
$camMac = Read-Host 'Enter the CAM MAC from the camera serial monitor, comma separated hex (example: 3C,71,BF,12,34,56)'
if ([string]::IsNullOrWhiteSpace($ssid) -or [string]::IsNullOrWhiteSpace($password) -or [string]::IsNullOrWhiteSpace($piIp) -or [string]::IsNullOrWhiteSpace($camMac)) {
  throw 'SSID, password, Pi IP and camera MAC are all required.'
}
$macBytes = ($camMac -split '[,\s:]+') | Where-Object { $_ -ne '' }
if ($macBytes.Count -ne 6) { throw 'Camera MAC must be exactly six hex bytes.' }
$macLiteral = '{' + (($macBytes | ForEach-Object { '0x' + $_.PadLeft(2, '0') }) -join ',') + '}'

# Find Arduino CLI; install it via winget if it is not already available.
$cliCmd = Get-Command arduino-cli -ErrorAction SilentlyContinue
if (-not $cliCmd) {
  Write-Host 'Installing Arduino CLI using winget. Approve prompts if Windows asks.' -ForegroundColor Yellow
  winget install --id ArduinoSA.CLI --exact --accept-package-agreements --accept-source-agreements
  if ($LASTEXITCODE -ne 0) { throw "winget install failed (exit $LASTEXITCODE)." }
  $possible = @(
    "$env:LOCALAPPDATA\Programs\Arduino CLI\arduino-cli.exe",
    "$env:ProgramFiles\Arduino CLI\arduino-cli.exe",
    "$env:USERPROFILE\AppData\Local\Microsoft\WinGet\Packages"
  )
  $cliPath = $null
  foreach ($p in $possible[0..1]) { if (Test-Path $p) { $cliPath = $p; break } }
  if (-not $cliPath -and (Test-Path $possible[2])) {
    $cliPath = (Get-ChildItem $possible[2] -Filter arduino-cli.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
  }
  if (-not $cliPath) { $cliCmd = Get-Command arduino-cli -ErrorAction SilentlyContinue; if ($cliCmd) { $cliPath = $cliCmd.Source } }
  if (-not $cliPath) { throw 'Arduino CLI was installed but was not found. Close PowerShell, reopen it, then run this file again.' }
} else { $cliPath = $cliCmd.Source }
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
uint8_t CAMERA_MAC[6] = __CAM_MAC__; // printed by the camera sketch
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

constexpr uint32_t CLIP_SAMPLE_RATE = 22050;
constexpr uint32_t WIFI_CONNECT_TIMEOUT_MS = 20000;
constexpr int HTTP_TIMEOUT_MS = 60000;
constexpr uint32_t FRAME_STALL_TIMEOUT_MS = 3000;
constexpr uint16_t MAGIC = 0xB517;
constexpr size_t CHUNK = 220;
constexpr size_t MAX_JPEG = 66000;

struct __attribute__((packed)) ImagePacket { uint16_t magic,imageId,index,count,bytes,checksum; uint8_t data[CHUNK]; };
uint8_t imageBuf[MAX_JPEG];
bool received[300];
volatile bool imageReady = false;
bool acceptingFrame = true;
uint16_t activeId = 0, expectedCount = 0, receivedCount = 0;
size_t imageLen = 0;
uint32_t lastPacketAt = 0;

uint16_t sum16(const uint8_t *d,size_t n){uint32_t s=0;for(size_t i=0;i<n;i++)s+=d[i];return s&0xffff;}
bool isCamera(const uint8_t *mac) { return mac && memcmp(mac, CAMERA_MAC, 6) == 0; }

void handlePacket(const uint8_t *srcMac, const uint8_t *data, int len) {
  if (!acceptingFrame || !isCamera(srcMac)) return;
  if (len != (int)sizeof(ImagePacket)) return;
  const ImagePacket *p = (const ImagePacket *)data;
  if (p->magic != MAGIC || p->count == 0 || p->count > 300 ||
      p->index >= p->count || p->bytes > CHUNK ||
      p->checksum != sum16(p->data, p->bytes)) return;
  if (p->imageId != activeId) { activeId = p->imageId; expectedCount = p->count; receivedCount = 0; imageLen = 0; memset(received, 0, sizeof(received)); }
  size_t off = p->index * CHUNK;
  if (off + p->bytes > MAX_JPEG || p->count != expectedCount) return;
  if (!received[p->index]) { memcpy(imageBuf + off, p->data, p->bytes); received[p->index] = true; receivedCount++; if (p->index == p->count - 1) imageLen = off + p->bytes; }
  lastPacketAt = millis();
  if (receivedCount == expectedCount && imageLen > 0) imageReady = true;
}

#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3
void onData(const esp_now_recv_info_t *info, const uint8_t *data, int len) { handlePacket(info->src_addr, data, len); }
#else
void onData(const uint8_t *mac, const uint8_t *data, int len) { handlePacket(mac, data, len); }
#endif

void initI2S(){
  i2s_config_t cfg={.mode=(i2s_mode_t)(I2S_MODE_MASTER|I2S_MODE_TX),.sample_rate=CLIP_SAMPLE_RATE,.bits_per_sample=I2S_BITS_PER_SAMPLE_16BIT,.channel_format=I2S_CHANNEL_FMT_RIGHT_LEFT,.communication_format=I2S_COMM_FORMAT_STAND_I2S,.intr_alloc_flags=0,.dma_buf_count=8,.dma_buf_len=256,.use_apll=false,.tx_desc_auto_clear=true,.fixed_mclk=0};
  i2s_pin_config_t pins={.bck_io_num=I2S_BCK,.ws_io_num=I2S_WS,.data_out_num=I2S_DIN,.data_in_num=I2S_PIN_NO_CHANGE};
  if (i2s_driver_install(I2S_NUM_0,&cfg,0,nullptr)!=ESP_OK){Serial.println("I2S driver install failed");}
  i2s_set_pin(I2S_NUM_0,&pins);
}

void playWavStream(HTTPClient &http){
  WiFiClient *s=http.getStreamPtr(); uint8_t h[44];
  if(s->readBytes(h,44)!=44 || memcmp(h,"RIFF",4) || memcmp(h+8,"WAVE",4)){Serial.println("Bad WAV header");return;}
  uint32_t rate; uint16_t bits, channels;
  memcpy(&rate,h+24,4); memcpy(&bits,h+34,2); memcpy(&channels,h+22,2);
  if(bits!=16){Serial.println("Need 16-bit WAV");return;}
  if(channels<1)channels=1; if(channels>2)channels=2;
  i2s_set_sample_rates(I2S_NUM_0,rate);
  uint8_t in[512]; int16_t out[512];
  while(http.connected() || s->available()) {
    int n=s->readBytes(in,sizeof(in)); if(n<=0) break;
    int samples=n/2; int outn=0;
    for(int i=0;i<samples;i+=channels){
      int16_t v; memcpy(&v,in+i*2,2);
      if(outn+2>(int)(sizeof(out)/sizeof(out[0])))break;
      out[outn++]=v; out[outn++]=v;
    }
    size_t done; i2s_write(I2S_NUM_0,out,outn*2,&done,portMAX_DELAY);
  }
  i2s_set_sample_rates(I2S_NUM_0,CLIP_SAMPLE_RATE);
}

void playFile(const char *path){
  File f=LittleFS.open(path,"r"); if(!f)return;
  uint8_t h[44]; if(f.read(h,44)!=44){f.close();return;}
  i2s_set_sample_rates(I2S_NUM_0,CLIP_SAMPLE_RATE);
  uint8_t b[512];
  while(f.available()){int n=f.read(b,sizeof(b)); if(n<=0)break; size_t w; i2s_write(I2S_NUM_0,b,n,&w,portMAX_DELAY);}
  f.close();
}

float distanceCM(int t,int e){digitalWrite(t,LOW);delayMicroseconds(3);digitalWrite(t,HIGH);delayMicroseconds(10);digitalWrite(t,LOW);long d=pulseIn(e,HIGH,30000);return d?d*0.0343/2:999;}
const char* quickRule(){
  float l=distanceCM(TRIG_L,ECHO_L);delay(55);
  float c=distanceCM(TRIG_C,ECHO_C);delay(55);
  float r=distanceCM(TRIG_R,ECHO_R);
  Serial.printf("L=%.0f C=%.0f R=%.0f cm\n",l,c,r);
  // Audited band: inside 25 cm steering is wrong advice; stop instead.
  if(c<25)return "Obstacle critical. Stop.";
  if(c<45)return l>r?"Obstacle ahead. Move left.":"Obstacle ahead. Move right.";
  if(l<45)return "Obstacle on left. Move right.";
  if(r<45)return "Obstacle on right. Move left.";
  if(c<100)return "Object ahead. Proceed carefully.";
  return "Path clear.";
}

String audioPathFromReply(const String &reply){
  const char *key="\"audio_url\":\"";
  int start=reply.indexOf(key); if(start<0)return String();
  start+=strlen(key);
  int end=reply.indexOf('"',start);
  return end>start?reply.substring(start,end):String();
}

void uploadAndPlay(){
  acceptingFrame=false;
  Serial.printf("Uploading %u bytes\n",(unsigned)imageLen);
  playFile("/analysing.wav");
  HTTPClient http; http.setTimeout(HTTP_TIMEOUT_MS);
  http.begin(String(PI_URL)+"/analyse");
  String boundary="BlindSightBoundary";
  http.addHeader("Content-Type","multipart/form-data; boundary="+boundary);
  String pre="--"+boundary+"\r\nContent-Disposition: form-data; name=\"image\"; filename=\"image.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n";
  String post="\r\n--"+boundary+"--\r\n";
  size_t total=pre.length()+imageLen+post.length();
  uint8_t *body=(uint8_t*)malloc(total);
  if(!body){Serial.println("Out of memory building multipart body");http.end();acceptingFrame=true;return;}
  memcpy(body,pre.c_str(),pre.length());memcpy(body+pre.length(),imageBuf,imageLen);memcpy(body+pre.length()+imageLen,post.c_str(),post.length());
  int code=http.POST(body,total); free(body);
  String reply=http.getString(); http.end();
  Serial.printf("AI %d: %s\n",code,reply.c_str());
  String audioPath=(code==200)?audioPathFromReply(reply):String();
  if(audioPath.length()){
    HTTPClient audio; audio.setTimeout(HTTP_TIMEOUT_MS);
    audio.begin(String(PI_URL)+audioPath);
    if(audio.GET()==200)playWavStream(audio);
    audio.end();
  }
  acceptingFrame=true;
}

void setup(){
  Serial.begin(115200);
  pinMode(TRIG_L,OUTPUT);pinMode(TRIG_C,OUTPUT);pinMode(TRIG_R,OUTPUT);
  pinMode(ECHO_L,INPUT);pinMode(ECHO_C,INPUT);pinMode(ECHO_R,INPUT);
  pinMode(BUTTON,INPUT_PULLUP);
  LittleFS.begin(true); initI2S();
  WiFi.mode(WIFI_STA); WiFi.setSleep(false); WiFi.begin(WIFI_SSID,WIFI_PASSWORD);
  Serial.println(); Serial.println("Connecting to WiFi...");
  uint32_t started=millis();
  while(WiFi.status()!=WL_CONNECTED && millis()-started<WIFI_CONNECT_TIMEOUT_MS){delay(300);Serial.print('.');}
  Serial.println();
  Serial.printf("\nMAIN MAC: %s\n",WiFi.macAddress().c_str());
  if(WiFi.status()==WL_CONNECTED){Serial.printf("Wi-Fi channel: %d\n",WiFi.channel());}
  else{Serial.println("Wi-Fi not connected. Ultrasonic fallback remains available.");}
  if(esp_now_init()!=ESP_OK){Serial.println("ESP-NOW init failed");return;}
  esp_now_register_recv_cb(onData);
  Serial.println("Ready. Press the button to test the sensors.");
}

void loop(){
  if(digitalRead(BUTTON)==LOW){
    const char *rule=quickRule(); Serial.println(rule);
    if(strstr(rule,"Stop"))playFile("/stop.wav");
    else if(strstr(rule,"Move left"))playFile("/left.wav");
    else if(strstr(rule,"Move right"))playFile("/right.wav");
    else if(strstr(rule,"carefully"))playFile("/careful.wav");
    else playFile("/clear.wav");
    delay(500);
  }
  if(expectedCount && !imageReady && millis()-lastPacketAt>FRAME_STALL_TIMEOUT_MS){
    Serial.println("Abandoning stalled frame");
    expectedCount=receivedCount=imageLen=0; memset(received,0,sizeof(received));
  }
  if(imageReady){imageReady=false;uploadAndPlay();}
}
'@

# Escape values for C++ strings, then inject them into sketch placeholders.
$ssidC = $ssid.Replace('\','\\').Replace('"','\"')
$passC = $password.Replace('\','\\').Replace('"','\"')
$piC = ('http://' + $piIp + ':5000').Replace('\','\\').Replace('"','\"')
$code = $code.Replace('__SSID__',$ssidC).Replace('__PASSWORD__',$passC).Replace('__PI_URL__',$piC).Replace('__CAM_MAC__',$macLiteral)
Set-Content -Path $ino -Value $code -Encoding UTF8

Write-Host 'Installing ESP32 board package (first time can take several minutes)...' -ForegroundColor Yellow
Invoke-Cli @('core', 'update-index', '--additional-urls', $BoardUrl)
Invoke-Cli @('core', 'install', 'esp32:esp32', '--additional-urls', $BoardUrl)

Write-Host 'Compiling...' -ForegroundColor Yellow
Invoke-Cli @('compile', '--fqbn', 'esp32:esp32:esp32', $work)
Write-Host "Uploading to $Port..." -ForegroundColor Yellow
Invoke-Cli @('upload', '-p', $Port, '--fqbn', 'esp32:esp32:esp32', $work)

Write-Host ''
Write-Host 'SUCCESS: Main ESP32 firmware uploaded.' -ForegroundColor Green
Write-Host 'Open a serial monitor at 115200 baud, reset the ESP32, and copy the printed MAIN MAC into the ESP32-CAM sketch.' -ForegroundColor Cyan
Write-Host 'Note: this uploads firmware only. Upload the LittleFS WAV voice clips later if you want local analysing/fallback speech.' -ForegroundColor Yellow

# The generated sketch embeds your Wi-Fi password in plaintext. Offer to remove it.
$cleanup = Read-Host 'Delete the generated sketch (contains your Wi-Fi password)? [y/N]'
if ($cleanup -match '^[yY]') {
  Remove-Item -LiteralPath $ino -Force -ErrorAction SilentlyContinue
  Write-Host "Deleted $ino" -ForegroundColor Green
} else {
  Write-Host "Kept $ino - it contains your Wi-Fi password in plaintext. Delete it when done." -ForegroundColor Yellow
}
Read-Host 'Press Enter to close'
