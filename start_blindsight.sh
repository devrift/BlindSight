#!/usr/bin/env bash
# BlindSight one-command starter for Linux / Raspberry Pi.
# Usage:  ./start_blindsight.sh
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -x .venv/bin/python ]; then
  echo "First run: creating a private Python environment, please wait..."
  python3 -m venv .venv
fi

# HTTPS for phone use: browsers only allow the mic on localhost or HTTPS.
if [ ! -f .tls/cert.pem ] && command -v openssl >/dev/null 2>&1; then
  mkdir -p .tls
  openssl req -x509 -newkey rsa:2048 -keyout .tls/key.pem -out .tls/cert.pem \
    -days 365 -nodes -subj "/CN=blindsight.local" 2>/dev/null || true
fi
if [ -f .tls/cert.pem ]; then
  export BLINDSIGHT_TLS=".tls/cert.pem,.tls/key.pem"
  echo "HTTPS on: phones can use https://<this-ip>:5000 (accept the certificate once)."
fi

echo "Installing or updating the requirements..."
.venv/bin/python -m pip install -q -r edge_server/requirements.txt

IP=$(hostname -I 2>/dev/null | awk '{print $1}' || true)
echo
echo "  BlindSight is starting on  http://localhost:5000  (or https if shown above)"
[ -n "${IP}" ] && echo "  Phone on the same Wi-Fi:   http://${IP}:5000"
echo "  Keep this terminal open while using the app. Ctrl+C stops it."
echo
exec .venv/bin/python edge_server/server.py
