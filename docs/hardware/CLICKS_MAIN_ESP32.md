# MAIN ESP32 — LITERAL CLICK LIST

Every single click, in order. Don't skip. Don't reorder.
Tick each box as you go.

---

## ⬛ PART A — INSTALL THE IDE (one time)

1. [ ] Close Chrome / Edge / Discord / Spotify completely
2. [ ] Plug laptop into charger
3. [ ] Open browser
4. [ ] Type in address bar: `arduino.cc/en/software` → press **Enter**
5. [ ] Scroll down until you see the heading **"Legacy IDE (1.8.X)"**
6. [ ] Click **"Windows Win 10 and newer, 64 bits"**
7. [ ] A donation page opens → click **"JUST DOWNLOAD"**
8. [ ] Wait for download → click the downloaded file at the bottom of the browser
9. [ ] If Windows asks "Do you want to allow this app..." → click **Yes**
10. [ ] Click **I Agree**
11. [ ] Leave all checkboxes ticked → click **Next**
12. [ ] Click **Install**
13. [ ] Windows pops up "Would you like to install this device software?" (2–3 times)
        → click **Install** every time
14. [ ] Click **Close**
15. [ ] Arduino IDE opens with an empty sketch
16. [ ] **Close your browser now** (frees RAM)

---

## ⬛ PART B — ADD ESP32 SUPPORT (one time)

17. [ ] In Arduino IDE, click menu **File**
18. [ ] Click **Preferences**
19. [ ] Find the text box labelled **"Additional Boards Manager URLs"** (near the bottom)
20. [ ] Click inside that box
21. [ ] Paste this exactly:

```
https://espressif.github.io/arduino-esp32/package_esp32_index.json
```

22. [ ] Click **OK**

---

## ⬛ PART C — DOWNLOAD THE BOARD PACKAGE (one time, SLOW)

23. [ ] Click menu **Tools**
24. [ ] Hover **Board:** → a submenu opens
25. [ ] Click **Boards Manager...**
26. [ ] A panel opens on the left. Click its search box
27. [ ] Type: `esp32`
28. [ ] Scroll to the entry **"esp32 by Espressif Systems"**
29. [ ] Click on that entry
30. [ ] Click the **Install** button that appears

### ⏳ NOW WAIT. 250 MB download. 10–25 minutes.
- [ ] Do NOT close the IDE
- [ ] Do NOT click Install again
- [ ] Bottom bar shows progress
- [ ] It will look frozen at "Installing platform..." — that's normal
- [ ] Go do something else. Come back.

31. [ ] When the entry says **INSTALLED**, click **Close**

---

## ⬛ PART D — PUT THE SKETCH IN THE RIGHT PLACE

32. [ ] Download `BlindSight_Main_ESP32.ino` from the workspace to your laptop
33. [ ] Press **Windows key + E** to open File Explorer
34. [ ] In the left sidebar click **Documents**
35. [ ] Double-click the folder **Arduino**
36. [ ] Right-click in the empty white area
37. [ ] Click **New** → click **Folder**
38. [ ] Type this name exactly, then press Enter:

```
BlindSight_Main_ESP32
```

39. [ ] Double-click into that new folder
40. [ ] Open your **Downloads** folder in another window
41. [ ] Drag `BlindSight_Main_ESP32.ino` into the `BlindSight_Main_ESP32` folder

Final result must look like:
```
Documents\Arduino\BlindSight_Main_ESP32\BlindSight_Main_ESP32.ino
```

42. [ ] **Double-click the .ino file** → Arduino IDE opens it

> 🔴 If the IDE says *"must be inside a sketch folder"* → the folder name doesn't
> exactly match the file name. Fix the spelling and try again.

---

## ⬛ PART E — EDIT 3 LINES

43. [ ] Scroll to the top of the sketch
44. [ ] Find the box marked `EDIT THESE 3 LINES ONLY`
45. [ ] Double-click the words `BlindSight` inside the quotes on the `WIFI_SSID` line
46. [ ] Type your Wi-Fi / hotspot name (keep the quote marks)
47. [ ] Double-click `CHANGE_ME` inside the quotes on the `WIFI_PASSWORD` line
48. [ ] Type your Wi-Fi password (keep the quote marks)
49. [ ] Leave the `PI_URL` line alone for now
50. [ ] Press **Ctrl + S** to save

