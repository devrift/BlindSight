#!/usr/bin/env bash
# BlindSight one-command starter for Linux / Raspberry Pi.
# Usage:  ./start_blindsight.sh
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -x .venv/bin/python ]; then
  echo "First run: creating a private Python environment, please wait..."
  python3 -m venv .venv
fi

echo "Installing or updating the requirements..."
.venv/bin/python -m pip install -q -r edge_server/requirements.txt

IP=$(hostname -I 2>/dev/null | awk '{print $1}' || true)
echo
echo "  BlindSight is starting on  http://localhost:5000"
[ -n "${IP}" ] && echo "  Phone on the same Wi-Fi:   http://${IP}:5000"
echo "  Keep this terminal open while using the app. Ctrl+C stops it."
echo
exec .venv/bin/python edge_server/server.py
