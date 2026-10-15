# GUIDE 1 — Main ESP32 ONLY

**Goal of today:** get the main ESP32 DevKit V1 flashed and printing its MAC address.
Nothing else. No camera. No Pi. No wiring. Just the board + USB cable + laptop.

**Time:** ~30 min (25 of it is downloads finishing while you do nothing)
**Libraries to install:** ZERO. Everything this sketch uses is built in.

---

# ⚡ Before you start

- [ ] Plug laptop into charger
- [ ] Close **Chrome / Edge** completely, and Discord / Spotify / WhatsApp
      (4GB RAM — the compiler needs it more than the browser does)
- [ ] Have the ESP32 DevKit V1 + a **data** USB cable ready
      (many cheap cables are charge-only and will silently not work)

---

# STEP 1 — Install Arduino IDE

### 👉 Use version 1.8.19, NOT 2.x

On 4GB RAM, IDE 2.x eats ~1.5GB and stutters. 1.8.19 uses ~400MB and does the exact
same job. Every step below is written for 1.8.19.

1. Go to **https://www.arduino.cc/en/software**
2. Scroll down to the section **"Legacy IDE (1.8.X)"**
3. Click **"Windows Win 10 and newer, 64 bits"**
4. On the donation page click **"JUST DOWNLOAD"**
5. Run the downloaded installer
6. Click **I Agree** → leave all checkboxes ticked → **Install**
7. Windows will ask 2–3 times to install drivers (Adafruit / Arduino USB) → click **Install** each time
8. Click **Close**. The IDE opens with an empty sketch.
9. **Now close your browser** to free RAM.

---

# STEP 2 — Tell the IDE about ESP32

1. Menu bar: **File → Preferences**
2. Near the bottom, find the field **"Additional Boards Manager URLs"**
3. Click inside it and paste exactly this:

```
https://espressif.github.io/arduino-esp32/package_esp32_index.json
```

4. Click **OK**

---

# STEP 3 — Install the ESP32 board package

1. Menu bar: **Tools → Board → Boards Manager...**
2. A panel opens on the left. In its search box, type: **esp32**
3. Scroll to find **"esp32 by Espressif Systems"**
4. Click it, then click the **Install** button

⏳ **This downloads about 250 MB.** On a slow connection it can take 15–25 minutes.

- Do NOT close the IDE
- Do NOT click Install twice
- The bar at the very bottom shows progress
- It's normal for the window to look frozen at "Installing platform..."

5. When the entry says **INSTALLED**, click **Close**.

> This is a one-time thing. You never do Steps 1–3 again.

---

# STEP 4 — Get the sketch file onto your laptop

The file is in this workspace at:

```
arduino_sketches/BlindSight_Main_ESP32/BlindSight_Main_ESP32.ino
```

1. Download it to your laptop.
2. Open **File Explorer** → go to **Documents → Arduino**
3. Right-click → **New → Folder** → name it exactly:

```
BlindSight_Main_ESP32
```

4. Move the downloaded `BlindSight_Main_ESP32.ino` **into that folder**.

You should end up with:

```
Documents\Arduino\BlindSight_Main_ESP32\BlindSight_Main_ESP32.ino
```

> 🔴 **Folder name must exactly match the file name.** Arduino refuses to open
> a `.ino` that isn't inside a folder of the same name. This trips up everyone once.

5. **Double-click the .ino file.** It opens in Arduino IDE.

---

# STEP 5 — Edit 3 lines (the only "coding" you do)

Near the top of the sketch you'll see a box like this:

```cpp
// ##############################################################
// ###############  EDIT THESE 3 LINES ONLY  ####################
const char *WIFI_SSID     = "BlindSight";
const char *WIFI_PASSWORD = "CHANGE_ME";
const char *PI_URL        = "http://192.168.4.1:5000";
// ##############################################################
```

Change them to your real values:

| Line | What to put |
|---|---|
| `WIFI_SSID` | Name of your **2.4GHz** Wi-Fi or phone hotspot |
| `WIFI_PASSWORD` | That network's password |
| `PI_URL` | Leave the default for now — Pi isn't set up yet |

**Keep the quote marks.** Only change what's between them.

### ⚠️ The #1 thing that breaks this
**ESP32 cannot see 5GHz Wi-Fi. Ever.** It's a hardware limitation.

If you're using a phone hotspot:
- **Android:** Hotspot settings → AP Band → choose **2.4 GHz**
- **iPhone:** turn ON **"Maximize Compatibility"** in Personal Hotspot

If you use your home router and it's a single combined network, it usually still
works because 2.4GHz is broadcast under the same name. If it doesn't connect,
use the phone hotspot instead — easier than fighting the router.

---

# STEP 6 — Set the board options

