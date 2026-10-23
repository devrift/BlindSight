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

if ! command -v espeak-ng >/dev/null 2>&1; then
  echo "Tip: 'sudo apt install espeak-ng' adds offline spoken answers (neural voices need internet)."
fi

IP=$(hostname -I 2>/dev/null | awk '{print $1}' || true)
SCHEME="http"
if [ -n "${BLINDSIGHT_TLS:-}" ]; then SCHEME="https"; fi
echo
echo "  BlindSight is starting on  http://localhost:5000  (or https if shown above)"
if [ -n "${IP}" ]; then
  echo "  Phone on the same Wi-Fi:   ${SCHEME}://${IP}:5000"
  if [ "${SCHEME}" = "https" ]; then
    echo "  Phone tip: accept the certificate warning once (Android: Advanced >"
    echo "  Continue; iPhone: Show Details > visit this website)."
  else
    echo "  Note: phone microphones need HTTPS. Install openssl and re-run to enable it."
  fi
fi
echo "  Keep this terminal open while using the app. Ctrl+C stops it."
echo
exec .venv/bin/python edge_server/server.py
