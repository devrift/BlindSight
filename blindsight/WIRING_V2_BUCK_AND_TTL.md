# BlindSight — WIRING v2 (Buck Converter + Real USB-TTL)

**Supersedes the 2-Arduino rail design.** Both Arduinos are now removed from the build.

| Changed | Old (v1) | New (v2) |
|---|---|---|
| Power | Uno #1 rail + Uno #2 rail (separate, never joined) | **ONE buck converter 5V 3A**, single shared rail |
| Grounds | Two isolated grounds | **One common ground** for everything on the buck |
| CAM flashing | Arduino-as-TTL (RESET→GND trick) | **Real USB-TTL adapter** |
| CAM RX level | 1k/2k divider needed (Arduino was 5V) | **No divider** — if TTL is set to 3.3V |
| Arduinos | 2 used | **0 used** ✅ |

---

## 1. SYSTEM BLOCK DIAGRAM

```
                    ┌──────────────────────┐
  12V SOURCE ──[F1]─┤  BUCK CONVERTER      ├── 5.0V OUT ──┐
  (battery /        │  set to 5.0-5.1V     │              │
   adapter)  ───────┤  3A                  ├── GND OUT ──┐│
                    └──────────────────────┘             ││
                                                         ││
        ┌────────────────────────────────────────────────┘│
        │  ┌──────────────────────────────────────────────┘
        │  │
        ▼  ▼
   ╔═══════════════╗  ╔═══════════════╗
   ║  5V BUS       ║  ║  GND BUS      ║   (WAGO lever nut
   ║ (lever nut)   ║  ║ (lever nut)   ║    or choc block)
   ╚═══╤═╤═╤═╤═╤═══╝  ╚═══╤═╤═╤═╤═╤═══╝
       │ │ │ │ │          │ │ │ │ │
       │ │ │ │ └──────────┼─┼─┼─┼─┴──► HC-SR04 RIGHT
       │ │ │ └────────────┼─┼─┼───────► HC-SR04 CENTRE
       │ │ └──────────────┼─┼─────────► HC-SR04 LEFT
       │ └────────────────┼───────────► PCM5102 DAC
       └──────────────────┴───────────► MAIN ESP32 (VIN pin)
       │                  │
       └──────┬───────────┴──────┐
              │                  │
         ┌────┴──────────────────┴────┐
         │  ESP32-CAM  + 1000µF cap   │   ← cap soldered AT the CAM pins
         └────────────────────────────┘


   ┌──────────────────────┐
   │ RASPBERRY PI 3B+     │   ← OWN POWER BANK. Totally separate.
   │ own 5V/2.5A supply   │      NO wire to the buck. NO shared GND.
   └──────────────────────┘      Talks over Wi-Fi only.
```

### 🔴 The one absolute rule
**The Pi's ground must NEVER touch the buck's ground.** There is no data wire between
the Pi and any ESP32 — everything is wireless (Wi-Fi HTTP + ESP-NOW). So they need no
common reference. Keeping them isolated avoids ground loops and protects the Pi.

Everything else (ESP32, CAM, DAC, sensors) **shares one ground.** That is required —
they exchange signals, so they need a common reference.

---

## 2. BUCK CONVERTER SETUP

### ⚠️ Do this BEFORE connecting any board

1. Connect **12V source → buck IN+ / IN−** only. Nothing on the output.
2. Power on.
3. Multimeter on **DC Volts**, probes on buck **OUT+ / OUT−**.
4. Turn the small blue **potentiometer screw** until you read **5.00 – 5.10 V**.
   - Many pots need 15–20 full turns. Keep going, it's normal.
   - **Never above 5.25 V.** Over 5.5V starts killing ESP32s.
5. Power off. Now start connecting boards.

### Fuses (cheap insurance)
| Position | Rating | Why |
|---|---|---|
| **F1** — on the 12V input wire | 3 A | stops a buck failure burning the battery |
| Optional on 5V output | 3 A | stops a short frying every board at once |

