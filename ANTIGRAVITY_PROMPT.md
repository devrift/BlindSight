# Antigravity / Agentic IDE Prompt — BlindSight Full Remediation

Paste everything below the line into Antigravity (or any agentic IDE: Cursor, Copilot
Workspace, Claude Code, Codex). It assumes `REPO_AUDIT.md` is present in the repo root —
that file carries the full evidence, verified reproductions and line numbers. If it is
missing, paste its contents in place of step 0.

Model-agnostic. Written for a long-horizon agent with shell access.

---

## PROMPT STARTS HERE

You are remediating `BlindSight`, an open-source assistive mobility wearable for blind and
low-vision users (ESP32-CAM → ESP-NOW → Main ESP32 → Raspberry Pi Flask gateway → YOLOv8n
ONNX edge inference + espeak-ng audio, plus a phone PWA talking to OpenRouter).

**This is safety-relevant software.** It tells a blind person whether the path in front of
them is clear. Every judgement call below is biased toward *failing loudly and safely*
rather than *failing quietly and optimistically*. Do not "improve" that bias away.

### Step 0 — Read before you touch anything

Read, in full, and build a mental model before editing:

```
REPO_AUDIT.md                                  <- the authoritative findings list (35 items)
.gitignore
README.md
edge_server/server.py
edge_server/vision_ai.py            (contains 9 committed merge conflicts)
edge_server/vision_ai.py.orig       (merge backup artifact, to be deleted)
edge_server/companion_ai.py
edge_server/blindsight.service
edge_server/models/README.md
edge_server/requirements.txt
edge_server/static/sw.js
edge_server/static/manifest.json
edge_server/templates/index.html
blindsight/pi_server/server.py      (orphan legacy tree)
firmware/esp32_main/src/main.cpp
firmware/esp32_main/BlindSight_Main_ESP32.ino
firmware/esp32_main/platformio.ini
firmware/esp32_cam/src/main.cpp
firmware/esp32_cam/BlindSight_Camera.ino
firmware/esp32_cam/platformio.ini
docs/hardware/*.md
docs/hardware/Install_BlindSight_Main_ESP32.ps1
```

`REPO_AUDIT.md` uses IDs (A1–A4, B1–B6, C1–C12, D1–D13). Reference those IDs in your
commit messages and in your final report.

### Hard constraints

- Work on the current branch. Never switch, create, or force-push branches.
- Never delete, rename, or move the repo root or `.git`.
- Do not commit runtime artifacts: `runtime/*`, `uploads/latest.jpg`, `__pycache__/`,
  `*.pyc`, `.pio/`, `.env`. The only intentional binary additions are the LittleFS voice
  clips described in task 6.
- Do not weaken a security or safety control to make a test pass.
- Do not invent performance numbers. If a claim in `README.md` cannot be met by the code
  as written, change the claim, not the code's behaviour.
- Keep the project usable by a non-technical builder. The `docs/hardware/` guides and the
  PowerShell one-click installer exist for that audience on purpose — do not silently
  delete them.
- Every task must end with its verification command passing. Do not move on with a red test.

### DECISIONS — already made, do not re-litigate

These are the judgement calls the audit flagged. Apply them exactly.

**D-1. Merge conflict resolution in `edge_server/vision_ai.py` (audit A2 + B1).**
This is *not* a pick-a-side merge. Combine:
- Take the **incoming `2b0cbbc`** behaviour for **all four `main()` error paths**: missing
  image, missing model, backend load failure, inference exception → print the reason to
  **stderr** and **return 1**. Never print a navigation sentence on a failure path.
  Rationale (B1): the `HEAD` side prints `"Path ahead appears clear."` and exits 0 when the
  model never ran. For a blind user that is the worst possible output. `run_inference`
  already raises specifically to avoid it, and the module docstring documents the
  fail-closed contract — `HEAD`'s `main()` violated both.
- Keep **`HEAD`'s** wider `get_model_path()` candidate list (`server_dir`, `root_dir`,
  `BLINDSIGHT_MODEL_PATH`, the `/home/pi/BlindSight/...` paths).
