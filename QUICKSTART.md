# BlindSight — get it running in 3 steps

Everything lives on GitHub already (branch `main`). You do not need to be a
programmer for this page.

## Step 1 — get the files (once)

Pick either way:

* **No git installed?** Open <https://github.com/devrift/BlindSight>, click the
  green **Code** button, choose **Download ZIP**, and unpack it anywhere
  (for example `C:\BlindSight`).
* **With git:** `git clone https://github.com/devrift/BlindSight.git`

To update later: download a fresh ZIP, or run `git pull` inside the folder.

## Step 2 — start it (every time)

* **Windows:** double-click `start_blindsight.bat`. The first run installs
  everything (a minute or two); afterwards it opens
  <http://localhost:5000> in your browser by itself.
* **Raspberry Pi / Linux:** `./start_blindsight.sh`
* **Any other setup:**
  ```
  python -m venv .venv
  .venv\Scripts\pip install -r edge_server\requirements.txt   (Windows)
  .venv\Scripts\python edge_server\server.py                  (Windows)
  ```

Keep the window/terminal open while using the app.

## Step 3 — use it

* On the computer: <http://localhost:5000>
* On your phone (same Wi-Fi): `http://<the computer's IP>:5000`
  (the starter script prints that address on the Pi)
* **Android phone (Chrome or Edge):** use the `https://` address the Pi
  prints, accept the certificate once, allow the microphone, then say
  **“hey BlindSight”** and ask your question — or tap the big button, or type.
* **iPhone / iPad (Safari):** Apple does not let any web app listen for a wake
  word, so BlindSight tells you what to do instead: tap the big button, then
  tap the **microphone on the iPhone keyboard** to dictate, then tap Ask.
  Answers are still spoken back in the same human-sounding voice.
* **Windows computer (Chrome or Edge):** everything works on
  <http://localhost:5000> — the easiest way to test before deploying to a Pi.
* Say `help` and it reads every command aloud. `remember …`, `what do you
  remember?`, `language hindi`, `stop` all work with no internet.

## Optional — real cloud answers

1. Copy `edge_server/.env.example` to `edge_server/.env`.
2. Put your OpenRouter key in it: `OPENROUTER_API_KEY=sk-or-...`
3. Restart the starter script. Without this the app still works, but cloud
   questions get an honest “cloud unavailable” reply.

## Using Antigravity or another editor?

Just open the unpacked folder as the project. Nothing else is required — the
starter scripts and the server are plain files inside it.
