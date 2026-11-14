import requests
import json
import base64
import time
import sys

def query_chat(prompt, lang="en", image=None):
    payload = {"prompt": prompt, "lang": lang}
    if image:
        payload["image"] = image
    t_start = time.time()
    r = requests.post("http://localhost:5000/api/chat", json=payload, timeout=10)
    elapsed = time.time() - t_start
    assert r.status_code == 200, f"Chat status: {r.status_code} - {r.text}"
    reply = r.json().get("reply", "")
    assert "i hear you ask" not in reply.lower(), f"Robotic fallback detected: {reply}"
    assert "path forward is monitored and i am ready" not in reply.lower(), f"Robotic fallback detected: {reply}"
    assert "i heard:" not in reply.lower(), f"Robotic 'I heard:' fallback: {reply}"
    return reply, elapsed

def run_test_round(cycle_name, round_num):
    print(f"\n--- [{cycle_name}] Round {round_num}/10 ---")
    t0 = time.time()

    # 1. Identity QA - standard
    rep, lat = query_chat("what is your name?")
    assert "blindsight" in rep.lower(), f"Failed identity: {rep}"
    assert "camera" not in rep.lower() and "front" not in rep.lower(), f"Confused identity with scene: {rep}"

    # 2. Identity QA - variation
    rep, lat = query_chat("who are you?")
    assert "blindsight" in rep.lower() or "assistant" in rep.lower(), f"Failed identity: {rep}"
    assert "camera" not in rep.lower(), f"Confused who are you with scene: {rep}"

    # 3. Identity QA with uploaded camera image attached (MUST NOT trigger scene description!)
    tiny_png = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkWPjfDwAEeQHzf12n+gAAAABJRU5ErkJggg=="
    rep, lat = query_chat("what is your name?", image=tiny_png)
    assert "blindsight" in rep.lower(), f"Image confused identity: {rep}"
    assert "camera is turned off" not in rep.lower(), f"Image hijacked identity into scene query: {rep}"

    # 4. Math QA - Addition
    rep, lat = query_chat("What is 12 plus 18?")
    assert "30" in rep, f"Math addition failed: {rep}"

    # 5. Math QA - Multiplication
    rep, lat = query_chat("calculate 25 times 4")
    assert "100" in rep, f"Math multiplication failed: {rep}"

    # 6. Humor
    rep, lat = query_chat("tell me a funny joke")
    assert len(rep) > 15, f"Joke too short: {rep}"
    assert "camera" not in rep.lower(), f"Joke hijacked: {rep}"

    # 7. Hindi Conversation
    rep, lat = query_chat("नमस्ते! आप कौन हैं?", lang="hi")
    assert len(rep) > 5, f"Hindi reply too short: {rep}"
    assert "ब्लाइंडसाइट" in rep or "सहायक" in rep or "साथी" in rep, f"Hindi identity missing: {rep}"

    # 8. Casual companionship
    rep, lat = query_chat("how are you doing today?")
    assert len(rep) > 10, f"Casual chitchat failed: {rep}"
    assert "i heard" not in rep.lower()

    # 9. Multimodal fail-closed camera perception (explicit scene query with empty/dummy frame)
    rep, lat = query_chat("describe the scene in front of me", image=tiny_png)
    assert "10 bje" not in rep.lower()
    assert "clock" not in rep.lower()
    assert ("turned off" in rep.lower() or "not sending frames" in rep.lower() or "clear" in rep.lower() or "obstructed" in rep.lower() or "wall" in rep.lower() or "blocked" in rep.lower()), f"Unsafe hallucination: {rep}"

    # 10. Fast System Action - Time
    rep, lat = query_chat("what time is it?")
    assert ("time is" in rep.lower() or "m." in rep.lower() or ":" in rep), f"Time failed: {rep}"

    elapsed = time.time() - t0
    print(f"[{cycle_name}] Round {round_num}/10 PASSED in {elapsed:.2f}s (10/10 assertions OK)")

def main():
    print("=================================================================")
    print("STARTING 3 CYCLES OF 10 COMPREHENSIVE TRAINING & TEST RUNS (30 TOTAL)")
    print("=================================================================")
    total_start = time.time()

    for cycle in ["CYCLE 1", "CYCLE 2", "CYCLE 3"]:
        print(f"\n==================== {cycle} (10 RUNS) ====================")
        c_start = time.time()
        for r in range(1, 11):
            run_test_round(cycle, r)
            time.sleep(0.1)
        print(f"=== {cycle} COMPLETED SUCCESSFULLY IN {time.time() - c_start:.2f}s ===")

    total_time = time.time() - total_start
    print(f"\n=================================================================")
    print(f"ALL 30 VERIFICATION ROUNDS (3 CYCLES x 10) PASSED WITH 0 ERRORS in {total_time:.2f}s!")
    print("=================================================================")

if __name__ == "__main__":
    main()
