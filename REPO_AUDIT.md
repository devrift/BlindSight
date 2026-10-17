# BlindSight — Repository Error Audit

Date: 2026-10-17 · Branch audited: `arena/b3d8a5c0-blindsight` @ `1246039`
Method: full read of all 42 tracked files, `ruff` static analysis, Python compile checks,
live Flask test-client probes of every endpoint, HTML well-formedness parse, binary
integrity checks, and upstream verification of ESP-IDF / esp32-camera / PlatformIO headers
via the GitHub API.

**35 findings.** Items marked *verified* were reproduced at runtime or confirmed against
upstream sources, not inferred.

---

## A. Blockers — the repo does not build or run

### A1. `.gitignore` contains a committed, unresolved merge conflict
Lines 1, 41, 55 hold `<<<<<<< HEAD`, `=======`, `>>>>>>> 2b0cbbc5…`. `git status` is clean
and there is no `.git/MERGE_HEAD` — **the conflict markers were committed as content.**
Git treats them as literal ignore patterns, so the file is invalid and neither side's rules
apply cleanly.

### A2. `edge_server/vision_ai.py` contains 9 committed merge conflicts → hard `SyntaxError`
Conflict blocks at lines 64/75/81, 158/160/162, 293/295/297, 305/307/309, 324/341/353,
358/371/382, 387/390/392, 427/435/443, 448/456/461.

*Verified* — this is exactly how `server.py` invokes it:
```
$ python3 edge_server/vision_ai.py uploads/image.png
  File "edge_server/vision_ai.py", line 81
    >>>>>>> 2b0cbbc5…:blindsight/pi_server/vision_ai.py
SyntaxError: invalid decimal literal        exit code = 1
```
Consequence: **Tier 1 — the headline "offline edge reflex" — is 100% dead.** Every
`POST /analyse` swallows the `CalledProcessError` and speaks `SAFE_FALLBACK`. The model is
never loaded, no detection ever runs.

### A3. `edge_server/vision_ai.py.orig` is committed
A merge-tool backup artifact, tracked in git. Delete it (and add `*.orig` to `.gitignore`).

### A4. `firmware/esp32_main/src/main.cpp` will not compile with the committed `platformio.ini`
`main.cpp:37` uses the ESP-IDF 5 / Arduino-core-3.x ESP-NOW callback signature with **no
version guard**:
```cpp
void onData(const esp_now_recv_info_t *info, const uint8_t *data, int len)
```
*Verified against upstream:*
- `platform = espressif32` (unpinned) resolves to platform-espressif32 **6.13.0**, whose
  `platform.json` pins `framework-arduinoespressif32 ~3.20017.0` = **arduino-esp32 2.0.17** (IDF 4.4).
- arduino-esp32 2.0.17 `esp_now.h:89`:
  `typedef void (*esp_now_recv_cb_t)(const uint8_t *mac_addr, const uint8_t *data, int data_len);`
  — `esp_now_recv_info_t` **does not exist**.
- ESP-IDF v5.1 `esp_now.h:109` is where the `esp_now_recv_info_t` form appears.

So the PlatformIO build fails on an unknown type. The `.ino` in the *same folder*
(`BlindSight_Main_ESP32.ino:78-86`) already handles this correctly with
`#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3` — `main.cpp`
simply never got the guard. Either add the guard or pin the core (`platform = pioarduino/…`,
`board_build.arduino.memory_type`), but pinning is required either way for reproducibility.

---

## B. Critical — safety and correctness

### B1. ⚠️ Fail-open vision: the code announces "path clear" when vision never ran
The `HEAD` side of the `vision_ai.py` conflict (lines 427-434, 448-455) makes `main()`
print `"Path ahead appears clear."` and **exit 0** when the image is missing, when no ONNX
model is found, and when inference throws.

This contradicts the same file's own contract, twice:
- module docstring: *"Any failure (no model, unreadable image, backend error) exits
  non-zero with the reason on stderr, so server.py speaks its safe fallback instead of
  announcing a clear path."*
