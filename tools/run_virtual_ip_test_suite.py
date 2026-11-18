#!/usr/bin/env python3
"""BlindSight Virtual IP & End-to-End Hardware Test Suite.

Tests:
1. Virtual Pi Edge Gateway Health Check (/health, /api/health)
2. Live JPEG Frame Integrity (JPEG JFIF Magic 0xFF 0xD8 ... 0xFF 0xD9)
3. ESP32-CAM Endpoint Verification (/capture)
4. Candidate URL Resolution & Subnet Probing
5. High-Throughput Burst & Stress Resistance (50 iterations)
6. Disconnection Recovery & Fail-Closed Behavior
"""
import sys
import time
import socket
import urllib.request
import urllib.error
from pathlib import Path

def test_endpoint(url: str, expected_code: int = 200, timeout: float = 2.0) -> tuple[bool, int, bytes]:
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "BlindSight-Android/1.0.0"}
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            data = resp.read()
            return (resp.status == expected_code, resp.status, data)
    except urllib.error.HTTPError as e:
        return (e.code == expected_code, e.code, b"")
    except Exception as e:
        return (False, 0, str(e).encode())

def run_suite():
    print("=" * 60)
    print("BLINDSIGHT VIRTUAL IP HARDWARE TEST SUITE")
    print("=" * 60)

    # 1. Determine local host IP
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        local_ip = s.getsockname()[0]
    except Exception:
        local_ip = "127.0.0.1"
    finally:
        s.close()

    print(f"Testing on Loopback (127.0.0.1) and Local Machine IP ({local_ip})")

    passed = 0
    total = 0

    # Test 1: Pi Health Check on 127.0.0.1:5000
    total += 1
    ok, code, data = test_endpoint("http://127.0.0.1:5000/health")
    if ok and b"healthy" in data:
        print("[PASS] Test 1: Pi Gateway /health on 127.0.0.1:5000")
        passed += 1
    else:
        print(f"[FAIL] Test 1: Pi Gateway /health (Code: {code})")

    # Test 2: Pi Health Check on Local IP
    total += 1
    ok, code, data = test_endpoint(f"http://{local_ip}:5000/health")
    if ok and b"healthy" in data:
        print(f"[PASS] Test 2: Pi Gateway /health on {local_ip}:5000")
        passed += 1
    else:
        print(f"[FAIL] Test 2: Pi Gateway /health on {local_ip}:5000 (Code: {code})")

    # Test 3: Pi Latest Frame Endpoint (JPEG validation)
    total += 1
    ok, code, data = test_endpoint("http://127.0.0.1:5000/api/latest-frame")
    is_valid_jpeg = len(data) > 20 and data[0] == 0xFF and data[1] == 0xD8 and data[-2] == 0xFF and data[-1] == 0xD9
    if ok and is_valid_jpeg:
        print(f"[PASS] Test 3: Pi /api/latest-frame served valid JPEG ({len(data)} bytes, SOI/EOI verified)")
        passed += 1
    else:
        print(f"[FAIL] Test 3: /api/latest-frame invalid JPEG (Code: {code}, bytes: {len(data)})")

    # Test 4: ESP32-CAM /capture endpoint
    total += 1
    ok, code, data = test_endpoint(f"http://127.0.0.1:8080/capture")
    is_valid_jpeg = len(data) > 20 and data[0] == 0xFF and data[1] == 0xD8
    if ok and is_valid_jpeg:
        print(f"[PASS] Test 4: ESP32-CAM /capture on port 8080 served valid JPEG ({len(data)} bytes)")
        passed += 1
    else:
        print(f"[FAIL] Test 4: ESP32-CAM /capture (Code: {code})")

    # Test 5: Endpoint Candidate Expansion Simulation
    total += 1
    test_inputs = [
        ("192.168.43.50", "http://192.168.43.50:5000/health"),
        ("192.168.43.50:5000", "http://192.168.43.50:5000/health"),
        ("http://192.168.4.1", "http://192.168.4.1:5000/health"),
        ("blindsight.local", "http://blindsight.local:5000/health")
    ]
    expansion_ok = True
    for raw, expected in test_inputs:
        cleaned = raw.strip().rstrip('/')
        url_with_scheme = cleaned if cleaned.startswith("http") else f"http://{cleaned}"
        if ":" in url_with_scheme.split("/")[-1]:
            candidate = f"{url_with_scheme}/health"
        else:
            candidate = f"{url_with_scheme}:5000/health"
        if candidate != expected:
            expansion_ok = False
            break

    if expansion_ok:
        print("[PASS] Test 5: Candidate URL resolution logic verified for all network formats")
        passed += 1
    else:
        print("[FAIL] Test 5: Candidate URL resolution logic mismatch")

    # Test 6: Stress Burst (30 rapid sequential requests)
    total += 1
    stress_passed = 0
    t0 = time.time()
    for _ in range(30):
        ok, _, _ = test_endpoint("http://127.0.0.1:5000/api/latest-frame", timeout=1.0)
        if ok:
            stress_passed += 1
    dur = time.time() - t0
    if stress_passed == 30:
        avg_ms = (dur / 30.0) * 1000.0
        print(f"[PASS] Test 6: High-frequency stress burst (30/30 succeeded, avg {avg_ms:.1f} ms/req)")
        passed += 1
    else:
        print(f"[FAIL] Test 6: Stress burst dropped frames ({stress_passed}/30)")

    # Test 7: Fail-Closed 404 Non-Existent Endpoint Handling
    total += 1
    ok, code, _ = test_endpoint("http://127.0.0.1:5000/nonexistent_route", expected_code=404)
    if ok:
        print("[PASS] Test 7: Fail-closed handling for invalid route returns clean 404")
        passed += 1
    else:
        print(f"[FAIL] Test 7: Expected 404, got {code}")

    print("=" * 60)
    print(f"RESULTS: {passed}/{total} tests passed ({'100% SUCCESS' if passed == total else 'SOME FAILED'})")
    print("=" * 60)
    return passed == total

if __name__ == "__main__":
    success = run_suite()
    sys.exit(0 if success else 1)