- Keep **`HEAD`'s** `generate_sentence(detections, orig_w, orig_h)` signature and its
  lateral escape-path **density** logic (`left_density` / `right_density`) — it is a genuine
  improvement over the incoming side's boolean blocked/unblocked test.
- Keep **`HEAD`'s** `Warning:` vs `Caution:` prefix distinction for centre and side hazards.
- For `graph_optimization_level`, take **`HEAD`**: `ORT_ENABLE_BASIC`. `ORT_ENABLE_ALL`
  costs load time and RAM on a 1 GB Pi 3B+ for negligible gain.
- After resolving, **delete** `edge_server/vision_ai.py.orig` (A3) and add `*.orig` to
  `.gitignore`.

**D-2. `.gitignore` (A1).** Take the `HEAD` side (it matches the current
`edge_server/` + `firmware/` layout; the incoming side references the deleted `blindsight/`
tree). Then add: `*.orig`, `*.rej`, `*.bak`, and a negation for the committed voice clips
(see task 6): `!firmware/esp32_main/data/*.wav`.

**D-3. ESP-NOW callback signature (A4).** Support **both** cores with the
`#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3` guard pattern
that `BlindSight_Main_ESP32.ino:78-86` already uses. Port that guard into
`firmware/esp32_main/src/main.cpp`. Then **pin** the platform in both `platformio.ini`
files so the build is reproducible, and add a comment stating which core the pin resolves
to. Verified facts: unpinned `platform = espressif32` resolves to platform-espressif32
6.13.0 → `framework-arduinoespressif32 ~3.20017.0` → arduino-esp32 **2.0.17** (IDF 4.4),
where `esp_now.h:89` is
`typedef void (*esp_now_recv_cb_t)(const uint8_t *mac_addr, const uint8_t *data, int data_len);`
and `esp_now_recv_info_t` does **not** exist (it arrives in IDF 5.1).

**D-4. Camera SCCB field spelling (D8).** Standardise on `pin_sccb_sda` / `pin_sccb_scl`
(current upstream spelling) in *both* camera variants. Verified: upstream `esp_camera.h`
puts both spellings in an anonymous `union`, so this is warning-hygiene, not a behaviour
change — but `pin_sccb_sda` requires esp32-camera ≥ v2.0.2, which is consistent with the
core pin from D-3. Add a one-line comment noting the legacy `sscb` alias is deprecated.

