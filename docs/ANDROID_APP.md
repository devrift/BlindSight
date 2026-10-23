# BlindSight Android app (APK)

The Android app is a **shell**: a real installed app (icon, splash, native
microphone popups) whose screen is the BlindSight web companion talking to your
gateway — the Pi or laptop on your Wi-Fi. All the brains (vision, memory,
voices, AI answers) stay on the gateway, exactly as in the browser version.

## Get the APK (no Android Studio needed)

1. Push or merge any branch to GitHub.
2. Open the repo on GitHub → **Actions** tab → the **Android APK** workflow run.
3. When it goes green, open the run page and download the artifact
   **blindsight-debug-apk** (a zip; unzip to get `app-debug.apk`).
4. Move the APK to your phone (USB, Drive, WhatsApp-to-yourself — anything).

## Install it on the phone

1. Tap the APK. Android will warn "unknown app" — that is expected for a
   sideloaded build. Tap **Settings → Allow from this source**, then Install.
2. Open **BlindSight** from the launcher.
3. First run asks for the **gateway address** — type the `https://…:5000`
   address your Pi starter printed (or `http://<laptop-ip>:5000` for a Windows
   laptop). It is remembered forever; change it any time from the
   "Gateway not reachable" screen or by clearing app data.
4. When you first use the mic, Android shows its own permission popup — allow.
5. The certificate warning is **not shown**: the app trusts the self-signed
   certificate of *your saved gateway host only*. Any other certificate fails
   closed.

## What the shell adds over the browser

* Launcher icon + full-screen theme (no address bar).
* Native mic/camera permission prompts (proper Android flow).
* Scoped self-signed-cert trust, so the Pi's HTTPS works with zero flags.
* A friendly "Gateway not reachable / Try again / Change address" screen
  instead of a browser error page when the gateway is off.

## What it does NOT do

* It does not put vision or AI on the phone — the gateway must be running on
  the same Wi-Fi.
* It is a debug-signed build (fine for personal use). A Play Store release
  would need your own signing key and a Google developer account.

## Building locally (optional)

With Android Studio or a local Gradle 8.7 + JDK 17 + Android SDK 34:

```
cd android && gradle assembleDebug
```

The APK lands in `android/app/build/outputs/apk/debug/app-debug.apk`.
