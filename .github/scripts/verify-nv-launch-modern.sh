#!/usr/bin/env bash
set -euo pipefail

PACKAGE="${NV_VERIFY_PACKAGE:-ir.nv.navigation.debug}"
ACTIVITY="${NV_VERIFY_ACTIVITY:-ir.nv.navigation.MainActivity}"
APK_PATH="${NV_VERIFY_APK:-app/build/outputs/apk/debug/app-debug.apk}"
COMPONENT="$PACKAGE/$ACTIVITY"
TEST_LAT="${NV_VERIFY_LAT:-35.6892}"
TEST_LON="${NV_VERIFY_LON:-51.3890}"

adb_shell() {
  local attempt
  for attempt in 1 2 3; do
    if adb shell "$@"; then return 0; fi
    adb kill-server >/dev/null 2>&1 || true
    adb start-server >/dev/null 2>&1 || true
    adb wait-for-device
    sleep 2
  done
  return 1
}

dump_ui() {
  local out="${1:-nv-modern-ui.xml}"
  adb shell uiautomator dump /sdcard/nv-modern-ui.xml >/dev/null 2>&1 || true
  adb pull /sdcard/nv-modern-ui.xml "$out" >/dev/null 2>&1 || true
  [[ -s "$out" ]]
}

ui_has() {
  local text="$1" file="${2:-nv-modern-ui.xml}"
  [[ -s "$file" ]] && grep -Fq "$text" "$file"
}

tap_text() {
  local target="$1"
  dump_ui nv-modern-ui.xml || return 1
  local xy
  xy="$(UI_TARGET="$target" python3 - <<'PY'
import os,re,sys,xml.etree.ElementTree as ET
root=ET.parse('nv-modern-ui.xml').getroot(); target=os.environ['UI_TARGET']
for n in root.iter('node'):
    if n.attrib.get('text') == target or n.attrib.get('content-desc') == target:
        m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); sys.exit(0)
sys.exit(1)
PY
)" || return 1
  read -r x y <<<"$xy"
  adb_shell input tap "$x" "$y" >/dev/null
}

tap_search() {
  local size w h
  size="$(adb shell wm size 2>/dev/null | tail -1 | grep -Eo '[0-9]+x[0-9]+' || true)"
  w="${size%x*}"; h="${size#*x}"
  [[ -n "$w" && -n "$h" && "$w" != "$size" ]] || { w=1080; h=1920; }
  adb_shell input tap "$((w/2))" "$((h/10))" >/dev/null
}

swipe_up() {
  local size w h
  size="$(adb shell wm size 2>/dev/null | tail -1 | grep -Eo '[0-9]+x[0-9]+' || true)"
  w="${size%x*}"; h="${size#*x}"
  [[ -n "$w" && -n "$h" && "$w" != "$size" ]] || { w=1080; h=1920; }
  adb_shell input swipe "$((w/2))" "$((h*4/5))" "$((w/2))" "$((h*2/5))" 450 >/dev/null
}

collect_diag() {
  adb shell dumpsys activity activities > nv-modern-activity-state.txt 2>/dev/null || true
  adb shell dumpsys window windows > nv-modern-window-state.txt 2>/dev/null || true
  adb logcat -d > nv-modern-logcat.txt 2>/dev/null || true
  adb exec-out screencap -p > nv-modern-launch-screen.png 2>/dev/null || true
  dump_ui nv-modern-ui.xml || true
  local pid
  pid="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' | awk '{print $1}' || true)"
  : > nv-modern-app-logcat.txt
  [[ -z "$pid" ]] || adb logcat -d --pid="$pid" > nv-modern-app-logcat.txt 2>/dev/null || true
}

trap 'status=$?; [[ $status -eq 0 ]] || collect_diag; exit $status' EXIT
rm -f nv-modern-*.txt nv-modern-*.png nv-modern-*.xml
adb wait-for-device