- `run_inference`: *"Returning no detections here would be spoken as 'path clear'"* → raises `RuntimeError`

…and `main()` then catches that `RuntimeError` and prints "Path ahead appears clear." anyway.

For a mobility aid for blind users, **telling the user the path is clear when the model
never loaded is the single worst failure mode in this codebase.** The incoming `2b0cbbc`
side (exit 1 + reason on stderr) is the safe one. This must drive how A2 is resolved —
it is not a cosmetic pick-a-side merge.

### B2. `/health` reports a healthy vision pipeline that is completely broken
`VisionService.configured` is just `bool(self.settings.vision_command)` — true whenever
`shlex.split()` yields a non-empty list. It never checks that the script parses or that a
model file exists. *Verified live*, with `vision_ai.py` a SyntaxError and no `.onnx` present:
```json
{"last_vision_success": null, "status": "ok", "vision_model_configured": true}
```
A health endpoint that cannot detect the total failure of the product's primary function.

### B3. `/api/latest-frame` and `/api/chat?use_camera=true` serve a committed placeholder as if it were live
Both fall back to `uploads/image.png` — a 152×155 PNG that is **tracked in git** (see D7).
*Verified*: `GET /api/latest-frame` → `200 image/png 3464 bytes`, i.e. the placeholder.
So the PWA's "Live Vision Feed" displays a stale committed image as a live camera frame,
and the cloud assistant answers "what's in front of me?" about that placeholder while the
user believes it is describing their real surroundings. No staleness check, no timestamp,
no "no camera data" state.

### B4. `BlindSight_Main_ESP32.ino:194` requests a route that does not exist
```cpp
audio.begin(String(PI_URL) + "/audio");   // -> 404
```
The server only serves `/audio/<request_id>` (16 hex chars). *Verified*: `GET /audio` → **404**.
So the Arduino-IDE flashing path — the one the README tells users to follow — never plays
any AI audio response. `src/main.cpp` does this correctly by parsing `audio_url` out of the
JSON reply (`audioPathFromReply`). `Install_BlindSight_Main_ESP32.ps1:96` repeats the bug.

### B5. No LittleFS assets exist, so the documented offline fallback is silent
`platformio.ini` declares `board_build.filesystem = littlefs`, and `playFile()` is called for
`/analysing.wav`, `/left.wav`, `/right.wav`, `/stop.wav`, `/careful.wav`, `/clear.wav`.
But there is **no `data/` directory and zero `.wav` files** in the repo, and `.gitignore`
blocks `*.wav` so they can never be committed. `playFile()` returns silently on a missing
file. Net effect: README §Core Features 2 — *"instant local LittleFS WAV clips … without
depending on the network"* — produces **total silence**.
`Blindsight_Click_by_Click_Guide.md:112` tells you to `cd /path/to/blindsight/main_esp32/data`:
a directory that does not exist, at a path that no longer exists.

### B6. Both firmwares block forever in `setup()` on Wi-Fi, defeating the offline fallback
```cpp
while (WiFi.status() != WL_CONNECTED) { delay(300); Serial.print('.'); }
```
No timeout, no retry cap. If the hotspot is absent, `loop()` never runs — so the button and
the 3× HC-SR04 ultrasonic fallback never execute. This directly contradicts the README's
central safety claim that the ultrasonic path works "without depending on the network."
Present at `.ino:210`, `main.cpp:80`, cam `.ino:112`, cam `main.cpp:79`, PS1:98.
ESP-NOW does not even require an association — a bounded wait plus `WiFi.setSleep(false)`
and an explicit channel match would fix both the hang and the ESP-NOW pairing fragility.

---

## C. High

### C1. `edge_server/blindsight.service` points at a deleted directory — the unit cannot start
```ini
WorkingDirectory=/home/pi/blindsight/pi_server
ExecStart=/usr/bin/python3 /home/pi/blindsight/pi_server/server.py
```
The real server is `edge_server/server.py`. Also: `After=network-online.target` with no
matching `Wants=network-online.target` (so it does not actually wait for the network), no
`Environment=PYTHONUNBUFFERED=1` (logs buffer instead of streaming), and it would run the
Flask dev server as the production service.

