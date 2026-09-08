#!/usr/bin/env bash
set -euo pipefail

PACKAGE="${NV_VERIFY_PACKAGE:-ir.nv.navigation.debug}"
ACTIVITY="${NV_VERIFY_ACTIVITY:-ir.nv.navigation.MainActivity}"
APK_PATH="${NV_VERIFY_APK:-app/build/outputs/apk/debug/app-debug.apk}"
MAP_PATH="/sdcard/Android/data/$PACKAGE/files/Download/Iran map.nvpack"

adb wait-for-device
adb shell settings put global hide_error_dialogs 1 || true
test -s "$APK_PATH"
adb install -r "$APK_PATH"
adb shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION
adb shell pm grant "$PACKAGE" android.permission.ACCESS_COARSE_LOCATION || true
adb logcat -c
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$PACKAGE/$ACTIVITY" || adb shell am start -n "$PACKAGE/$ACTIVITY"

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

function ensure_app_foreground() {
  local resumed
  resumed="$(adb shell dumpsys activity activities 2>/dev/null | grep -m1 'ResumedActivity' || true)"
  if [[ "$resumed" != *"$PACKAGE/$ACTIVITY"* ]]; then
    adb shell am start -W -n "$PACKAGE/$ACTIVITY" >/dev/null 2>&1 || adb shell am start -n "$PACKAGE/$ACTIVITY" >/dev/null 2>&1 || true
    sleep 2
  fi
}

clear_emulator_dialogs

app_alive=0
APP_PID=""
for _ in $(seq 1 30); do
  clear_emulator_dialogs
  ensure_app_foreground
  APP_PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' | awk '{print $1}' || true)"
  if [[ -n "$APP_PID" ]]; then app_alive=1; break; fi
  sleep 2
done

first_frame=0
for _ in $(seq 1 60); do
  clear_emulator_dialogs
  ensure_app_foreground
  if adb logcat -d | grep -F "Displayed $PACKAGE/$ACTIVITY" >/dev/null; then first_frame=1; break; fi
  if ! adb shell pidof "$PACKAGE" >/dev/null 2>&1; then break; fi
  sleep 2
done

function dump_ui() {
  clear_emulator_dialogs
  ensure_app_foreground
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

ui_ready=0
for attempt in $(seq 1 60); do
  dump_ui
  if grep -q "package=\"$PACKAGE\"" nv-ui.xml && grep -q 'content-desc="جستجو"' nv-ui.xml && grep -q 'content-desc="نقشه آفلاین"' nv-ui.xml; then
    ui_ready=1
    break
  fi
  if (( attempt % 10 == 0 )); then
    echo "NV UI not ready after $attempt checks; reasserting foreground activity"
    adb shell am start -W -n "$PACKAGE/$ACTIVITY" >/dev/null 2>&1 || true
  fi
  sleep 2
done
if [[ "$ui_ready" -ne 1 ]]; then
  echo "NV controls did not become visible"
  adb shell dumpsys window windows > nv-window-state.txt || true
  adb shell dumpsys activity activities > nv-activity-state.txt || true
  adb logcat -d > nv-logcat.txt || true
  adb exec-out screencap -p > nv-launch-screen.png || true
  exit 1
fi

adb exec-out screencap -p > nv-launch-screen.png
grep -q 'content-desc="اطراف من"' nv-ui.xml
grep -q 'content-desc="سنجاق NV"' nv-ui.xml

tap_desc "اطراف من"
sleep 2
dump_ui
grep -q 'اطراف من' nv-ui.xml
grep -q 'داروخانه' nv-ui.xml
adb exec-out screencap -p > nv-nearby-screen.png
adb shell input keyevent 4
sleep 1

tap_desc "نقشه آفلاین"
map_started=0
map_bytes=0
for _ in $(seq 1 15); do
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
  echo "WARNING: Iran map asset did not deliver bytes in CI; keeping launch verification independent from external CDN availability"
fi
if ! adb shell pidof "$PACKAGE" >/dev/null 2>&1; then
  echo "NV process died after starting Iran map download"
  exit 1
fi
adb exec-out screencap -p > nv-offline-screen.png

tap_desc "سنجاق NV"
sleep 2
dump_ui
grep -Eq 'نقطه دقیق را روی نقشه مشخص کنید|تأیید مکان' nv-ui.xml
adb exec-out screencap -p > nv-pin-screen.png
adb shell input keyevent 4
sleep 1

adb shell dumpsys window windows > nv-window-state.txt
adb shell dumpsys activity activities > nv-activity-state.txt
adb logcat -d > nv-logcat.txt

: > nv-app-logcat.txt
APP_PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' | awk '{print $1}' || true)"
if [[ -n "$APP_PID" ]]; then
  adb logcat -d --pid="$APP_PID" > nv-app-logcat.txt 2>/dev/null || true
fi

if [[ "$app_alive" -ne 1 ]]; then echo "NV process did not stay alive after launch"; exit 1; fi
if [[ "$first_frame" -ne 1 ]]; then echo "NV did not render its first frame before the timeout"; exit 1; fi
if grep -E 'FATAL EXCEPTION:' nv-app-logcat.txt; then echo "NV fatal exception detected"; exit 1; fi
if grep -E "ANR in ${PACKAGE//./\\.}([[:space:]]|$)" nv-logcat.txt; then echo "NV ANR detected"; exit 1; fi
if ! grep -q "$PACKAGE/$ACTIVITY" nv-activity-state.txt; then echo "NV MainActivity not present in activity state"; exit 1; fi

echo "NV launch and core Persian UI verification passed for $PACKAGE"
