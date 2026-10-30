#!/data/data/com.termux/files/usr/bin/sh
# Termux: keeps the phone awake and restarts the server if it crashes.
# Ctrl+C stops everything (no restart). If another copy is already running, it says so and stops.
cd "$(dirname "$0")"
command -v termux-wake-lock >/dev/null && termux-wake-lock
trap 'echo; echo "ZINGO server stopped."; command -v termux-wake-unlock >/dev/null && termux-wake-unlock; exit 0' INT TERM
while true; do
  node app.js
  code=$?
  if [ "$code" = "0" ] || [ "$code" = "3" ] || [ "$code" = "130" ]; then break; fi
  echo "server stopped unexpectedly (code $code), restarting in 5 s (Ctrl+C to quit)"
  sleep 5
done
command -v termux-wake-unlock >/dev/null && termux-wake-unlock
