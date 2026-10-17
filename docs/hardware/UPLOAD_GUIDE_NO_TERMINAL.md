# BlindSight — Click-by-Click Upload Guide (NO TERMINAL, 4GB Laptop)

> Zero PowerShell. Zero typing commands. Mouse + copy/paste only.
> Total time: ~30–40 min, most of it waiting for downloads.

---

## ⚠️ Before you start — 4GB RAM survival rules

Arduino IDE 2.x is heavy. Do this or compiling will freeze your laptop:

- [ ] **Close Chrome/Edge completely** (biggest RAM eater). Reopen after.
- [ ] Close Discord, Spotify, WhatsApp, Steam, anything in the tray.
- [ ] Plug the laptop into charger (Windows throttles CPU on battery).
- [ ] First compile takes **3–8 minutes**. It looks frozen. **It is not.** Do not close it.
- [ ] Later compiles take ~30 seconds (it caches).

If the IDE is still unbearable, see **Appendix A** at the bottom for the lighter alternative.

---

## PART 1 — Install Arduino IDE (one time, ~10 min)

1. Open your browser → go to **https://www.arduino.cc/en/software**
2. Scroll down to **"Windows"** → click **"Windows Win 10 and newer, 64 bits"**
3. It asks for a donation → click **"JUST DOWNLOAD"**
4. Open the downloaded file (bottom of browser, or Downloads folder)
5. Click **I Agree** → **Install** → wait → **Finish**
6. Arduino IDE opens. If Windows Firewall pops up → click **Allow access**
7. Now **close your browser** to free RAM.

---

## PART 2 — Add ESP32 board support (one time, ~10 min)

1. In Arduino IDE top menu: **File → Preferences**
2. Find the box labelled **"Additional boards manager URLs"**
3. Click in it and paste exactly this:

```
https://espressif.github.io/arduino-esp32/package_esp32_index.json
```

4. Click **OK**
5. On the **left sidebar**, click the **second icon down** (a little chip — "Boards Manager").
   If the sidebar is hidden: **Tools → Board → Boards Manager**
6. In the search box type: **esp32**
7. Find the entry **"esp32 by Espressif Systems"** → click **INSTALL**
8. ⏳ This downloads ~250 MB. **Go get a snack.** Do not close the IDE.
9. When it says INSTALLED, you're done with this part forever.

---

## PART 3 — Install the one library you need (2 min)

1. Left sidebar → click the **books icon** (Library Manager).
   Or: **Sketch → Include Library → Manage Libraries**
2. Search: **ArduinoJson**
3. Find **"ArduinoJson by Benoit Blanchon"** → click **INSTALL**

Everything else (WiFi, esp_now, HTTPClient, LittleFS, I2S, esp_camera) is already
built into the ESP32 core. Do not install anything else.

---

## PART 4 — Get the sketch files onto your laptop

The two sketches are in this workspace at:

```
arduino_sketches/BlindSight_Main_ESP32/BlindSight_Main_ESP32.ino
arduino_sketches/BlindSight_Camera/BlindSight_Camera.ino
```

1. Download both files from the workspace to your laptop.
2. In **File Explorer**, go to **Documents → Arduino**
   (this folder is created when the IDE first runs)
3. Create a folder named exactly **`BlindSight_Main_ESP32`**
   → put `BlindSight_Main_ESP32.ino` inside it.
4. Create a folder named exactly **`BlindSight_Camera`**
   → put `BlindSight_Camera.ino` inside it.

> 🔴 **The folder name MUST match the .ino file name.** That's an Arduino rule.
> `BlindSight_Main_ESP32/BlindSight_Main_ESP32.ino` ✅
> `Downloads/BlindSight_Main_ESP32.ino` ❌ (IDE will complain)

5. **Double-click the .ino file** → it opens in Arduino IDE. Done, no terminal.

---

## PART 5 — Install the USB driver (only if the board doesn't show up)

Skip this for now. Come back only if **Part 6 step 3** shows no COM port.

- If your ESP32 has a **CP2102** chip → download "CP210x Windows Drivers" from Silicon Labs
- If it has a **CH340/CH9102** chip → search "CH341SER driver" and install
- Look at the small square chip near the USB socket to know which one you have
- After installing: unplug, replug, restart the IDE

---

## PART 6 — Upload the MAIN ESP32 (do this one FIRST)

### 6.1 Edit the 3 settings
Open `BlindSight_Main_ESP32.ino`. Near the top, find the box marked
`EDIT THESE 3 LINES ONLY` and change:

| Line | Change it to |
|---|---|
| `WIFI_SSID` | your hotspot name (e.g. `"BlindSight"`) |
| `WIFI_PASSWORD` | your hotspot password |
| `PI_URL` | `"http://YOUR_PI_IP:5000"` |

Keep the quotes. Don't touch anything else.

> Don't know the Pi IP yet? Leave the default. You can re-upload later in 30 seconds.

### 6.2 Set the board
- **Tools → Board → esp32 → ESP32 Dev Module**
- **Tools → Upload Speed → 115200** ← important, don't use 921600
- **Tools → Port → COM4** (or whatever COM shows up)

