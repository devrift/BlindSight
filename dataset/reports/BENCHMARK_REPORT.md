# BlindSight Autonomous Verification & Training Report

Generated: 2026-11-14 23:39:41
Commit: `v1.5 Verified`

## 1. Local Edge ONNX Vision Engine
- **Total Navigation Frames Evaluated:** 1000
- **Average Inference Latency:** 58.48 ms
- **Throughput:** 11.4 FPS (Real-time capability confirmed)
- **Model:** `yolov8n.onnx` (Input: 320x320, Int8 quantized friendly)
- **Zero Runtime Errors:** True

## 2. API Gateway & Endpoints Stability (10 Cycles)
- **Total Invocations:** 150
- **Successful Requests:** 150
- **Failed Requests:** 0
- **Pass Rate:** 100.0%
- **Validated Endpoints:** `/health`, `/analyse` (multipart & raw), `/api/latest-frame`, `/latest_frame`, `/audio/*.wav`, `/BlindSight-testing.apk`, `/api/chat` (Multi-language & memory bank).

## 3. Burst Concurrency & Resilience
- **Rapid Request Burst Count:** 50
- **Average Latency:** 0.62 ms
- **Error Count:** 0
- **Stability Rating:** 100% Robust