An inline blade-fuse holder from a car-accessory shop is ₹40.

### Current budget on a 3A buck
| Load | Typical | Peak |
|---|---|---|
| Main ESP32 (Wi-Fi TX) | 120 mA | 400 mA |
| ESP32-CAM (capture + Wi-Fi) | 250 mA | 800 mA |
| PCM5102 DAC | 25 mA | 50 mA |
| 3 × HC-SR04 | 45 mA | 60 mA |
| **TOTAL** | **~0.44 A** | **~1.3 A** |

**3A is comfortable** — roughly 2.3× headroom on peak. ✅
(The 5A/6A figure from earlier assumed the Pi was on the same buck. It isn't.)

---

## 3. MAIN ESP32 DEVKIT V1 — FULL PIN LIST

| ESP32 pin | Goes to | Notes |
|---|---|---|
| **VIN** (a.k.a. 5V) | **5V BUS** | 5V in, onboard regulator makes 3.3V |
| **GND** (any one) | **GND BUS** | |
| GPIO 26 | PCM5102 **BCK** | I2S bit clock |
| GPIO 25 | PCM5102 **LCK / LRCK** | I2S word select |
| GPIO 22 | PCM5102 **DIN** | I2S data |
| GPIO 13 | HC-SR04 **LEFT — TRIG** | direct, 3.3V out is fine |
| GPIO 14 | HC-SR04 **CENTRE — TRIG** | direct |
| GPIO 27 | HC-SR04 **RIGHT — TRIG** | direct |
| GPIO 34 | LEFT **ECHO** → **via divider** | ⚠️ input-only pin |
| GPIO 35 | CENTRE **ECHO** → **via divider** | ⚠️ input-only pin |
| GPIO 39 | RIGHT **ECHO** → **via divider** | ⚠️ input-only pin |
| GPIO 32 | **Button** side 1 | code uses INPUT_PULLUP |
| GND | **Button** side 2 | pressed = LOW |

> ❗ Do **NOT** feed 5V into the ESP32's **3V3** pin. Use **VIN** only.

---

## 4. HC-SR04 × 3 — AND THE MANDATORY DIVIDER

HC-SR04 runs on 5V, so its ECHO output is **5V**. GPIO 34/35/39 are **3.3V max** and
have **no internal protection**. Feeding 5V in will damage the pin.

```
   HC-SR04 ECHO ──────[ 1 kΩ ]──────┬───────► ESP32 GPIO (34/35/39)
                                    │
                                 [ 2 kΩ ]
                                    │
                                   GND BUS
```

Output = 5V × 2k/(1k+2k) = **3.33 V** ✅

Build **three** of these — one per sensor. Solder them on a scrap perfboard, don't
leave them on alligator clips.

| Sensor | VCC | GND | TRIG | ECHO → divider → |
|---|---|---|---|---|
| **LEFT** | 5V BUS | GND BUS | GPIO 13 | GPIO 34 |
| **CENTRE** | 5V BUS | GND BUS | GPIO 14 | GPIO 35 |
| **RIGHT** | 5V BUS | GND BUS | GPIO 27 | GPIO 39 |

Physical placement: LEFT ≈ 45° left, CENTRE straight ahead, RIGHT ≈ 45° right.
Keep them ≥10 cm apart so they don't hear each other's pings.

---

## 5. PCM5102 / PCM5100 I2S DAC

| DAC pin | Goes to |
|---|---|
| **VIN / VCC** | 5V BUS |
| **GND** | GND BUS |
| **BCK** | ESP32 GPIO **26** |
| **LCK / LRCK / WS** | ESP32 GPIO **25** |
| **DIN** | ESP32 GPIO **22** |
| **SCK** | **GND** (or leave open — see note) |
| **LOUT / ROUT / AGND** | earphone jack tip / ring / sleeve |

> **SCK:** most PCM5102 breakouts have a solder jumper that already ties SCK low
> (internal PLL mode). If you get no sound or only hiss, **connect SCK to GND**
> explicitly. That fixes it 9 times out of 10.

> **FLT / DEMP / XSMT:** if your board exposes these, XSMT must be **HIGH** (many
> boards pull it up already). If there's silence, tie **XSMT → 3.3V**.

⚠️ PCM5102 is **line level**. Earphones will be quiet but audible. For exhibition
volume add a small **PAM8403** amp or a powered speaker.

---

## 6. ESP32-CAM — RUN MODE (normal operation)

| ESP32-CAM pin | Goes to |
|---|---|
| **5V** | 5V BUS |
| **GND** | GND BUS |
| **1000µF 16V cap** | **+** to the CAM's 5V pin, **−** (stripe) to the CAM's GND pin |
| GPIO 0 | **nothing** — must be floating to boot normally |
| U0R / U0T | **nothing** in run mode |

### Cap placement
Solder the cap legs **directly onto the CAM's 5V and GND pins**, or as close as
physically possible. On long alligator leads it does maybe half its job. This cap is
what prevents `Brownout detector was triggered` when the Wi-Fi radio fires.

Your **1000µF 16V** is correct — 16V rating on a 5V line is fine and safer than 6.3V.

---

## 7. ESP32-CAM — FLASH MODE (real USB-TTL adapter)

### 🔴 THE BIG CHANGE: TX and RX are now CROSSED

With the Arduino trick the labels lined up straight. With a **real USB-TTL adapter
they cross.** Get this wrong and you get "Failed to connect."

| USB-TTL adapter | ESP32-CAM | Note |
|---|---|---|
| **TXD** | **U0R** (GPIO3) | TTL transmit → CAM receive |
| **RXD** | **U0T** (GPIO1) | CAM transmit → TTL receive |
| **GND** | **GND** | **mandatory** |
| **3V3 / 5V** | ⛔ **leave disconnected** | power the CAM from the buck instead |
| — | **GPIO 0 → GND** | jumper = flash mode |

```
  USB-TTL                       ESP32-CAM
  ┌────────┐                   ┌──────────┐
  │  TXD ──┼──────────────────►│ U0R      │
  │  RXD ◄─┼───────────────────┤ U0T      │
  │  GND ──┼───────────────────┤ GND      │
  │  5V  ✗ │  (not used)       │ 5V ◄─── from BUCK 5V BUS
  └────────┘                   │ GPIO0 ──┐│
                               └─────────┼┘
                                         ▼
                                        GND   (jumper, flash mode only)
```

### ⚙️ Set the adapter to 3.3V logic
Most CP2102/FT232/CH340 adapters have a **jumper or slide switch** marked
**3V3 / 5V**. Set it to **3V3**.

- **At 3V3 → no divider needed on U0R.** ✅ This is why a real TTL is better.
- If your adapter is **5V-only**, keep the divider on the TTL-TXD → U0R line:
  `TXD ──[1kΩ]──┬── U0R` and `┬──[2kΩ]── GND`

The CAM→TTL direction (U0T → RXD) **never** needs a divider.

### Why not power the CAM from the TTL's 5V pin?
Most adapters supply only 100–500 mA. The CAM pulls up to 800 mA on a capture.
Power it from the **buck**, keep the TTL for data + a shared GND only.
**The GND wire between TTL and CAM is still required** — it's the signal reference.

### Flash sequence
1. GPIO0 → GND jumper in place
2. Buck ON (CAM powered), TTL plugged into laptop
3. Arduino IDE → Board **AI Thinker ESP32-CAM**, Upload Speed **115200**, Port = TTL's COM
4. Click Upload → when it prints `Connecting.....` press the **RST** button on the CAM
5. After `Hard resetting` → **remove the GPIO0 jumper**
6. Press **RST** again → it boots your sketch
7. Serial Monitor @115200 should show `Camera OK`

---

## 8. FALLBACK BUTTON

```
   GPIO 32 ──────┬─── [ BUTTON ] ─── GND BUS
                 │
          (internal pull-up, enabled in code)
```

No resistor needed. Pressed = LOW. Use two diagonally-opposite legs on a 4-pin
tactile switch (the other two are internally joined).

---

## 9. FULL CONNECTION TABLE (build checklist)

| # | From | To | Type |
|---|---|---|---|
| 1 | 12V source + | Buck IN+ (through 3A fuse) | power |
| 2 | 12V source − | Buck IN− | power |
| 3 | Buck OUT+ | **5V BUS** | power |
| 4 | Buck OUT− | **GND BUS** | power |
| 5 | 5V BUS | ESP32 **VIN** | power |
| 6 | GND BUS | ESP32 **GND** | power |
| 7 | 5V BUS | ESP32-CAM **5V** | power |
| 8 | GND BUS | ESP32-CAM **GND** | power |
| 9 | Cap **+** | CAM 5V pin | decoupling |
| 10 | Cap **−** (stripe) | CAM GND pin | decoupling |
| 11 | 5V BUS | DAC VIN | power |
| 12 | GND BUS | DAC GND | power |
| 13 | 5V BUS | HC-SR04 L/C/R VCC ×3 | power |
| 14 | GND BUS | HC-SR04 L/C/R GND ×3 | power |
| 15 | GPIO 26 | DAC BCK | signal |
| 16 | GPIO 25 | DAC LCK | signal |
| 17 | GPIO 22 | DAC DIN | signal |
| 18 | DAC SCK | GND BUS | signal (if no sound) |
| 19 | GPIO 13 | Sensor L TRIG | signal |
| 20 | GPIO 14 | Sensor C TRIG | signal |
| 21 | GPIO 27 | Sensor R TRIG | signal |
| 22 | Sensor L ECHO | 1k → GPIO 34, 2k → GND | divider |
| 23 | Sensor C ECHO | 1k → GPIO 35, 2k → GND | divider |
| 24 | Sensor R ECHO | 1k → GPIO 39, 2k → GND | divider |
| 25 | GPIO 32 | Button leg 1 | signal |
| 26 | GND BUS | Button leg 2 | signal |
| 27 | DAC LOUT/ROUT/AGND | Earphone jack | audio |
| — | Raspberry Pi | **own power bank only** | ⛔ isolated |

**27 connections total.** Tick them off one at a time.

---

## 10. PRE-POWER CHECKLIST

Power **OFF**, multimeter in continuity/beep mode:

- [ ] 5V BUS ↔ GND BUS → **NO beep** (a beep = short, find it before powering)
- [ ] Buck output measured **5.0–5.1 V** before boards were connected
- [ ] Cap **stripe leg is on GND** (reversed = it can burst)
- [ ] No 12V wire touches any board — 12V exists **only** between source and buck IN
- [ ] Nothing is plugged into the ESP32's **3V3** pin
- [ ] CAM **GPIO0 is NOT grounded** (run mode)
- [ ] Each ECHO reaches the GPIO **only through its divider** — no direct wire
- [ ] Pi is on its **own** power bank, no wire to the buck
- [ ] Both Arduinos are **out of the build** entirely

Then power on. Expect: ESP32 LED on, CAM LED on, no reset loops, no hot components.

---

## 11. BRING-UP ORDER (add one thing at a time)

1. **Buck only** → verify 5.0V on a meter
2. **+ Main ESP32** → Serial Monitor shows the MAC banner
3. **+ Button** → press it, Serial prints `L= C= R=` (999s are fine, no sensors yet)
4. **+ Sensors** one at a time → wave your hand, distance should change
5. **+ DAC & earphones** → button press should play audio
6. **+ ESP32-CAM** → Serial shows `Camera OK` and `Sending JPEG`
7. **+ Pi server** → full loop

If a step fails, the thing you just added is the culprit. That's the whole point of
going one at a time.