> Not sure which COM? Unplug the board, look at the Port list, plug it back in,
> the **new** entry that appears is yours.

### 6.3 Upload
1. Click the **→ arrow button** (top-left, "Upload")
2. First time: **wait 3–8 minutes.** Bottom bar says "Compiling sketch...".
3. Then it says "Connecting........"
4. **If it sits on dots for 10+ seconds:** hold the **BOOT** button on the ESP32,
   keep holding, then release when you see "Writing at 0x..." progress
5. Success looks like: **`Hard resetting via RTS pin...`**

### 6.4 Get the MAC address (you NEED this)
1. Click the **magnifying glass icon**, top-right = **Serial Monitor**
2. In the dropdown at the bottom of that panel, set **115200 baud**
3. Press the **EN / RST** button on the ESP32
4. You'll see:

```
=====================================
  MAIN MAC: 3C:71:BF:12:34:56
  ^^ COPY THIS INTO THE CAMERA SKETCH
=====================================
```

5. **Select that MAC with your mouse and copy it.** Paste it in Notepad so you don't lose it.

> If it just prints dots forever → your hotspot name/password is wrong, or the
> hotspot is 5GHz. **ESP32 only works on 2.4GHz.** Turn off 5GHz-only mode.

---

## PART 7 — Upload the ESP32-CAM (do this SECOND)

### 7.1 Put the MAC into the sketch
Open `BlindSight_Camera.ino`. Find:

```cpp
uint8_t MAIN_ESP_MAC[6] = {0xAA,0xBB,0xCC,0xDD,0xEE,0xFF};
```

If your MAC was `3C:71:BF:12:34:56`, change it to:

```cpp
uint8_t MAIN_ESP_MAC[6] = {0x3C,0x71,0xBF,0x12,0x34,0x56};
```

Rule: each pair of characters becomes `0x` + that pair, separated by commas.
Also set the same `WIFI_SSID` and `WIFI_PASSWORD` as the main board.

### 7.2 Wire it for upload
Using your Arduino-as-TTL setup (RESET→GND on the Arduino):

| Arduino | ESP32-CAM |
|---|---|
| RESET | GND (do this first!) |
| D0 (RX) | U0R — through 1k/2k divider |
| D1 (TX) | U0T |
| GND | GND |
| 5V | 5V |
| — | **GPIO0 → GND** ← flash mode jumper |

### 7.3 Set the board
- **Tools → Board → esp32 → AI Thinker ESP32-CAM**
- **Tools → Upload Speed → 115200**
- **Tools → Port →** the Arduino's COM port

### 7.4 Upload
1. Click **→ Upload**
2. When it says "Connecting........" → **press the RST button on the ESP32-CAM**
3. Wait for `Hard resetting`
4. **Remove the GPIO0 → GND jumper**
5. Press **RST** again
6. Open Serial Monitor @ 115200. You should see:

```
CAM MAC: ...
Wi-Fi channel: 6 (must match main ESP32)
Camera OK
Ready. Capturing every 10 seconds.
```

✅ Both boards done.

---

## PART 8 — Sound files (optional, do it later)

The main sketch plays local WAV clips for instant feedback. **The sketch runs fine
without them** — `playFile()` just silently does nothing if the file is missing.

When you want them, you'll need the LittleFS uploader plugin. Ask me and I'll
walk you through it. Don't let this block you today.

---

## Troubleshooting cheat sheet

| Symptom | Fix |
|---|---|
| No COM port in Tools → Port | Bad USB cable (many are charge-only) or missing driver → Part 5 |
| `A fatal error occurred: Failed to connect` | Hold **BOOT** during "Connecting...", release on "Writing" |
| Endless dots in Serial Monitor | Wrong Wi-Fi password, or hotspot is 5GHz — ESP32 needs **2.4GHz** |
| Serial Monitor shows garbage characters | Baud rate is wrong → set **115200** |
| `Brownout detector was triggered` | Power problem — use the buck, keep the 1000µF cap at the CAM |
| IDE freezes while compiling | Normal on 4GB. Wait. Close browser next time. |
| `Camera init FAILED` | Ribbon cable not seated, or not enough current. Reseat, check 5V. |
| CAM uploads but never receives | MAC typed wrong, or the two boards are on different Wi-Fi channels |

---

## Appendix A — If Arduino IDE 2.x is too heavy for 4GB

Install **Arduino IDE 1.8.19 (Legacy)** instead — same site, scroll to
"Legacy IDE (1.8.X)". It uses about a quarter of the RAM and every step above
works identically (menus are in the same places). Slightly uglier, much faster
on a low-spec laptop. **This is a genuinely good choice for your machine.**

---

## Order of the whole project (reminder)

1. ✅ Upload MAIN ESP32 → copy MAC
2. ✅ Upload ESP32-CAM with that MAC
3. ⬜ Set up the Pi server (`edge_server/server.py`, see README quickstart)
4. ⬜ Point `PI_URL` at the Pi's real IP, re-upload main ESP32
5. ⬜ Bench test everything before mounting on the wearable
