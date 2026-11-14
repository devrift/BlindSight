import requests
import json
import base64
import time

def run_single_suite(run_num):
    print(f"\n==================== TEST RUN {run_num}/5 ====================")
    t0 = time.time()

    # 1. Vite server & Web Companion
    r = requests.get("http://localhost:3000/", timeout=5)
    assert r.status_code == 200, f"Vite server status: {r.status_code}"
    print(f"[Run {run_num}] [PASS] 1. Web companion UI running (HTTP 200)")

    # 2. Testing APK MIME & Header verification
    r = requests.head("http://localhost:3000/BlindSight-testing.apk", timeout=5)
    assert r.status_code == 200, f"APK status: {r.status_code}"
    ctype = r.headers.get("Content-Type", "")
    cdisp = r.headers.get("Content-Disposition", "")
    assert "application/vnd.android.package-archive" in ctype, f"Invalid Content-Type: {ctype}"
    assert "BlindSight-testing.apk" in cdisp, f"Invalid Content-Disposition: {cdisp}"
    size_mb = int(r.headers.get("Content-Length", 0)) / (1024 * 1024)
    assert size_mb > 20, f"APK file too small: {size_mb} MB"
    print(f"[Run {run_num}] [PASS] 2. Testing APK headers valid ({size_mb:.2f} MB, {ctype}, {cdisp})")

    # 3. Edge server health
    r = requests.get("http://localhost:5000/health", timeout=5)
    assert r.status_code == 200, f"Edge health status: {r.status_code}"
    data = r.json()
    assert data.get("status") == "healthy"
    print(f"[Run {run_num}] [PASS] 3. Edge gateway healthy")

    # Helper for chat queries
    def query_chat(prompt, lang="en", use_camera=False, image=None):
        payload = {"prompt": prompt, "lang": lang, "use_camera": use_camera}
        if image:
            payload["image"] = image
        t_start = time.time()
        r = requests.post("http://localhost:5000/api/chat", json=payload, timeout=10)
        elapsed = time.time() - t_start
        assert r.status_code == 200, f"Chat status: {r.status_code}"
        reply = r.json().get("reply", "")
        assert "i hear you ask" not in reply.lower(), f"Robotic fallback: {reply}"
        assert "path forward is monitored and i am ready" not in reply.lower(), f"Robotic fallback: {reply}"
        return reply, elapsed

    # 4. Identity QA
    reply, latency = query_chat("what is your name?")
    assert "blindsight" in reply.lower(), f"No blindsight in reply: {reply}"
    print(f"[Run {run_num}] [PASS] 4. Identity QA passed in {latency:.2f}s: \"{reply[:50]}...\"")

    # 5. Casual conversation
    reply, latency = query_chat("tell me a short joke")
    assert len(reply) > 10
    print(f"[Run {run_num}] [PASS] 5. Casual QA passed in {latency:.2f}s: \"{reply[:50]}...\"")

    # 6. Hindi Conversation
    reply, latency = query_chat("नमस्ते! आप कौन हैं?", lang="hi")
    assert len(reply) > 5
    print(f"[Run {run_num}] [PASS] 6. Hindi conversational QA passed in {latency:.2f}s: \"{reply[:50]}...\"")

    # 7. Math / Reasoning
    reply, latency = query_chat("What is 12 plus 18?")
    assert "30" in reply
    print(f"[Run {run_num}] [PASS] 7. Math QA passed in {latency:.2f}s: \"{reply[:50]}...\"")

    # 8. Multimodal fail-closed camera perception (turned off / empty frame)
    png_b64 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkWPjfDwAEeQHzf12n+gAAAABJRU5ErkJggg=="
    reply, latency = query_chat("describe the scene in front of me", use_camera=True, image=png_b64)
    assert "clock" not in reply.lower()
    assert "10 bje" not in reply.lower()
    print(f"[Run {run_num}] [PASS] 8. Multimodal scene perception honest fail-closed in {latency:.2f}s: \"{reply[:50]}...\"")

    total_time = time.time() - t0
    print(f">>> RUN {run_num}/5 COMPLETED IN {total_time:.2f}s - ALL TESTS PASSED! <<<")

def main():
    print("STARTING 5 CONSECUTIVE SYSTEM VERIFICATION RUNS...")
    start_all = time.time()
    for i in range(1, 6):
        run_single_suite(i)
        time.sleep(0.5)
    print(f"\n========================================================")
    print(f"SUCCESS: ALL 5 RUNS COMPLETED WITH 0 ERRORS IN {time.time() - start_all:.2f}s!")
    print(f"========================================================")

if __name__ == "__main__":
    main()
