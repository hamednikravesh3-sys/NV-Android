#!/usr/bin/env bash
set -euo pipefail

PACKAGE="${NV_VERIFY_PACKAGE:-ir.nv.navigation}"
ACTIVITY="${NV_VERIFY_ACTIVITY:-ir.nv.navigation.MainActivity}"
APK_PATH="${NV_VERIFY_APK:-app/build/outputs/apk/release/Rahnama-Android16-v0.18.6-acceptance.apk}"
COMPONENT="$PACKAGE/$ACTIVITY"
TEST_LAT="${NV_VERIFY_LAT:-35.6892}"
TEST_LON="${NV_VERIFY_LON:-51.3890}"

adb wait-for-device
for _ in $(seq 1 90); do
  [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" == "1" ]] && break
  sleep 2
done
[[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" == "1" ]]

dump_ui() {
  local out="${1:-nv-v0186-smart-ui.xml}"
  adb shell uiautomator dump /sdcard/nv-v0186-ui.xml >/dev/null 2>&1 || true
  adb pull /sdcard/nv-v0186-ui.xml "$out" >/dev/null 2>&1 || true
  [[ -s "$out" ]]
}

ui_has() {
  local text="$1" file="${2:-nv-v0186-smart-ui.xml}"
  [[ -s "$file" ]] && grep -Fq "$text" "$file"
}

tap_semantics() {
  local target="$1"
  dump_ui nv-v0186-smart-ui.xml || return 1
  local xy
  xy="$(UI_TARGET="$target" python3 - <<'PY'
import os,re,sys,xml.etree.ElementTree as ET
root=ET.parse('nv-v0186-smart-ui.xml').getroot(); target=os.environ['UI_TARGET']
for n in root.iter('node'):
    if n.attrib.get('text') == target or n.attrib.get('content-desc') == target:
        m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            print((x1+x2)//2,(y1+y2)//2); sys.exit(0)
sys.exit(1)
PY
)" || return 1
  read -r x y <<<"$xy"
  adb shell input tap "$x" "$y" >/dev/null
}

screen_size() {
  local size
  size="$(adb shell wm size 2>/dev/null | tail -1 | grep -Eo '[0-9]+x[0-9]+' || true)"
  if [[ -z "$size" ]]; then echo "1080 1920"; else echo "${size%x*} ${size#*x}"; fi
}

swipe_up() {
  read -r w h <<<"$(screen_size)"
  adb shell input swipe "$((w/2))" "$((h*4/5))" "$((w/2))" "$((h*2/5))" 500 >/dev/null
}

collect_diag() {
  adb exec-out screencap -p > nv-v0186-smart-screen.png 2>/dev/null || true
  dump_ui nv-v0186-smart-ui.xml || true
  adb logcat -d > nv-v0186-smart-logcat.txt 2>/dev/null || true
}
trap 'status=$?; [[ $status -eq 0 ]] || collect_diag; exit $status' EXIT

rm -f nv-v0186-smart-ui.xml nv-v0186-smart-screen.png nv-v0186-smart-logcat.txt
[[ -s "$APK_PATH" ]]
adb install -r "$APK_PATH" >/dev/null
adb shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION >/dev/null 2>&1 || true
adb shell pm grant "$PACKAGE" android.permission.ACCESS_COARSE_LOCATION >/dev/null 2>&1 || true
adb emu geo fix "$TEST_LON" "$TEST_LAT" >/dev/null 2>&1 || true
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$COMPONENT" >/dev/null || adb shell am start -n "$COMPONENT" >/dev/null

HOME_READY=0
for attempt in $(seq 1 60); do
  (( attempt % 3 != 0 )) || adb emu geo fix "$TEST_LON" "$TEST_LAT" >/dev/null 2>&1 || true
  if dump_ui nv-v0186-smart-ui.xml && (ui_has 'موقعیت فعال' || ui_has 'موقعیت ±'); then HOME_READY=1; break; fi
  sleep 1
done
[[ "$HOME_READY" -eq 1 ]] || { echo "v0.18.6 did not consume the Android 16 GPS fix"; exit 1; }

tap_semantics 'مرکز هوشمند راهنما' || { echo "Smart Hub launcher is not reachable"; exit 1; }
HUB_READY=0
for _ in $(seq 1 20); do
  if dump_ui nv-v0186-smart-ui.xml && ui_has 'مرکز هوشمند راهنما' && ui_has 'دستیار سفر هوشمند' && ui_has 'حالت عجله' && ui_has 'مسیر چندحالته'; then
    HUB_READY=1; break
  fi
  sleep 1
done
[[ "$HUB_READY" -eq 1 ]] || { echo "Professional Smart Hub did not render expected primary cards"; exit 1; }

# Prove that the long professional menu is actually scrollable/reachable.
FOUND_DEEP=0
for _ in $(seq 1 6); do
  dump_ui nv-v0186-smart-ui.xml || true
  if ui_has 'زمان و هزینه' && ui_has 'مسیریابی پیاده' && ui_has 'پارکینگ مقصد'; then FOUND_DEEP=1; break; fi
  swipe_up
  sleep 1
done
[[ "$FOUND_DEEP" -eq 1 ]] || { echo "Smart Hub lower cards are not reachable"; exit 1; }

# Return toward top and open the chat capability.
read -r w h <<<"$(screen_size)"
for _ in $(seq 1 6); do adb shell input swipe "$((w/2))" "$((h*2/5))" "$((w/2))" "$((h*4/5))" 450 >/dev/null; done
sleep 1
tap_semantics 'دستیار سفر هوشمند' || { echo "Smart travel assistant card is not actionable"; exit 1; }
CHAT_READY=0
for _ in $(seq 1 15); do
  if dump_ui nv-v0186-smart-ui.xml && ui_has 'سؤال سفر' && ui_has 'تحلیل درخواست و ساخت بهترین مسیر'; then CHAT_READY=1; break; fi
  sleep 1
done
[[ "$CHAT_READY" -eq 1 ]] || { echo "Smart chat input/action did not render"; exit 1; }

echo "v0.18.6 Smart Hub acceptance test passed"
