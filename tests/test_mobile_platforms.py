"""Cross-platform contract: Android (full voice), iOS (dictation + spoken
replies), Windows/desktop (testing).

Apple exposes no SpeechRecognition to any web app on any iOS browser, and iOS
blocks audio playback before the first touch. These tests pin the honest
degradation a phone user depends on: no dead mic button, no blocking
window.prompt (invisible in iOS home-screen PWAs), audio unlocked on first
gesture, Stop silencing gateway audio, and install metadata for both platforms.
"""
from __future__ import annotations

import json
import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]


def _app_js(client) -> str:
    return client.get("/static/app.js").get_data(as_text=True)


def test_shell_declares_ios_pwa_support(client):
    html = client.get("/").get_data(as_text=True)
    assert "apple-mobile-web-app-capable" in html
    assert "apple-touch-icon" in html
    assert "apple-mobile-web-app-status-bar-style" in html
    assert "apple-mobile-web-app-title" in html


def test_token_ui_replaces_blocking_prompts(client):
    html = client.get("/").get_data(as_text=True)
    assert 'id="token-input"' in html and 'id="token-save"' in html
    js = _app_js(client)
    # window.prompt never appears in iOS standalone PWAs and a wrong-token
    # prompt loop recursed forever; the inline field replaces both failures.
    assert "window.prompt(" not in js
    assert "showTokenControl" in js


def test_app_js_handles_missing_speech_recognition(client):
    js = _app_js(client)
    assert "hasSR" in js and "isIOS" in js
    assert "focusDictation" in js
    assert "keyboard" in js.lower()  # dictation guidance for iPhone users
    # recognition constructors must never run when the API is absent
    assert re.search(r"if \(!hasSR\) return;", js)
    assert re.search(r"if \(!hasSR\) \{ focusDictation\(\); return; \}", js)


def test_app_js_unlocks_audio_on_first_gesture(client):
    js = _app_js(client)
    assert "unlockAudio" in js and "audioUnlocked" in js
    assert "touchend" in js
    assert "SILENT_WAV" in js


def test_stop_button_stops_gateway_audio(client):
    js = _app_js(client)
    assert "currentPlayer" in js
    # stopSpeaking must pause the neural-mp3 element, not just browser synth
    stop_body = js.split("function stopSpeaking()", 1)[1].split("/* ---", 1)[0]
    assert "currentPlayer.pause()" in stop_body


def test_manifest_matches_theme_and_platform_copy(client):
    manifest = json.loads(client.get("/static/manifest.json").get_data(as_text=True))
    assert manifest["theme_color"] == "#05070c"
    assert manifest["background_color"] == "#05070c"
    assert manifest["display"] == "standalone"
    html = client.get("/").get_data(as_text=True)
    assert 'content="#05070c"' in html


def test_service_worker_cache_bumped_for_mobile_release(client):
    sw = client.get("/static/sw.js").get_data(as_text=True)
    assert "blindsight-v5" in sw
    assert "/static/apple-touch-icon.png" in sw


def test_health_reports_app_version(client):
    report = client.get("/health").get_json()
    assert isinstance(report.get("version"), str) and report["version"]


def test_pi_starter_prints_https_phone_url():
    sh = (REPO_ROOT / "start_blindsight.sh").read_text(encoding="utf-8")
    assert "https://" in sh
    assert "certificate" in sh.lower()
    # set -e must not kill the starter when hostname -I yields nothing
    assert '[ -n "${IP}" ] &&' not in sh