**D-5. Firmware variants.** Keep all three firmware entry points working and **in sync**
(`src/main.cpp`, the `.ino`, and the PowerShell installer's embedded sketch). They serve
different audiences — PlatformIO for developers, Arduino IDE / the `.ps1` for
non-technical builders. Every bug fix must land in all copies that contain the bug. Add a
`FIRMWARE_VARIANTS.md` note in `firmware/` explaining which to use and that the three must
be kept identical in behaviour.

**D-6. Legacy `blindsight/` tree (C2).** Delete it: `blindsight/pi_server/server.py`,
`blindsight/pi_server/requirements.txt`,
`blindsight/main_esp32/include/secrets.example.h`,
`blindsight/camera_esp32cam/include/secrets.example.h`. It is half-deleted already (no
`vision_ai.py`, no `models/`, no `src/`, no templates) and cannot run. Its live
functionality is duplicated by `edge_server/`. Remove the whole `blindsight/` directory.

**D-7. Performance claims (D12).** Correct `README.md` to match the code. Replace
"sub-100ms", "96ms Inference" and the "~120MB"/"<150MB" inconsistency with the measured
target already documented in `vision_ai.py`'s own docstring (~1.5 s per frame, <150 MB).
State plainly that `server.py` spawns a fresh interpreter per request. Add a
`## Roadmap / Known limitations` section naming the persistent-inference-worker change as
future work — do **not** implement it here.

### Tasks, in dependency order

Nothing below can be validated until task 1 lands. Do not reorder.

**1. Resolve the committed merge conflicts (A1, A2, A3, B1) — per D-1 and D-2.**
Then verify:
```bash
grep -rn "^<<<<<<<\|^=======$\|^>>>>>>>" --exclude-dir=.git . && echo "FAIL: markers remain" || echo "PASS"
find . -name "*.orig" -not -path "./.git/*" | grep . && echo "FAIL" || echo "PASS"
python3 -m py_compile edge_server/server.py edge_server/vision_ai.py edge_server/companion_ai.py && echo PASS
# fail-closed contract — stdout MUST be empty, exit code MUST be non-zero:
python3 edge_server/vision_ai.py /nonexistent.jpg; echo "exit=$? (want non-zero)"
BLINDSIGHT_MODEL_PATH=/nonexistent.onnx python3 edge_server/vision_ai.py uploads/image.png; echo "exit=$? (want non-zero)"
```

**2. Make `/health` honest (B2).** `VisionService.configured` must actually verify the
pipeline. At minimum: the vision script exists and parses, and a model file was located.
Report distinct fields, e.g. `vision_script_ok`, `model_path` (or `null`),
`espeak_ok` (probe with `shutil.which` plus a 1-word synth to a temp file),
`last_vision_success`, and an overall `status` that is `"degraded"` or `"error"` — not
`"ok"` — when any component is missing. Verify:
```bash
python3 -c "
import sys; sys.path.insert(0,'edge_server'); import server
print(server.app.test_client().get('/health').get_json())"
# with no .onnx present this MUST NOT report status ok / vision ready
```

**3. Stop serving the placeholder as a live frame (B3, D7-of-audit).**
Remove `uploads/image.png` from git tracking (`git rm --cached uploads/image.png`) and from
the `/api/latest-frame` and `/api/chat` candidate lists. `/api/latest-frame` must return
**404 `{"error":"no frame captured yet"}`** when no camera frame has arrived, and
`index.html` must render an explicit "Waiting for camera" state rather than an image that
looks live. `/api/chat` with `use_camera=true` and no fresh frame must tell the model there
is **no image** (send text only) rather than silently attaching a stale one; add a frame
max-age (e.g. 30 s) after which it is considered stale. Verify with the test client that
`/api/latest-frame` 404s on a clean checkout.

**4. Fix the firmware audio + robustness bugs (B4, C3, C4, C5, C6, D10, D11).**
Apply to **every** copy per D-5:
- Audio URL: fetch `GET {PI_URL}/audio` → **404**. Parse `audio_url` from the `/analyse`
  JSON reply as `main.cpp`'s `audioPathFromReply()` already does. Port that function into
  the `.ino` and the PS1 sketch.
- `playWavStream`: guard `channels == 0` (`.ino:121` already does; `main.cpp:56` and
  PS1:85 do not) → infinite loop plus overflow of the 512-element stack buffer.
- Replace the `*(uint32_t*)(h+24)` unaligned/strict-aliasing casts in `main.cpp:54` and
  PS1:83 with `memcpy` (as `.ino:118-119` does).
- HTTP timeouts: `http.setTimeout()` / `audio.setTimeout()` on **all** paths. The server
  allows 25 s inference + 20 s TTS, so set ≥ 60 s. The `.ino` and PS1 currently use the
  5 s HTTPClient default and time out on every real request.
- Sender validation: port `isCamera()` MAC filtering into the `.ino` and PS1. Resolve the
  `CAMERA_MAC` contradiction — `.ino:25` says "You do NOT need the camera MAC here" while
  `Blindsight_Click_by_Click_Guide.md` §5.9 tells the user to paste it in. Pick one
  (recommend: MAC filtering ON, and fix the guide) and make code + docs agree.
- Partial-frame recovery: port the `lastPacketAt` 3 s stall reset from `main.cpp:90` into
  the `.ino` and PS1. Without it a lost final chunk plus a camera reboot (which restarts
  `imageId` at 1) uploads a corrupt mixed JPEG as valid.
- Remove or implement the dead `strstr(rule, "Stop")` branch — `quickRule()` never returns
  a string containing "Stop".
- Wi-Fi blocking (B6): replace `while (WiFi.status() != WL_CONNECTED)` with a bounded wait
  (e.g. 20 s) in **all five** places, then continue into `loop()` regardless so the button
  and HC-SR04 ultrasonic fallback work with no network. Call `WiFi.setSleep(false)` and
  log the channel. This is what makes the README's offline-safety claim actually true.

**5. Make the PlatformIO build compile (A4, D8) — per D-3 and D-4.**
Verify:
```bash
grep -n "espressif32" firmware/*/platformio.ini     # must be pinned, not bare
grep -n "ESP_ARDUINO_VERSION_MAJOR" firmware/esp32_main/src/main.cpp   # guard present
grep -rn "pin_sscb_" firmware/ && echo "FAIL: deprecated spelling" || echo "PASS"
```
If PlatformIO is installed, additionally run `pio run -d firmware/esp32_main` and
`pio run -d firmware/esp32_cam` and make them green. If it is not installed, say so in your
report rather than claiming a passing build.

**6. Ship the LittleFS voice clips (B5).**
There is no `data/` directory and zero `.wav` files, while `.gitignore` blocks `*.wav` — so
every `playFile()` silently no-ops and the documented offline fallback is **silent**.
- Create `firmware/esp32_main/data/` containing `analysing.wav`, `left.wav`, `right.wav`,
  `stop.wav`, `careful.wav`, `clear.wav`.
- Format is mandatory: **16-bit PCM, mono, 22050 Hz** RIFF/WAV with a canonical 44-byte
  header. `playFile()` does *not* call `i2s_set_sample_rates()` — it plays at whatever
  `initI2S()` configured (22050). Wrong rate = wrong pitch and speed.
- Generate them with `espeak-ng -v en -s 155 -w <file> "<text>"` (the exact commands are in
  `Blindsight_Click_by_Click_Guide.md:113-118`), then resample to 22050 Hz if needed.
  Text: `analysing`="Analysing", `left`="Obstacle ahead. Move left.",
  `right`="Obstacle ahead. Move right.", `stop`="Stop. Obstacle very close ahead.",
  `careful`="Object ahead. Proceed carefully.", `clear`="Path clear."
- If `espeak-ng` is unavailable, commit a `firmware/esp32_main/data/generate_clips.sh` (or
  `.py`) that produces them, and state clearly in your report that the clips were **not**
  generated. Do not commit silent or tone-only placeholders and call them done.
- Commit a `tools/verify_wav.py` that asserts the format of every clip, and run it.
- Un-ignore them per D-2.

**7. Fix deployment config (C1, D1-of-audit, C12, D3).**
- `edge_server/blindsight.service`: point `WorkingDirectory` and `ExecStart` at the real
  `edge_server/` path. Add `Wants=network-online.target` alongside the existing `After=`
  (without `Wants=`, `After=network-online.target` does not wait for the network). Add
  `Environment=PYTHONUNBUFFERED=1`. Do not ship the Flask dev server as the production
  entry point — use a `gunicorn`/`waitress` `ExecStart` (add the dep to
  `requirements.txt`) or document the dev-server limitation loudly in the unit's comments.
- `server.py:60-62`: stop preferring the hardcoded `/home/pi/BlindSight/edge_server/vision_ai.py`
  over the file next to the running server. Prefer the local file; allow an explicit
  `VISION_SCRIPT` env override.
- `/api/chat`: add a shared-secret bearer token (`BLINDSIGHT_API_TOKEN` env, optional but
  documented) and a simple in-process rate limit. Return 401/429 as appropriate.
- `edge_server/requirements.txt`: add `ultralytics` (the README's export one-liner needs it)
  and note `opencv-python` as the optional extra for the `cv2.dnn` fallback branch.
- Add the missing `edge_server/.env.example` that `README.md` already tells users to copy
  (audit D2), documenting `OPENROUTER_API_KEY`, `BLINDSIGHT_API_TOKEN`, `VISION_COMMAND`,
  `BLINDSIGHT_MODEL_PATH`, `BLINDSIGHT_DATA`, `ESPEAK_BINARY`.

**8. Fix the request lifecycle (C11) and the failure-path UX (D2-of-audit).**
- `runtime/` grows without bound: `/analyse` writes a `.jpg` + `.wav` + `latest.jpg` per
  request and never deletes them (only the corrupt-JPEG path unlinks). Add a TTL sweeper
  (e.g. delete `runtime/*` older than 1 h on a background thread or at request time) and
  cap the directory. Note the privacy angle in a comment: `/audio/<id>` currently makes
  every spoken response replayable forever.
- Reorder `/analyse` so TTS availability is checked **before** running the expensive
  inference, instead of discarding a completed inference and returning 503.
- Reconcile `MAX_CONTENT_LENGTH` (1.5 MB for the whole multipart body) with the handler's
  own `MAX_UPLOAD_BYTES + 1` check so the intended JSON 413 is actually reachable.
- `companion_ai.py`: `/api/chat` returns `FALLBACK_REPLY` with HTTP **200**, and
  `index.html:430` treats 200 as success and **speaks the failure string aloud as if it were
  the assistant's answer**. Make the failure distinguishable — return a `degraded: true`
  flag (or a non-2xx status) and have the UI announce "I couldn't reach my vision brain"
  in a visibly/aurally distinct way, not as a normal reply.

**9. Fix the PWA (C9, C10, and the manifest nit in D13).**
- `index.html:299` registers `/static/sw.js`. A service worker's scope defaults to its own
  directory, so it controls only `/static/*` and **never the page at `/`** — the precache
  list and the offline `navigate` fallback are inert. Fix by serving `sw.js` from the site
  root (add a route or move the file) or by sending `Service-Worker-Allowed: /`. Verify the
  chosen approach actually puts the SW in control of `/`.
- `sw.js` `fetch` handler resolves `respondWith` with `undefined` for non-navigation
  requests on a network failure — a spec violation that turns every offline subresource
  miss into a hard error. Return a real `Response` or don't call `respondWith`.
- `manifest.json`: `"sizes": "192x192 512x512"` on an SVG should be `"any"`, and the
  `apple-touch-icon` pointing at an SVG is ignored by iOS. Add PNG icons (192 + 512, and a
  maskable variant) so the app is genuinely installable.

**10. Reconcile the docs with reality (D4, D5, D6, C7, C8, D7).**
- `edge_server/models/README.md:8` → `mv yolov8n.onnx blindsight/pi_server/models/` is a
  dead path. Point it at `edge_server/models/`.
- **Contradictory wiring docs with safety implications**: `WIRING_V2_BUCK_AND_TTL.md`
  declares it *"Supersedes the 2-Arduino rail design"* (one buck rail, one common ground),
  while `PIN_CONNECTION_CHART.md` and `Blindsight_Click_by_Click_Guide.md` still present
  the two-Uno, two-isolated-grounds design as current — including *"Do not connect the
  separate 5 V rails together."* Pick v2 as canonical, add a prominent
  `> ⚠️ SUPERSEDED by WIRING_V2_BUCK_AND_TTL.md` banner at the top of every v1 doc (do not
  just delete them — builders may have printed them), and make the top-level README's power
  section consistent with v2.
- `Blindsight_Click_by_Click_Guide.md` §4.2 tells users to edit
  `const char *WIFI_SSID = "BlindSight";` at the top of `src/main.cpp`. That file has no
  such literals — it `#include "secrets.h"` and `#error`s without it, so following the guide
  verbatim hits the `#error`. Rewrite §4.2 and §5 to the `secrets.h` workflow, and replace
  every `blindsight/...` path in the guide with the current `firmware/...` layout
  (§3.8, §5.1, §6, including the `data/` path).
- Arduino IDE sketch-folder rule (C7): `README.md` §3 says to open
  `firmware/esp32_main/BlindSight_Main_ESP32.ino` in the Arduino IDE, but Arduino requires
  the sketch to sit in a folder of the same name (the folders are `esp32_main`/`esp32_cam`).
  Either restructure so the IDE path works as documented, or correct the README to describe
  what actually happens. Make README and filesystem agree.
- `Install_BlindSight_Main_ESP32.ps1` (C8): `$ErrorActionPreference='Stop'` does **not**
  catch native-command failures, so a failed compile still prints
  `SUCCESS: Main ESP32 firmware uploaded.` Check `$LASTEXITCODE` after every `& $cliPath`
  call and abort on non-zero. Remove the unused `arduino-cli lib install ArduinoJson`. Stop
  writing the Wi-Fi password in plaintext to
  `~/Desktop/BlindSight_Main_ESP32/BlindSight_Main_ESP32.ino` — or at minimum warn the user
  and offer to delete the generated sketch after upload. Sync the embedded sketch with all
  of task 4's fixes per D-5.
- Apply D-7 to the README performance claims.
- Fix the trailing whitespace at `edge_server/server.py:3` (ruff W291), the redundant
  `continue` at `companion_ai.py:104`, the redundant `int(round(...))` (RUF046), and the
  no-op `"traffic light": "traffic light"` entry in `LABEL_REPLACEMENTS` (D9).

**11. Add regression tests so this cannot silently rot again.**
Create `tests/` with `pytest`:
- `test_vision_fail_closed.py` — missing image, missing model, unreadable image and a forced
  inference exception each produce a **non-zero exit, empty stdout, and a reason on stderr**.
  Assert stdout never contains a navigation sentence on any failure path. This is the B1
  regression guard; it is the most important test in the repo.
- `test_generate_sentence.py` — centre/left/right hazard combinations, the `norm_h` 0.30 and
  0.55 thresholds, the density-based escape-path choice, and the empty-detections case.
- `test_server_api.py` — using the Flask test client: `/health` reflects a broken pipeline;
  `/api/latest-frame` 404s with no frame; `/analyse` rejects corrupt bytes with 400 and an
  oversize body with a JSON 413; `/audio/<id>` rejects non-16-char and non-alnum ids;
  `/api/chat` requires a prompt and enforces the token from task 7.
- `test_no_conflict_markers.py` — fail if `<<<<<<<`, `=======` or `>>>>>>>` appear in any
  tracked text file, or if any `*.orig` exists. This is the A1/A2 regression guard.
- `test_wav_assets.py` — assert every clip in `firmware/esp32_main/data/` is RIFF/WAV,
  16-bit, mono, 22050 Hz.
Add a GitHub Actions workflow running ruff + pytest on push and PR.

### Definition of done

All of these must pass, and you must paste their real output into your final report:

```bash
# no merge artifacts anywhere
grep -rn "^<<<<<<<\|^=======$\|^>>>>>>>" --exclude-dir=.git . ; echo "exit=$? (want 1)"
find . \( -name "*.orig" -o -name "*.rej" \) -not -path "./.git/*" ; echo "(want empty)"

# python parses and lints clean
python3 -m py_compile $(git ls-files '*.py') && echo "PASS compile"
ruff check --select=E,F,W,B,S,PLE,PLW,C4,SIM,RUF --ignore=E501,S104,S603 .

# the safety contract, explicitly
python3 edge_server/vision_ai.py /nonexistent.jpg ; echo "exit=$? (want non-zero, stdout empty)"

# tests + assets
pytest -q
python3 tools/verify_wav.py

# no runtime artifacts staged
git status --short
git ls-files uploads/ runtime/ ; echo "(want only .gitkeep)"
```

### Final report format

Give me:
1. A table: audit ID → status (fixed / partially fixed / not fixed) → files changed → the
   verification output that proves it.
2. Every item you could **not** fully fix, and exactly why (missing hardware, missing
   `espeak-ng`, no PlatformIO toolchain, needs a physical Pi, etc.). Do not mark these done.
3. Anything you deliberately did differently from the DECISIONS block, with your reasoning.
4. Any new bug you found while fixing these that is not already in `REPO_AUDIT.md`.

Do not summarise the audit back to me. Report what you *changed* and what you *proved*.

## PROMPT ENDS HERE

---

### Two notes on using this

**On the model.** This prompt is model-agnostic — nothing in it depends on a specific
Gemini version. What it *does* depend on is an agent with shell access and a long enough
horizon to run the verification commands, because most of the value is in the
`grep`/`pytest`/exit-code checks rather than the edits. If your Antigravity agent cannot
execute shell commands, delete the verification blocks and review the diffs by hand.

**On scope.** Task 11 (tests + CI) and task 6 (generating real 22050 Hz voice clips) are the
two most likely to be skipped or faked by an agent under pressure to finish. They are also
the two that stop this repo regressing — the committed merge conflicts in A1/A2 made it into
`main` precisely because nothing checked for them. If you cut scope, cut task 10's doc
rewrites before you cut those.