### ⚠️ FIRST: make sure your network is 2.4GHz
ESP32 cannot see 5GHz. Ever.
- **Android hotspot:** Settings → Hotspot → **AP Band** → **2.4 GHz**
- **iPhone hotspot:** Personal Hotspot → turn ON **Maximize Compatibility**
- **Home router:** usually fine; if it won't connect, use the phone hotspot

---

## ⬛ PART F — PLUG IN THE BOARD

51. [ ] Make sure **nothing** is wired to the ESP32 — no sensors, no DAC, no buck
52. [ ] Plug the USB cable into the ESP32
53. [ ] Plug the other end into the laptop (use a port on the laptop, not a hub)
54. [ ] A small red/blue LED on the board should light up

---

## ⬛ PART G — SET THE 3 TOOLS OPTIONS

55. [ ] Click menu **Tools**
56. [ ] Hover **Board:** → hover **ESP32 Arduino** → click **ESP32 Dev Module**
57. [ ] Click menu **Tools** again
58. [ ] Hover **Upload Speed:** → click **115200**
59. [ ] Click menu **Tools** again
60. [ ] Hover **Port:** → click **COM4** (or whichever COM is listed)

### If no COM port is listed:
- [ ] Unplug the ESP32
- [ ] Tools → Port → note what's there
- [ ] Plug the ESP32 back in, wait 5 seconds
- [ ] Tools → Port → the **NEW** entry is your board
- [ ] Still nothing? Try a **different USB cable** first (most cheap cables are charge-only)
- [ ] Still nothing? Look at the small chip near the USB socket:
      **CP2102** → install "CP210x Windows Driver"
      **CH340 / CH9102** → install "CH341SER driver"
      Then unplug, replug, restart IDE

---

## ⬛ PART H — UPLOAD

61. [ ] Click the **→ (right arrow)** button in the top-left toolbar

62. [ ] Bottom console says `Compiling sketch...`

### ⏳ WAIT 3–8 MINUTES. The IDE will look frozen. IT IS NOT.
- [ ] Do NOT close it
- [ ] Do NOT click the arrow again

63. [ ] Console then shows: `Connecting........_____`

64. [ ] **If dots keep going past ~10 seconds:**
     - [ ] Press and **HOLD** the **BOOT** button on the ESP32
     - [ ] Keep holding
     - [ ] Release when you see `Writing at 0x00001000...` with a **%** counting up

65. [ ] Success looks like:
```
Hash of data verified.
Leaving...
Hard resetting via RTS pin...
```

---

## ⬛ PART I — READ THE MAC (the whole point)

66. [ ] Click menu **Tools**
67. [ ] Click **Serial Monitor**
68. [ ] In the **bottom-right dropdown** of that window, click **115200 baud**
69. [ ] Press the **EN** button on the ESP32 board (the one NOT labelled BOOT)
70. [ ] You should see:

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

71. [ ] Highlight the MAC with your mouse
72. [ ] Press **Ctrl + C**
73. [ ] Open **Notepad**, press **Ctrl + V**, press **Ctrl + S**, save as `MAC.txt`

# 🎉 DONE. That's the whole session.

---

## ⬛ IF SOMETHING GOES WRONG

| Console / screen says | Do this |
|---|---|
| No COM port anywhere | Different USB cable. Then driver (step 60 notes). |
| `Failed to connect to ESP32` | Hold **BOOT** during "Connecting", release on % |
| `Serial port busy / access denied` | Close Serial Monitor, then upload again |
| Endless `....` after "Connecting to WiFi" | Wrong password, or network is **5GHz** |
| Serial Monitor shows `��������` | Dropdown is on wrong baud → set **115200** |
| `brownout detector` repeating | Bad USB port/cable → try a rear port, no hub |
| `must be inside a sketch folder` | Folder name ≠ file name (Part D step 38) |
| IDE frozen 5 min while compiling | Normal on 4GB. Wait it out. |

Paste any red error text to me and I'll decode it.
