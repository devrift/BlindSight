"""Voice-first PWA shell: the pieces a blind user depends on must exist.

These are deliberately structural assertions - the shell is the product for a
blind user, so the assertive live region, the labelled mic button and the
voice engine assets are part of the contract, not cosmetics.
"""
from __future__ import annotations


def test_shell_serves_voice_ui_assets(client):
    page = client.get("/")
    assert page.status_code == 200
    html = page.get_data(as_text=True)
    assert 'id="sr-announcer"' in html and 'aria-live="assertive"' in html
    assert 'id="mic-btn"' in html and 'aria-label=' in html
    assert 'id="mode-select"' in html and 'id="voice-select"' in html
    assert 'id="language-select"' in html and 'id="mic-meter"' in html
    assert "/static/app.js" in html and "/static/app.css" in html
    # the visual status line must not double-announce over the live region
    assert 'aria-hidden="true"' in html


def test_voice_assets_are_served(client):
    js = client.get("/static/app.js")
    css = client.get("/static/app.css")
    assert js.status_code == 200 and "javascript" in js.headers["Content-Type"]
    assert css.status_code == 200 and "text/css" in css.headers["Content-Type"]
    assert "WAKE_PHRASES" in js.get_data(as_text=True)


def test_service_worker_precaches_voice_assets(client):
    sw = client.get("/static/sw.js").get_data(as_text=True)
    for asset in ("/static/app.js", "/static/app.css"):
        assert asset in sw, f"service worker must precache {asset} for offline use"


def test_conversation_log_is_not_a_live_region(client):
    html = client.get("/").get_data(as_text=True)
    assert 'id="chat-log" role="log"' in html and 'aria-live="off"' in html
