"""Android shell packaging contract.

The APK is a thin WebView wrapper; these structural tests keep it honest:
the permissions a voice-first app needs, the scoped self-signed-cert trust,
the cloud build that produces the downloadable artifact, and the plain-English
install doc a non-technical builder follows.
"""
from __future__ import annotations

from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
ANDROID = REPO_ROOT / "android"


def _read(rel: str) -> str:
    return (ANDROID / rel).read_text(encoding="utf-8")


def test_manifest_declares_voice_permissions():
    manifest = _read("app/src/main/AndroidManifest.xml")
    assert "android.permission.RECORD_AUDIO" in manifest
    assert "android.permission.INTERNET" in manifest
    assert "usesCleartextTraffic" in manifest  # plain-http laptop gateways


def test_shell_trusts_only_the_saved_gateway_host():
    activity = _read("app/src/main/java/app/blindsight/companion/MainActivity.kt")
    assert "onReceivedSslError" in activity
    assert "gatewayHost()" in activity
    assert "handler.proceed()" in activity
    # fail closed for every other host
    assert "super.onReceivedSslError" in activity


def test_shell_handles_mic_permission_natively():
    activity = _read("app/src/main/java/app/blindsight/companion/MainActivity.kt")
    assert "onPermissionRequest" in activity
    assert "RESOURCE_AUDIO_CAPTURE" in activity
    assert "mediaPlaybackRequiresUserGesture = false" in activity


def test_shell_has_retry_screen_and_gateway_editor():
    activity = _read("app/src/main/java/app/blindsight/companion/MainActivity.kt")
    assert "Gateway not reachable" in activity
    assert "changeGateway" in activity
    assert "gateway_url" in activity


def test_ci_builds_and_uploads_apk():
    wf = (REPO_ROOT / ".github" / "workflows" / "android.yml").read_text(encoding="utf-8")
    assert "assembleDebug" in wf
    assert "upload-artifact" in wf
    assert "app-debug.apk" in wf


def test_install_doc_exists_and_is_plain():
    doc = (REPO_ROOT / "docs" / "ANDROID_APP.md").read_text(encoding="utf-8")
    assert "Actions" in doc and "Allow from this source" in doc
    assert "does NOT do" in doc  # honest limits section