### C2. `blindsight/` is a half-deleted orphan tree, still tracked in git
Contains `pi_server/server.py`, `pi_server/requirements.txt`,
`main_esp32/include/secrets.example.h`, `camera_esp32cam/include/secrets.example.h` — but
`pi_server/vision_ai.py`, `pi_server/models/`, `main_esp32/src/`, `camera_esp32cam/src/`,
`templates/` and `static/` are all gone. `pi_server/server.py` cannot work: its
`load_settings()` looks for a `vision_ai.py` beside itself (absent), and `Flask(__name__)`
with no `templates/` means no `/` route and no PWA. Dead duplicate of `edge_server/server.py`.

### C3. `playWavStream` can infinite-loop and smash the stack on a malformed WAV
`main.cpp:56` and PS1:85:
```cpp
for (int i = 0; i < samples; i += channels) { out[outn++] = v; out[outn++] = v; }
```
If `channels == 0`, `i` never advances while `out` (512 elements, stack) overflows. The
`.ino:121` guards this (`if (channels < 1) channels = 1;`); `main.cpp` and the PS1 do not.
The WAV header comes off the network, so this is remotely reachable.

### C4. `.ino` and PS1 have no HTTP timeout — every real request times out
HTTPClient defaults to 5 s. The server allows up to 25 s for inference (`server.py:103`)
plus 20 s for espeak (`server.py:115`). Any genuine `/analyse` round trip exceeds the client
timeout. `main.cpp` sets `setTimeout(30000)`, which still under-covers the ~45 s worst case.

### C5. `.ino` and PS1 accept ESP-NOW packets from any transmitter
No sender-MAC validation, so any device on the channel can inject frames into `imageBuf`
and have them uploaded to the Pi. `main.cpp:36-38` does this correctly via
`isCamera(info->src_addr)`. Compounding the confusion: `.ino:25` declares `CAMERA_MAC` and
never uses it, with the comment *"You do NOT need the camera MAC here"* — while
`Blindsight_Click_by_Click_Guide.md` §5.9 instructs you to paste the CAM MAC into
`CAMERA_MAC` and re-flash. Direct contradiction between code and docs.

### C6. `.ino` and PS1 have no partial-frame timeout → corrupt JPEGs uploaded as valid
`main.cpp:90` recovers a stalled frame after 3 s of silence (`lastPacketAt`). Without it, a
frame that loses its final chunk leaves `expectedCount` and `received[]` dirty. If the camera
then reboots and reuses `imageId` (it always restarts at 1), `p->imageId != activeId` is
false, so the stale `received[]` flags survive and a **mixed/corrupt JPEG is uploaded and
fed to the vision model as though valid.**

### C7. Arduino IDE cannot open either sketch the way the README says
README §3 Step A/B: *"Open `firmware/esp32_main/BlindSight_Main_ESP32.ino` in Arduino IDE."*
Arduino requires the sketch to live in a folder of the same name. The folders are
`esp32_main` / `esp32_cam`, so the IDE errors out and offers to relocate the file into a
new subfolder — which then breaks the PlatformIO layout. The two toolchains are fighting
over one directory.

### C8. `Install_BlindSight_Main_ESP32.ps1` always reports success
`$ErrorActionPreference = 'Stop'` does **not** catch native-command failures, and the
`& $cliPath compile …` / `upload …` exit codes are never checked. A failed build still
prints `SUCCESS: Main ESP32 firmware uploaded.` Also: `arduino-cli lib install ArduinoJson`
installs a library the sketch never includes, and the script writes the Wi-Fi password in
**plaintext** to `~/Desktop/BlindSight_Main_ESP32/BlindSight_Main_ESP32.ino`.

