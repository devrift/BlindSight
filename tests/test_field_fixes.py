"""v3.3 field-fix regression contract.

Every assertion here pins a confirmed field failure so it can never silently
return: the Android beep-over-mic that killed recognition after ~1s, the
noise-gate mic contention, silent recognition deaths, phone layout bugs, the
soft-keyboard trap, button-state races, and stale service-worker shells.
"""
from __future__ import annotations


def _js(client) -> str:
    return client.get("/static/app.js").get_data(as_text=True)


def test_no_audio_cue_into_opening_microphone(client):
    js = _js(client)
    # F1: earcon('listen') played at the instant of cmdRec.start() stole audio
    # focus on Android and killed the session ~1s later.
    assert "earcon('listen');" not in js
    assert "earcon('wake');" not in js
    assert "flashCue" in js  # visual-only cue replaced the beeps


def test_noise_gate_stream_paused_during_recognition(client):
    js = _js(client)
    # F2: a second live mic stream starves Chrome's recognizer on Android.
    assert "function stopMicAnalytics()" in js
    body_wake = js.split("function startWakeSession()", 1)[1].split("function", 1)[0]
    body_cmd = js.split("function startCommandSession(", 1)[1].split("\nfunction ", 1)[0]
    assert "stopMicAnalytics();" in body_wake
    assert "stopMicAnalytics();" in body_cmd
    # recalibration resumes when listening resumes
    resume = js.split("function resumeListening()", 1)[1].split("}", 1)[0]
    assert "openMicAnalytics()" in resume


def test_recognition_errors_are_spoken_not_silent(client):
    js = _js(client)
    err = js.split("function handleRecError(", 1)[1].split("\nfunction ", 1)[0]
    assert err.count("speak(msg)") >= 3  # not-allowed, audio-capture, network
    assert "lastNetworkRetry" in err     # network drops retry once on their own


def test_mic_button_state_race_guards(client):
    js = _js(client)
    click = js.split("$('mic-btn').addEventListener('click'", 1)[1].split("});", 1)[0]
    assert "appState === 'think'" in click          # no colliding session
    assert "lastMicTap" in click and "300" in click  # double-tap debounce


def test_rate_limit_speaks_honestly(client):
    js = _js(client)
    assert "Slow down a little" in js


def test_frame_button_tells_truth_about_token(client):
    js = _js(client)
    frame = js.split("$('frame-btn')", 1)[1][:700]
    assert "showTokenControl(" in frame


def test_soft_keyboard_keeps_controls_visible(client):
    js = _js(client)
    assert "visualViewport" in js
    assert "scrollIntoView({ block: 'nearest' })" in js


def test_mobile_layout_fixes_in_css(client):
    css = client.get("/static/app.css").get_data(as_text=True)
    assert "100dvh" in css and "100vh" not in css
    assert "env(safe-area-inset-top)" in css
    assert "env(safe-area-inset-bottom)" in css
    assert "#token-control { flex-direction: column" in css
    assert "#mic-btn.cue" in css


def test_service_worker_revalidates_shell(client):
    sw = client.get("/static/sw.js").get_data(as_text=True)
    assert "blindsight-v5" in sw
    assert "REVALIDATE" in sw and "'/static/app.js'" in sw
