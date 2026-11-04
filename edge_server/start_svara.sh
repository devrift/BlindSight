#!/bin/bash
# Run svara-TTS GGUF via llama.cpp server (CPU mode)
# Install llama.cpp: https://github.com/ggml-org/llama.cpp

llama-server \
    -m /home/pi/svara-tts/svara-tts-v1.Q4_K_M.gguf \
    --port 8081 \
    --host 0.0.0.0 \
    -t 4 \
    -c 512 \
    --n-gpu-layers 0