### C9. Service worker scope is wrong — the PWA is not actually offline-capable
`index.html:299` registers `/static/sw.js`. A service worker's scope defaults to its own
directory, so it controls only `/static/*` and **never the page at `/`**. Its precache list
(`'/'`, `/static/manifest.json`, `/static/icon.svg`) and its `navigate` fallback to
`caches.match('/')` therefore never apply. *Verified*: `GET /sw.js` → 404 (no root copy).
Fix: serve `sw.js` from the site root, or return a `Service-Worker-Allowed: /` header.

### C10. `sw.js` resolves `respondWith` with `undefined`
```js
return cached || fetch(event.request).catch(() => {
  if (event.request.mode === 'navigate') { return caches.match('/'); }
});   // <- non-navigation requests fall through to undefined
```
`respondWith` must resolve to a `Response`; `undefined` is a spec violation that turns every
offline subresource fetch into a hard failure instead of a graceful miss.

### C11. Unbounded disk growth in `runtime/`
`/analyse` writes `{sha256[:16]}.jpg`, `{…}.wav` and `latest.jpg` on every request and never
deletes them (only the corrupt-JPEG path calls `unlink`). *Verified*: one test request left
`runtime/f6f9f5fc999c775b.jpg` and `runtime/latest.jpg` behind, and the 503 path leaks the
`.jpg` too. On a 1 GB Pi 3B+ this fills the SD card. Separately, `/audio/<id>` keeps every
spoken response replayable forever — a privacy issue for a device used by a disabled person
in their own home.

### C12. `/api/chat` is unauthenticated and unrate-limited, holding a paid API key
Bound to `0.0.0.0:5000` via `app.run(...)` (the Flask **dev server**) under systemd. Anyone
on the LAN can burn the OpenRouter budget and pull the latest camera frame of a blind user's
surroundings via `/api/latest-frame`. No token, no allowlist, no rate limit.

---

## D. Medium

### D1. `server.py:60-62` prefers a hardcoded absolute path over the file actually running
```python
prod_vision = Path("/home/pi/BlindSight/edge_server/vision_ai.py")
default_vision = str(prod_vision) if prod_vision.is_file() else local_vision.as_posix()
```
If a stale copy exists at that path, it silently wins over the `vision_ai.py` sitting next to
the running server — so you edit one file and the server executes another.

### D2. README setup step references a file that does not exist
README §1: `cp .env.example .env`. *Verified* — `find . -name ".env*"` returns nothing.
No `.env.example` is committed anywhere, so the documented step fails and
`OPENROUTER_API_KEY` is never set. `/api/chat` then returns `FALLBACK_REPLY` with HTTP
**200**, which `index.html:430` treats as success and **speaks aloud as the assistant's
answer** — the user hears a failure string in the assistant's voice with no indication
anything is wrong.

### D3. `edge_server/requirements.txt` is missing dependencies its own docs need
No `ultralytics`, though the README's model-export one-liner is
`python3 -c "from ultralytics import YOLO; …"`. No `opencv-python`, though `vision_ai.py`
has a `cv2.dnn` fallback branch that can therefore never be taken.

### D4. `edge_server/models/README.md:8` has a stale destination path
`mv yolov8n.onnx blindsight/pi_server/models/` — that directory does not exist. Contradicts
the top-level README, which correctly says `mkdir -p models && mv yolov8n.onnx models/`.

### D5. Two live wiring docs give opposite power/grounding instructions
`WIRING_V2_BUCK_AND_TTL.md` opens with *"**Supersedes the 2-Arduino rail design.** Both
Arduinos are now removed"* and mandates ONE buck rail with ONE common ground.
But `PIN_CONNECTION_CHART.md` and `Blindsight_Click_by_Click_Guide.md` still present the
two-Uno, two-isolated-grounds design as current — including *"**Do not** connect the
separate 5 V rails together."* The top-level README follows v2. Neither v1 doc is marked
superseded, so a builder following `PIN_CONNECTION_CHART.md` wires the device differently
from the README. Safety-relevant.