Go to the **Tools** menu and set these three:

| Menu item | Set to |
|---|---|
| **Tools → Board → ESP32 Arduino → ESP32 Dev Module** | ESP32 Dev Module |
| **Tools → Upload Speed** | **115200** |
| **Tools → Port** | **COM4** (or whatever appears) |

Leave every other Tools setting at its default. Don't touch Partition Scheme,
Flash Frequency, PSRAM, etc.

### If no COM port appears
1. Unplug the ESP32
2. Open **Tools → Port** and note what's listed
3. Plug the ESP32 back in, wait 5 seconds
4. Open **Tools → Port** again — the **new** entry is your board

Still nothing? Two possible causes:
- **Charge-only USB cable** → try a different cable first, this is the most common cause
- **Missing USB driver** → look at the small chip next to the USB socket:
  - Says **CP2102** → search "CP210x Windows Driver" (Silicon Labs), install, replug
  - Says **CH340** or **CH9102** → search "CH341SER driver", install, replug

---

# STEP 7 — Upload

1. Click the **→ (right arrow)** button in the top-left toolbar

2. **The first compile takes 3–8 minutes.** The IDE will look frozen.
   **It is not frozen.** Do not close it. Do not click again.
   The bottom black console will show `Compiling sketch...`

3. When compiling finishes, the console shows:
   ```
   Connecting........_____
   ```

4. **If those dots keep going for more than ~10 seconds:**
   - Press and **HOLD** the **BOOT** button on the ESP32
   - Keep holding it
   - Release only when you see `Writing at 0x00001000...` and a percentage counting up

   (Many DevKit V1 boards auto-reset fine and you never need this. Try without first.)

5. **Success looks like this:**
   ```
   Writing at 0x00010000... (100%)
   Wrote 8xxxxx bytes ...
   Hash of data verified.
   Leaving...
   Hard resetting via RTS pin...
   ```

🎉 The board is flashed.

---

# STEP 8 — Read the MAC address (the actual prize)

1. Menu: **Tools → Serial Monitor** (or the magnifying-glass icon, top-right)
2. In the **bottom-right dropdown** of that window, select **115200 baud**
3. Press the **EN** (or **RST**) button on the ESP32 board
4. You should see:

```
Connecting to WiFi...
....
=====================================
  MAIN MAC: 3C:71:BF:12:34:56
  ^^ COPY THIS INTO THE CAMERA SKETCH
=====================================
Wi-Fi channel: 6
Ready. Press the button to test the sensors.
```

5. **Highlight that MAC with your mouse, Ctrl+C, paste it into Notepad and save it.**
   You need it for the camera sketch tomorrow. Losing it just means repeating Step 8.

## ✅ You are done for today if you see the MAC.

---

# What "success" actually means right now

You have NOT built the whole device. You've proven:
- ✅ Toolchain installed and working
- ✅ Board is alive and flashable
- ✅ Wi-Fi credentials are correct
- ✅ You have the MAC for the camera

That's genuinely the hardest and most fragile part of the software side. Everything
after this is repeats of the same click sequence.

---

# Troubleshooting

| What you see | What it means | Fix |
|---|---|---|
| No COM port listed | Cable or driver | Try another USB cable first. Then Step 6 driver notes. |
| `A fatal error occurred: Failed to connect to ESP32` | Board not in flash mode | Hold **BOOT** during "Connecting...", release when % appears |
| `Serial port COM4 busy / access denied` | Serial Monitor is open | Close Serial Monitor, then upload |
| Dots forever after "Connecting to WiFi..." | Wrong password, or **5GHz network** | Switch hotspot to 2.4GHz. Recheck password (case-sensitive) |
| Serial Monitor shows `����������` | Wrong baud rate | Set dropdown to **115200** |
| Console shows `rst:0x10 ... brownout` on repeat | Not enough power | Use a different USB port, or a rear/powered port, not a hub |
| IDE freezes 5 min during compile | Normal on 4GB | Wait. Don't kill it. |
| `esptool.py not found` / weird Python error | Board package half-installed | Redo Step 3, let it fully finish |

---

# ⚠️ Do NOT connect any hardware yet

For this whole guide the ESP32 should have **only the USB cable** in it.
No sensors, no DAC, no buck converter, nothing.

Reason: if something is miswired, you don't want to find out during your first
upload — you won't know whether it's a software or hardware problem. Get a clean
software success first, then add hardware one piece at a time.

---

# Next session (not today)

1. Wire the button (GPIO32 → button → GND) and test with Serial Monitor
2. Add the 3 ultrasonic sensors + dividers, watch `L= C= R=` distances print
3. Add the PCM5102 DAC
4. Upload the camera sketch with the MAC you saved
5. Set up the Pi server
