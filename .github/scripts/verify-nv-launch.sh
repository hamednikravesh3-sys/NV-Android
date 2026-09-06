#!/usr/bin/env bash
set -euo pipefail

PACKAGE="ir.nv.navigation.debug"
ACTIVITY="ir.nv.navigation.MainActivity"
MAP_PATH="/sdcard/Android/data/$PACKAGE/files/Download/Iran map.nvpack"

adb wait-for-device
adb shell settings put global hide_error_dialogs 1 || true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION
adb shell pm grant "$PACKAGE" android.permission.ACCESS_COARSE_LOCATION || true
adb logcat -c
adb shell am force-stop "$PACKAGE"
adb shell am start -n "$PACKAGE/$ACTIVITY"

function dump_ui_raw() {
  adb shell uiautomator dump /sdcard/nv-ui.xml >/dev/null 2>&1 || true
  adb shell cat /sdcard/nv-ui.xml > nv-ui.xml 2>/dev/null || true
}

function tap_text_if_present() {
  local wanted="$1"
  dump_ui_raw
  local coords
  coords="$(python - "$wanted" <<'PY'
import re,sys,xml.etree.ElementTree as ET
wanted=sys.argv[1]
try: root=ET.parse('nv-ui.xml').getroot()
except Exception: sys.exit(2)
for n in root.iter('node'):
    if n.attrib.get('text') == wanted:
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); sys.exit(0)
sys.exit(3)
PY
)" || return 1
  adb shell input tap $coords
  return 0
}

function clear_emulator_dialogs() {
  for _ in 1 2 3; do
    if tap_text_if_present "Wait"; then sleep 2; continue; fi
    if tap_text_if_present "صبر کردن"; then sleep 2; continue; fi
    break
  done
}

clear_emulator_dialogs

app_alive=0
for _ in $(seq 1 30); do
  clear_emulator_dialogs
  if adb shell pidof "$PACKAGE" >/dev/null 2>&1; then app_alive=1; break; fi
  sleep 2
done

first_frame=0
for _ in $(seq 1 60); do
  clear_emulator_dialogs
  if adb logcat -d | grep -F "Displayed $PACKAGE/$ACTIVITY" >/dev/null; then first_frame=1; break; fi
  if ! adb shell pidof "$PACKAGE" >/dev/null 2>&1; then break; fi
  sleep 2
done

function dump_ui() {
  clear_emulator_dialogs
  dump_ui_raw
}

function tap_desc() {
  local desc="$1"
  dump_ui
  local coords
  coords="$(python - "$desc" <<'PY'
import re, sys, xml.etree.ElementTree as ET
name=sys.argv[1]
try: root=ET.parse('nv-ui.xml').getroot()
except Exception: sys.exit(2)
for n in root.iter('node'):
    if n.attrib.get('content-desc') == name:
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); sys.exit(0)
sys.exit(3)
PY
)" || return 1
  adb shell input tap $coords
}

# Wait until the actual NV Compose controls are visible, not a transient System UI ANR dialog.
ui_ready=0
for _ in $(seq 1 45); do
  dump_ui
  if grep -q 'content-desc="جستجو"' nv-ui.xml && grep -q 'content-desc="نقشه آفلاین"' nv-ui.xml; then
    ui_ready=1
    break
  fi
  sleep 2
done
if [[ "$ui_ready" -ne 1 ]]; then
  echo "NV controls did not become visible"
  exit 1
fi

adb exec-out screencap -p > nv-launch-screen.png
grep -q 'content-desc="اطراف من"' nv-ui.xml
grep -q 'content-desc="سنجاق NV"' nv-ui.xml
grep -q 'کد NV' nv-ui.xml

# Nearby must be actionable and show categories.
tap_desc "اطراف من"
sleep 2
dump_ui
grep -q 'اطراف من' nv-ui.xml
grep -q 'داروخانه' nv-ui.xml
adb exec-out screencap -p > nv-nearby-screen.png
adb shell input keyevent 4
sleep 1

# Start the real production Iran map download and verify bytes arrive from the release asset.
tap_desc "نقشه آفلاین"
map_started=0
map_bytes=0
for _ in $(seq 1 45); do
  clear_emulator_dialogs
  raw="$(adb shell stat -c %s "$MAP_PATH" 2>/dev/null | tr -d '\r' || true)"
  if [[ "$raw" =~ ^[0-9]+$ ]]; then
    map_bytes="$raw"
    if (( map_bytes >= 65536 )); then map_started=1; break; fi
  fi
  sleep 2
done
printf 'Iran map downloaded bytes during smoke test: %s\n' "$map_bytes"
if [[ "$map_started" -ne 1 ]]; then
  echo "Iran map production download did not receive data"
  adb shell dumpsys download || true
  exit 1
fi
if ! adb shell pidof "$PACKAGE" >/dev/null 2>&1; then
  echo "NV process died after starting Iran map download"
  exit 1
fi
adb exec-out screencap -p > nv-offline-screen.png

# Explicit NV pin tool must open the point picker.
tap_desc "سنجاق NV"
sleep 2
dump_ui
grep -q 'ثبت سنجاق' nv-ui.xml
adb exec-out screencap -p > nv-pin-screen.png
adb shell input keyevent 4
sleep 1

# NV code/QR entry must remain visible and open its picker/dialog.
tap_desc "کد NV"
sleep 2
dump_ui
grep -Eq 'نقطه را برای کد NV انتخاب کنید|کد NV و QR' nv-ui.xml
adb exec-out screencap -p > nv-code-screen.png
adb shell input keyevent 4
sleep 1

adb shell dumpsys window windows > nv-window-state.txt
adb shell dumpsys activity activities > nv-activity-state.txt
adb logcat -d > nv-logcat.txt

if [[ "$app_alive" -ne 1 ]]; then echo "NV process did not stay alive after launch"; exit 1; fi
if [[ "$first_frame" -ne 1 ]]; then echo "NV did not render its first frame before the timeout"; exit 1; fi
if grep -E 'FATAL EXCEPTION: main|ANR in ir\.nv\.navigation\.debug' nv-logcat.txt; then echo "NV fatal exception or ANR detected"; exit 1; fi
if ! grep -q "$PACKAGE/$ACTIVITY" nv-activity-state.txt; then echo "NV MainActivity not present in activity state"; exit 1; fi

echo "NV launch, core UI, and real Iran map download-start verification passed"