for _ in $(seq 1 90); do
  [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" == "1" ]] && break
  sleep 2
done
[[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" == "1" ]] || { echo "Android did not report boot completion"; exit 1; }

adb_shell settings put global hide_error_dialogs 1 || true
adb_shell settings put global window_animation_scale 0 || true
adb_shell settings put global transition_animation_scale 0 || true
adb_shell settings put global animator_duration_scale 0 || true
sleep 4

test -s "$APK_PATH"
adb install -r "$APK_PATH"
adb_shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION || true
adb_shell pm grant "$PACKAGE" android.permission.ACCESS_COARSE_LOCATION || true
adb emu geo fix "$TEST_LON" "$TEST_LAT" >/dev/null 2>&1 || true
sleep 2
adb logcat -c || true
adb_shell am force-stop "$PACKAGE"
adb_shell am start -W -n "$COMPONENT" || adb_shell am start -n "$COMPONENT"

LOCATION_READY=0
for attempt in $(seq 1 90); do
  if (( attempt % 3 == 0 )); then adb emu geo fix "$TEST_LON" "$TEST_LAT" >/dev/null 2>&1 || true; fi
  if dump_ui nv-modern-ui.xml && ui_has 'موقعیت فعال' nv-modern-ui.xml; then LOCATION_READY=1; break; fi
  sleep 2
done
[[ "$LOCATION_READY" -eq 1 ]] || { echo "NV did not consume the injected Android 16 GPS fix"; exit 1; }

# Validate the new Nearby product architecture using controls that are guaranteed to be
# composed in the initial viewport. Lower categories are covered by taxonomy/unit tests.
tap_text 'همه اطراف من' || { echo "NV Android 16 could not open the Rahnama Nearby hub"; exit 1; }
NEARBY_READY=0
for _ in $(seq 1 20); do
  if dump_ui nv-modern-nearby-ui.xml && \
     ui_has 'محدوده جستجو' nv-modern-nearby-ui.xml && \
     ui_has 'اطراف من' nv-modern-nearby-ui.xml && \
     ui_has 'در طول مسیر' nv-modern-nearby-ui.xml && \
     ui_has 'نزدیک مقصد' nv-modern-nearby-ui.xml && \
     ui_has 'نزدیک مبدأ' nv-modern-nearby-ui.xml && \
     ui_has 'شعاع جستجو' nv-modern-nearby-ui.xml && \
     ui_has '5' nv-modern-nearby-ui.xml && ui_has '10' nv-modern-nearby-ui.xml && \
     ui_has '25' nv-modern-nearby-ui.xml && ui_has '50' nv-modern-nearby-ui.xml && ui_has '100' nv-modern-nearby-ui.xml && \
     ui_has 'اورژانس' nv-modern-nearby-ui.xml && ui_has 'بیمارستان' nv-modern-nearby-ui.xml && \
     ui_has 'داروخانه' nv-modern-nearby-ui.xml && ui_has 'پلیس' nv-modern-nearby-ui.xml; then
    NEARBY_READY=1; break
  fi
  sleep 1
done
[[ "$NEARBY_READY" -eq 1 ]] || { echo "NV Android 16 Nearby hub did not expose scopes, radii and visible core categories"; exit 1; }
tap_text 'بستن' || adb_shell input keyevent 4 >/dev/null 2>&1 || true
sleep 1

# Search is deterministic through IranCityIndex and does not depend on a geocoder.
tap_search
adb_shell input text karaj >/dev/null
SEARCH_READY=0
for _ in $(seq 1 30); do
  if dump_ui nv-modern-search-ui.xml && ui_has 'کرج' nv-modern-search-ui.xml; then SEARCH_READY=1; break; fi
  sleep 1
done
[[ "$SEARCH_READY" -eq 1 ]] || { echo "NV Android 16 Home search did not return built-in Karaj result"; exit 1; }
tap_text 'کرج' || { echo "NV could not select the Karaj search result"; exit 1; }

ROUTE_READY=0
for attempt in $(seq 1 60); do
  if (( attempt % 4 == 0 )); then adb emu geo fix "$TEST_LON" "$TEST_LAT" >/dev/null 2>&1 || true; fi
  if dump_ui nv-modern-route-ui.xml && ui_has 'شروع' nv-modern-route-ui.xml; then ROUTE_READY=1; break; fi
  sleep 1
done
[[ "$ROUTE_READY" -eq 1 ]] || { echo "NV Android 16 did not produce a route card from Tehran to Karaj"; exit 1; }

# Screen 10: route comparison must be reachable from real alternatives and expose
# decision data in the sheet. The confirmation control may be below the fold.
COMPARISON_BUTTON=0
for _ in $(seq 1 10); do
  if dump_ui nv-modern-route-ui.xml && ui_has 'مقایسه مسیرها' nv-modern-route-ui.xml; then COMPARISON_BUTTON=1; break; fi
  sleep 1
done
[[ "$COMPARISON_BUTTON" -eq 1 ]] || { echo "NV route alternatives did not expose the Rahnama comparison action"; exit 1; }
tap_text 'مقایسه مسیرها' || { echo "NV could not open route comparison"; exit 1; }
COMPARISON_READY=0
for _ in $(seq 1 15); do
  if dump_ui nv-modern-comparison-ui.xml && \
     ui_has 'مقایسه مسیرها' nv-modern-comparison-ui.xml && \
     ui_has 'پیشنهاد راهنما' nv-modern-comparison-ui.xml && \
     ui_has 'زمان' nv-modern-comparison-ui.xml && \
     ui_has 'فاصله' nv-modern-comparison-ui.xml && \
     ui_has 'رسیدن' nv-modern-comparison-ui.xml && \
     ui_has 'مسیر 2' nv-modern-comparison-ui.xml; then
    COMPARISON_READY=1; break
  fi
  sleep 1
done
[[ "$COMPARISON_READY" -eq 1 ]] || { echo "NV Android 16 route comparison sheet did not render expected decision data"; exit 1; }

CONFIRM_READY=0
for _ in $(seq 1 5); do
  if dump_ui nv-modern-comparison-ui.xml && ui_has 'تأیید مسیر انتخاب‌شده' nv-modern-comparison-ui.xml; then
    CONFIRM_READY=1; break
  fi
  swipe_up || true
  sleep 1
done
[[ "$CONFIRM_READY" -eq 1 ]] || { echo "NV Android 16 route comparison confirmation action is not reachable"; exit 1; }
tap_text 'تأیید مسیر انتخاب‌شده' || { echo "NV could not confirm the selected route comparison result"; exit 1; }
sleep 1

# Screen 12: SOS/emergency entry must be reachable without a network request and expose
# the configured Iran emergency services plus the nearby-center action.
tap_text 'SOS و خدمات اضطراری' || { echo "NV Android 16 could not open SOS and emergency services"; exit 1; }
EMERGENCY_READY=0
for _ in $(seq 1 15); do
  if dump_ui nv-modern-emergency-ui.xml && \
     ui_has 'SOS و خدمات اضطراری' nv-modern-emergency-ui.xml && \
     ui_has 'اورژانس پزشکی' nv-modern-emergency-ui.xml && ui_has '115' nv-modern-emergency-ui.xml && \
     ui_has 'پلیس' nv-modern-emergency-ui.xml && ui_has '110' nv-modern-emergency-ui.xml && \
     ui_has 'آتش‌نشانی' nv-modern-emergency-ui.xml && ui_has '125' nv-modern-emergency-ui.xml && \
     ui_has 'امداد و نجات' nv-modern-emergency-ui.xml && ui_has '112' nv-modern-emergency-ui.xml && \
     ui_has 'یافتن نزدیک‌ترین اورژانس پزشکی' nv-modern-emergency-ui.xml; then
    EMERGENCY_READY=1; break
  fi
  sleep 1
done
[[ "$EMERGENCY_READY" -eq 1 ]] || { echo "NV Android 16 emergency overlay did not expose expected SOS services"; exit 1; }
tap_text 'بستن' || adb_shell input keyevent 4 >/dev/null 2>&1 || true
sleep 1

collect_diag
PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' | awk '{print $1}' || true)"
[[ -n "$PID" ]] || { echo "NV process died during Android 16 verification"; exit 1; }
grep -q "$PACKAGE" nv-modern-activity-state.txt || { echo "NV MainActivity missing from activity state"; exit 1; }
! grep -E 'FATAL EXCEPTION:' nv-modern-app-logcat.txt || { echo "NV fatal exception detected"; exit 1; }
! grep -E "ANR in ${PACKAGE//./\\.}([[:space:]]|$)" nv-modern-logcat.txt || { echo "NV ANR detected"; exit 1; }

trap - EXIT
echo "NV Android 16 GPS, Nearby, search, route, comparison, and SOS verification passed for $PACKAGE (pid=$PID)"
