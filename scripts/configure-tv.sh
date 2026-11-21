#!/usr/bin/env bash
set -euo pipefail

# Run from any directory: scripts/configure-tv.sh [device-ip] [adb-port]
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEVICE_IP="${1:-192.168.6.208}"
ADB_PORT="${2:-5555}"
ADB_BIN="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
PACKAGE="me.lsong.mytv"
ENDPOINT="http://${DEVICE_IP}:10481/api/settings"
PAYLOAD="$ROOT/scripts/tv-sources.json"

if [[ ! -x "$ADB_BIN" ]]; then
  ADB_BIN="$(command -v adb || true)"
fi
if [[ -z "$ADB_BIN" || ! -x "$ADB_BIN" ]]; then
  echo "adb not found. Set ADB=/path/to/adb and retry." >&2
  exit 1
fi

SERIAL="${DEVICE_IP}:${ADB_PORT}"
echo "Connecting to $SERIAL..."
"$ADB_BIN" connect "$SERIAL" >/dev/null
STATE="$("$ADB_BIN" -s "$SERIAL" get-state 2>/dev/null || true)"
if [[ "$STATE" != "device" ]]; then
  echo "Device is not authorized. Accept the wireless debugging prompt on the TV, then retry." >&2
  exit 2
fi

APK="${APK:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
if [[ -n "${1:-}" && -f "$1" ]]; then APK="$1"; fi
if [[ -f "$APK" ]]; then
  echo "Installing $APK..."
  if ! "$ADB_BIN" -s "$SERIAL" install -r -d "$APK"; then
    echo "Install failed (often because release/debug signatures differ). Uninstalling $PACKAGE..."
    "$ADB_BIN" -s "$SERIAL" uninstall "$PACKAGE"
    "$ADB_BIN" -s "$SERIAL" install "$APK"
  fi
  "$ADB_BIN" -s "$SERIAL" shell am start -n "$PACKAGE/.MainActivity" >/dev/null || true
else
  echo "No APK found; applying configuration to the installed app. Set APK=/path/to/app.apk to install one."
fi

python3 - "$PAYLOAD" "$ENDPOINT" <<'PY'
import json, sys, time, urllib.error, urllib.request
payload_path, endpoint = sys.argv[1:]
payload = json.load(open(payload_path, encoding="utf-8"))
body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
last = None
for attempt in range(20):
    try:
        req = urllib.request.Request(endpoint, data=body, headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=5) as response:
            result = json.load(response)
        if not result.get("ok"):
            raise RuntimeError("TV rejected configuration")
        with urllib.request.urlopen(endpoint, timeout=5) as response:
            actual = json.load(response)
        if actual.get("sources") != payload.get("sources") or actual.get("epgUrls") != payload.get("epgUrls"):
            raise RuntimeError("Read-back does not match the saved configuration")
        kinds = {kind: sum(s["type"] == kind for s in actual["sources"]) for kind in ("IPTV", "VOD")}
        enabled = sum(bool(s["isEnabled"]) for s in actual["sources"])
        print(f"Configuration verified: {len(actual['sources'])} sources, {kinds['IPTV']} IPTV, {kinds['VOD']} VOD, {enabled} enabled, {len(actual.get('epgUrls', []))} custom EPG URLs.")
        break
    except Exception as error:
        last = error
        time.sleep(2)
else:
    raise SystemExit(f"Could not apply configuration at {endpoint}: {last}. Ensure OhMyTV is running and reachable.")
PY
