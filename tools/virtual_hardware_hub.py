#!/usr/bin/env python3
"""BlindSight Virtual Hardware Hub.

Simulates:
1. Raspberry Pi Open.Jarvis Edge Gateway (Port 5000):
   - GET /health
   - GET /api/health
   - GET /api/status
   - GET /api/latest-frame
   - GET /latest_frame
   - GET /capture
   - POST /analyse
   - POST /api/upload-frame
   - POST /api/chat
   - POST /chat
   - GET /audio/<filename>

2. ESP32-CAM Standalone Nodes (Port 80 and Port 8080):
   - GET /capture
   - GET /
   - GET /status
   - GET /health

Permits full-stack end-to-end integration testing against real working virtual IPs (127.0.0.1, 127.0.4.1, 127.0.43.15, LAN IP).
"""
from __future__ import annotations

import io
import sys
import time
import socket
import threading
from pathlib import Path
from http.server import HTTPServer, BaseHTTPRequestHandler

def make_minimal_jpeg() -> bytes:
    test_file = Path(__file__).resolve().parent.parent / "runtime" / "test_frame.jpg"
    if test_file.is_file():
        try:
            return test_file.read_bytes()
        except Exception:
            pass

    edge_latest = Path(__file__).resolve().parent.parent / "edge_server" / "runtime" / "latest.jpg"
    if edge_latest.is_file():
        try:
            return edge_latest.read_bytes()
        except Exception:
            pass

    return bytes([
        0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
        0x01, 0x01, 0x00, 0x48, 0x00, 0x48, 0x00, 0x00, 0xFF, 0xDB, 0x00, 0x43,
        0x00, 0x08, 0x06, 0x06, 0x07, 0x06, 0x05, 0x08, 0x07, 0x07, 0x07, 0x09,
        0x09, 0x08, 0x0A, 0x0C, 0x14, 0x0D, 0x0C, 0x0B, 0x0B, 0x0C, 0x19, 0x12,
        0x13, 0x0F, 0x14, 0x1D, 0x1A, 0x1F, 0x1E, 0x1D, 0x1A, 0x1C, 0x1C, 0x20,
        0x24, 0x2E, 0x27, 0x20, 0x22, 0x2C, 0x23, 0x1C, 0x1C, 0x28, 0x37, 0x29,
        0x2C, 0x30, 0x31, 0x34, 0x34, 0x34, 0x1F, 0x27, 0x39, 0x3D, 0x38, 0x32,
        0x3C, 0x2E, 0x33, 0x34, 0x32, 0xFF, 0xC0, 0x00, 0x0B, 0x08, 0x00, 0x08,
        0x00, 0x08, 0x01, 0x01, 0x11, 0x00, 0xFF, 0xC4, 0x00, 0x1F, 0x00, 0x00,
        0x01, 0x05, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
        0x09, 0x0A, 0x0B, 0xFF, 0xDA, 0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3F,
        0x00, 0xBF, 0x80, 0xFF, 0xD9
    ])

SAMPLE_FRAME = make_minimal_jpeg()

class VirtualPiGatewayHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, User-Agent")
        self.end_headers()

    def do_GET(self):
        if self.path in ("/health", "/api/health", "/api/status"):
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            payload = (
                b'{"status":"healthy","service":"BlindSight Virtual Edge Gateway",'
                b'"device":"Raspberry Pi 3B+ / 4B","yolo_model":"ready","fps":15}'
            )
            self.wfile.write(payload)
        elif self.path in ("/api/latest-frame", "/latest_frame", "/capture"):
            self.send_response(200)
            self.send_header("Content-Type", "image/jpeg")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Content-Length", str(len(SAMPLE_FRAME)))
            self.end_headers()
            self.wfile.write(SAMPLE_FRAME)
        elif self.path.startswith("/audio/"):
            self.send_response(200)
            self.send_header("Content-Type", "audio/wav")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(b"RIFF$...\x00\x00\x00\x00WAVEfmt \x10\x00\x00\x00\x01\x00\x01\x00")
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        if self.path in ("/analyse", "/api/upload-frame"):
            length = int(self.headers.get("Content-Length", 0))
            if length > 0:
                _ = self.rfile.read(length)
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(
                b'{"success":true,"instruction":"Path ahead appears clear. Proceed forward.",'
                b'"detections":0,"audio_url":"/audio/clear.wav"}'
            )
        elif self.path in ("/api/chat", "/chat"):
            length = int(self.headers.get("Content-Length", 0))
            if length > 0:
                _ = self.rfile.read(length)
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(
                b'{"reply":"I can see your path ahead clearly. Walking forward is safe.",'
                b'"ok":true,"image_attached":true}'
            )
        else:
            self.send_response(404)
            self.end_headers()

class VirtualEsp32CamHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, User-Agent")
        self.end_headers()

    def do_GET(self):
        if self.path in ("/capture", "/"):
            self.send_response(200)
            self.send_header("Content-Type", "image/jpeg")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Content-Length", str(len(SAMPLE_FRAME)))
            self.end_headers()
            self.wfile.write(SAMPLE_FRAME)
        elif self.path in ("/status", "/health"):
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(b'{"camera":"OV2640","resolution":"QVGA","status":"active"}')
        else:
            self.send_response(404)
            self.end_headers()

def get_local_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
    except Exception:
        ip = "127.0.0.1"
    finally:
        s.close()
    return ip

def run_servers():
    local_ip = get_local_ip()
    print("Starting BlindSight Virtual Hardware Hub...")
    print(f"Detected Host IP: {local_ip}")

    # 1. Start Virtual Pi on port 5000
    try:
        pi_server = HTTPServer(("0.0.0.0", 5000), VirtualPiGatewayHandler)
        pi_thread = threading.Thread(target=pi_server.serve_forever, daemon=True)
        pi_thread.start()
        print(f"Virtual Raspberry Pi Server running on port 5000:")
        print(f"  - http://127.0.0.1:5000/health")
        print(f"  - http://127.0.4.1:5000/health")
        print(f"  - http://{local_ip}:5000/health")
    except Exception as e:
        print(f"Port 5000 busy or error: {e}")

    # 2. Start Virtual ESP32-CAM on port 8080
    try:
        cam_8080 = HTTPServer(("0.0.0.0", 8080), VirtualEsp32CamHandler)
        cam_8080_thread = threading.Thread(target=cam_8080.serve_forever, daemon=True)
        cam_8080_thread.start()
        print(f"Virtual ESP32-CAM Server running on port 8080:")
        print(f"  - http://127.0.0.1:8080/capture")
        print(f"  - http://127.0.43.50:8080/capture")
        print(f"  - http://{local_ip}:8080/capture")
    except Exception as e:
        print(f"Port 8080 busy or error: {e}")

    # 3. Start Virtual ESP32-CAM on standard port 80 (if permitted)
    try:
        cam_80 = HTTPServer(("0.0.0.0", 80), VirtualEsp32CamHandler)
        cam_80_thread = threading.Thread(target=cam_80.serve_forever, daemon=True)
        cam_80_thread.start()
        print(f"Virtual ESP32-CAM Server running on port 80 (standard HTTP):")
        print(f"  - http://127.0.0.1/capture")
        print(f"  - http://127.0.4.1/capture")
        print(f"  - http://{local_ip}/capture")
    except Exception as e:
        print(f"Port 80 optional bind note: {e}")

    print("Virtual Hardware Hub ready for continuous testing.")
    while True:
        time.sleep(1)

if __name__ == "__main__":
    run_servers()
