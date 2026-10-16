# BlindSight — Wiring Cleanup Plan (NO CODING DAY)

> Code is already done. Nothing to program today. Just hands.
> Follow top to bottom. Tick each box. Stop when tired — it's resumable.

---

## 0. Before you touch anything

- [ ] Power OFF. Unplug buck / power bank / USB.
- [ ] Clear a table. Phone camera ready.
- [ ] **TAKE A PHOTO OF THE CURRENT NEST** (from 2 angles) before you pull anything apart. Insurance.

---

## 1. Shopping list (₹400–600 total, local shop or Amazon)

| Item | Why | Approx ₹ |
|---|---|---|
| WAGO 221 lever nuts, 5-way × 2 (or choc block strip) | 5V bus + GND bus, no soldering | 150–300 |
| Male–male + male–female Dupont jumpers (40pc set) | replaces alligator clips | 100 |
| Small perfboard (3×5 cm is enough) | mount cap + 3 ECHO dividers | 20 |
| Zip ties / spiral wrap | bundling | 50 |
| Masking tape (label flags) | labelling | 20 |
| Hot glue sticks | strain relief + locking | 30 |

Optional but nice: red + black wire spool, heatshrink.

---

## 2. Order of operations (do NOT skip around)

### Step A — Strip it down
- [ ] Disconnect everything. Coil the alligator clips and put them in a box (keep for bench testing).
- [ ] Lay the boards out in the physical positions they'll sit in the final build.

### Step B — Build the power bus (this alone kills 70% of the mess)
- [ ] WAGO #1 = **5V node**. WAGO #2 = **GND node**.
- [ ] Buck converter **+OUT → 5V WAGO**, **−OUT → GND WAGO**.
- [ ] ⚠️ **Before any board is connected:** power the buck, multimeter across the two WAGOs, adjust to **5.0–5.1 V**. Never above 5.25 V.
- [ ] Power off again.

### Step C — Solder the tiny passives board (only soldering today)
On the 3×5 cm perfboard:
- [ ] 1000 µF 16 V cap — **stripe leg = GND side**
- [ ] 3 × ECHO divider: `ECHO in → 1kΩ → tap → 2kΩ → GND`, tap = output to ESP32
- [ ] Pigtail wires out: `5V`, `GND`, `ECHO-L in/out`, `ECHO-C in/out`, `ECHO-R in/out`
- [ ] No expensive module gets soldered. Only ₹10 of passives.

### Step D — Wire power, one board at a time, testing as you go
After EACH board: power on, confirm it lights/boots, power off, then do the next.
- [ ] Main ESP32 → 5V bus + GND bus
- [ ] ESP32-CAM → 5V bus + GND bus (**cap sits right at the CAM's pins**)
- [ ] PCM5102 DAC → 5V bus + GND bus
- [ ] HC-SR04 ×3 → 5V bus + GND bus
- [ ] Pi stays on its **own** power bank — do not put it on this bus

### Step E — Signal wires (Dupont, red/black reserved for power only)
| From | To | Label |
|---|---|---|
| DAC BCK | ESP32 GPIO 26 | `BCLK` |
| DAC LCK/WS | ESP32 GPIO 25 | `LRCK` |
| DAC DIN | ESP32 GPIO 22 | `DIN` |
| Sensor L TRIG | ESP32 GPIO 13 | `TRIG-L` |
| Sensor C TRIG | ESP32 GPIO 14 | `TRIG-C` |
| Sensor R TRIG | ESP32 GPIO 27 | `TRIG-R` |
| Sensor L ECHO → divider → | ESP32 GPIO 34 | `ECHO-L` |
| Sensor C ECHO → divider → | ESP32 GPIO 35 | `ECHO-C` |
| Sensor R ECHO → divider → | ESP32 GPIO 39 | `ECHO-R` |
| Button side 1 | ESP32 GPIO 32 | `BTN` |
| Button side 2 | GND bus | `BTN-GND` |

- [ ] Tape-flag **both ends** of every signal wire with the label above.

### Step F — Bundle & lock
- [ ] Bundle by destination: `CAM bundle`, `DAC bundle`, `Sensor-L/C/R bundles`.
- [ ] Zip tie each bundle.
- [ ] Hot glue strain relief ~2 cm from every board connector.
- [ ] Hot glue any Dupont that has already tested good.

---

## 3. Final sanity checks (power OFF)

- [ ] Continuity 5V bus ↔ GND bus = **NO beep** (if it beeps, find the short before powering up)
- [ ] Cap polarity: stripe leg on GND ✔
- [ ] No 12V wire touching any board
- [ ] Pi's ground is NOT tied to this bus (it's wireless-only)
- [ ] GPIO0 on the CAM is **NOT** grounded (that's upload mode only)

Then power on. Boards should boot with no reset loops.

---

## 4. If you only have 20 minutes
Do **Step B** (power bus) and nothing else. That single change removes most of the nightmare and most of the brownout risk.

---

## 5. What's left after wiring (for later, not today)
1. Set up the Pi server (`pi_server/` — copy files, `pip install -r requirements.txt`, run)
2. Run `Install_BlindSight_Main_ESP32.ps1` → uploads main ESP32 on COM4
3. Note the **MAIN MAC** it prints, paste into the CAM sketch, upload CAM
4. Upload the LittleFS WAV clips
5. Bench test before mounting on the wearable

*Nothing above requires you to write code. Only copy, paste, click.*
