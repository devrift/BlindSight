# BlindSight — get it running in 3 steps (v1.5)

Everything lives on GitHub already (branch `main`). You do not need to be a
programmer for this page.

## Step 1 — get the files (once)

Pick either way:

* **No git installed?** Open <https://github.com/devrift/BlindSight>, click the
  green **Code** button, choose **Download ZIP**, and unpack it anywhere
  (for example `C:\BlindSight`).
* **With git:** `git clone https://github.com/devrift/BlindSight.git`

To update later: download a fresh ZIP, or run `git pull` inside the folder.

## Step 2 — start the edge server (every time)

The edge server is the "brain" the glasses and the web app talk to. It binds to
`0.0.0.0` (all network interfaces) so phones and the ESP32 can reach it over any
Wi-Fi/hotspot.

```bash
python -m venv .venv
# Windows:
.venv\Scripts\pip install -r edge_server\requirements.txt
.venv\Scripts\python edge_server\server.py
# Linux / Raspberry Pi:
.venv/bin/pip install -r edge_server/requirements.txt
.venv/bin/python edge_server/server.py
```

Keep the window/terminal open. On startup it prints a banner like:

```
============================================================
  BlindSight Edge Gateway v1.5.0
  Listening on http://0.0.0.0:5000  (all interfaces)
  Reachable LAN address(es) — type one into BlindSight-Setup:
    http://192.168.1.42:5000
  ESP32 portal: join 'BlindSight-Setup', enter the IP above + port.
============================================================
```

**Write down that LAN IP** (e.g. `192.168.1.42`) — you'll type it into the
glasses in Step 3.

For the web companion app, run `npm install` then `npm run dev` and open
<http://localhost:3000>. If the edge server runs on a different machine (e.g. a
Pi), start Vite with `EDGE_SERVER_URL=http://<pi-ip>:5000 npm run dev`.

## Step 3 — point the glasses at the server (no reflashing!)

Since v1.5 you configure the ESP32 from your phone, not by editing code:

1. Power the ESP32. If it can't reach the saved Wi-Fi or server, it opens a
   Wi-Fi network called **`BlindSight-Setup`**. (You can also force this by
   **holding the reflex button for 3 seconds**; a short press still works as
   the normal reflex trigger.)
2. On your phone, join **`BlindSight-Setup`**. A page pops up — or open
   <http://192.168.4.1>.
3. Select your venue Wi-Fi, enter its password, and type the **Edge Server IP**
   and **Port** from the Step 2 banner (defaults: `192.168.1.100` / `5000`).
4. Save. The ESP32 reboots, connects, and remembers the settings.

**At a new exhibit, just repeat Step 3 — no laptop, no reflash.** The ESP32-CAM
has its own **`BlindSight-Cam-Setup`** portal; join the *same* venue Wi-Fi there.

> Firmware library dependency: **WiFiManager by tzapu** (≥ 2.0.17). PlatformIO
> installs it automatically; in the Arduino IDE use Library Manager.

## Using the app

* On the computer: <http://localhost:3000> (Vite) or <http://localhost:5000>
  (edge server directly).
* On your phone (same Wi-Fi): `http://<the computer's LAN IP>:3000`.
* **Android (Chrome/Edge):** allow the microphone, then say **"hey BlindSight"**
  and ask your question — or tap the big button, or type.
* **iPhone/iPad (Safari):** tap the big button, then tap the **microphone on the
  iPhone keyboard** to dictate, then tap Ask. Answers are spoken back.
* Say `help` and it reads every command aloud. `remember …`, `what do you
  remember?`, `language hindi`, `stop` all work with no internet.

## Optional — cloud answers

1. Copy `.env.example` to `.env`.
2. Add your key (e.g. `GEMINI_API_KEY=...` or `OPENROUTER_API_KEY=sk-or-...`).
3. Restart the edge server. Without a key the app still works using the local
   intent router / Ollama.

## Using Antigravity or another editor?

Just open the unpacked folder as the project. Nothing else is required — the
server and firmware are plain files inside it.
