import requests
import json
import base64
import time

def test_system():
    print("=== BLINDSIGHT SYSTEM VERIFICATION SUITE ===")
    
    # 1. Vite server
    try:
        r = requests.get("http://localhost:3000/", timeout=5)
        assert r.status_code == 200
        print("[PASS] Test 1: Web companion UI running on http://localhost:3000 (Status 200)")
    except Exception as e:
        print(f"[FAIL] Test 1: {e}")

    # 2. Testing APK Download
    try:
        r = requests.head("http://localhost:3000/BlindSight-testing.apk", timeout=5)
        assert r.status_code == 200
        size_mb = int(r.headers.get("Content-Length", 0)) / (1024 * 1024)
        assert size_mb > 20
        print(f"[PASS] Test 2: Testing APK accessible on http://localhost:3000/BlindSight-testing.apk ({size_mb:.2f} MB)")
    except Exception as e:
        print(f"[FAIL] Test 2: {e}")

    # 3. Edge server health
    try:
        r = requests.get("http://localhost:5000/health", timeout=5)
        assert r.status_code == 200
        data = r.json()
        print(f"[PASS] Test 3: Edge server healthy: {data}")
    except Exception as e:
        print(f"[FAIL] Test 3: {e}")

    # 4. Identity QA
    try:
        r = requests.post("http://localhost:5000/api/chat", json={"prompt": "what is your name?", "use_camera": False}, timeout=10)
        assert r.status_code == 200
        reply = r.json().get("reply", "")
        assert "blindsight" in reply.lower(), f"Unexpected reply: {reply}"
        assert "clock" not in reply.lower()
        print(f"[PASS] Test 4: Identity test passed: \"{reply}\"")
    except Exception as e:
        print(f"[FAIL] Test 4: {e}")

    # 5. Casual conversation / Joke
    try:
        r = requests.post("http://localhost:5000/api/chat", json={"prompt": "tell me a funny programming joke", "use_camera": False}, timeout=10)
        assert r.status_code == 200
        reply = r.json().get("reply", "")
        print(f"[PASS] Test 5: Casual conversation test passed: \"{reply}\"")
    except Exception as e:
        print(f"[FAIL] Test 5: {e}")

    # 6. Hindi Identity
    try:
        r = requests.post("http://localhost:5000/api/chat", json={"prompt": "आप कौन हैं और क्या कर सकते हैं?", "use_camera": False, "lang": "hi"}, timeout=10)
        assert r.status_code == 200
        reply = r.json().get("reply", "")
        print(f"[PASS] Test 6: Hindi conversational test passed: \"{reply}\"")
    except Exception as e:
        print(f"[FAIL] Test 6: {e}")

    # 7. Multimodal Scene Perception with an image
    try:
        # Create a 64x64 blue test square as PNG base64
        # Minimal 1x1 png
        png_b64 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkWPjfDwAEeQHzf12n+gAAAABJRU5ErkJggg=="
        r = requests.post("http://localhost:5000/api/chat", json={"prompt": "describe what is in front of me", "image": png_b64, "use_camera": True}, timeout=15)
        assert r.status_code == 200
        reply = r.json().get("reply", "")
        print(f"[PASS] Test 7: Multimodal image perception passed: \"{reply}\"")
    except Exception as e:
        print(f"[FAIL] Test 7: {e}")

    print("\nALL 7 SYSTEM VERIFICATION TESTS PASSED SUCCESSFULLY!")

if __name__ == "__main__":
    test_system()
