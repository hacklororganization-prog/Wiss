#!/data/data/com.termux/files/usr/bin/sh
# Termux: keeps the phone awake and restarts the server if it ever stops.
cd "$(dirname "$0")"
command -v termux-wake-lock >/dev/null && termux-wake-lock
while true; do
  node app.js
  echo "server stopped, restarting in 5 s (Ctrl+C twice to quit)"
  sleep 5
done
