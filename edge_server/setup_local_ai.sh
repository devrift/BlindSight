#!/usr/bin/env bash
# ==============================================================================
# BlindSight Local Edge AI - Permanent Free Setup for Raspberry Pi 3B+ / PC
# 100% Free, Local, No API Keys, No Subscriptions
# ==============================================================================
set -e

echo "=== [1/5] Updating packages and installing system tools ==="
sudo apt-get update
sudo apt-get install -y curl wget git python3-pip python3-venv libasound2-dev espeak-ng

echo "=== [2/5] Setting up Python Virtual Environment ==="
mkdir -p ~/BlindSight
cd "$(dirname "$0")"
python3 -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt

echo "=== [3/5] Installing Ollama & Pulling llama3.2:1b (The Local Brain) ==="
if ! command -v ollama &> /dev/null; then
    curl -fsSL https://ollama.com/install.sh | sh
fi
echo "Starting Ollama and pulling llama3.2:1b (~770MB)..."
sudo systemctl enable ollama || true
sudo systemctl start ollama || true
sleep 3
ollama pull llama3.2:1b

echo "=== [4/5] Downloading Piper Neural Voices (Hindi + English) ==="
mkdir -p piper_voices
cd piper_voices
python3 -m piper.download_voices hi_IN-pratham-medium || true
python3 -m piper.download_voices hi_IN-priyamvada-medium || true
python3 -m piper.download_voices en_US-lessac-medium || true
cd ..

echo "=== [5/5] Downloading svara-TTS GGUF (Indic Languages) ==="
mkdir -p ~/svara-tts
if [ ! -f ~/svara-tts/svara-tts-v1.Q4_K_M.gguf ]; then
    echo "Downloading svara-TTS GGUF (~2GB)..."
    wget -c https://huggingface.co/mradermacher/svara-tts-v1-GGUF/resolve/main/svara-tts-v1.Q4_K_M.gguf -O ~/svara-tts/svara-tts-v1.Q4_K_M.gguf
fi

echo "=== Setup Complete! ==="
echo "To run the BlindSight Gateway manually:"
echo "    source .venv/bin/activate && python server.py"
echo "Or start the background service:"
echo "    sudo systemctl restart blindsight-voice"