### D6. `Blindsight_Click_by_Click_Guide.md` §4.2 does not match the code it describes
It tells you to edit `const char *WIFI_SSID = "BlindSight";` etc. "at the top of
`src/main.cpp`". But `main.cpp` has no such literals — it does `#include "secrets.h"` and
`#error`s if that file is absent. Following the guide verbatim leads straight into the
`#error`. The guide also uses the dead `blindsight/main_esp32` / `blindsight/camera_esp32cam`
paths throughout (§3.8, §5.1, §6).

### D7. `uploads/image.png` is tracked in git
Both sides of `.gitignore` specify `uploads/*` + `!uploads/.gitkeep`, but gitignore does not
apply to already-tracked files. A runtime artifact committed as source — and it is the exact
placeholder that leaks into `/api/latest-frame` and `/api/chat` (B3).

### D8. `camera_config_t` field spelling differs between the two camera variants
`BlindSight_Camera.ino:72` uses `pin_sccb_sda` / `pin_sccb_scl` (current spelling).
`src/main.cpp:52` uses `pin_sscb_sda` / `pin_sscb_scl`, which upstream esp32-camera marks
`__attribute__((deprecated("please use pin_sccb_sda instead")))`.
*Verified against upstream* `esp_camera.h`: the two names sit in an **anonymous union**, so
this is functionally harmless — but it emits deprecation warnings and diverges from the `.ino`.
Note `pin_sccb_sda` requires esp32-camera ≥ v2.0.2; on older cores only `sscb` exists. Pick
one spelling and pin the core (see A4).

### D9. `vision_ai.py` `LABEL_REPLACEMENTS` has a no-op entry
```python
"traffic light": "traffic light",
```
A self-mapping that does nothing — almost certainly meant to shorten the spoken phrase.

### D10. Dead branch in every firmware variant
`else if (strstr(rule, "Stop")) playFile("/stop.wav");` — `quickRule()` never returns a
string containing `"Stop"`, so `/stop.wav` is unreachable (and does not exist either, B5).
The guide nonetheless generates `stop.wav`.

### D11. Unaligned / strict-aliasing access in `main.cpp` and PS1
`uint32_t rate = *(uint32_t*)(h+24);` (`main.cpp:54`, PS1:83) — strict-aliasing UB and an
unaligned 32-bit access on Xtensa; it only works because `h` happens to be 4-byte aligned.
The `.ino:118-119` correctly uses `memcpy`.

### D12. README performance claims contradict the code
README: "sub-100ms local edge reflex detection", "96ms Inference", and "~120MB" in one place
vs "<150MB" in another. `vision_ai.py`'s own docstring says "execution <1.5s", and
`server.py` allows a **25 s** subprocess timeout. Beyond the numbers disagreeing: `server.py`
spawns a **fresh `python3` process per request**, which re-imports numpy/PIL/onnxruntime and
reloads the ONNX model on every single frame — architecturally the opposite of a 96 ms hot
path. A persistent inference worker (or an in-process session) is needed for that claim to
be reachable.

### D13. Minor / lint
- `edge_server/server.py:3` trailing whitespace (ruff W291).
- `MAX_CONTENT_LENGTH` (1.5 MB for the whole multipart body, including overhead) rejects the
  request *before* the handler's own `photo.read(MAX_UPLOAD_BYTES + 1)` check can emit the
  intended JSON `413`; the two limits should differ by the multipart overhead.
- `companion_ai.py:104` — redundant `continue` at the end of a loop body.
- `int(round(...))` is redundant on Python 3 (ruff RUF046).
- `manifest.json` declares only an SVG icon; `"sizes": "192x192 512x512"` should be `"any"`
  for SVG, and `apple-touch-icon` pointing at an SVG is ignored by iOS (installability and
  home-screen icon both suffer on the platform most blind users will use).
- `vision_ai.py.orig` also carries the pre-merge bug
  `"close ahead" if target.norm_h > 0.55 else "close ahead"` (ruff RUF034) — both conflict
  sides in `vision_ai.py` fixed it, in different ways.
