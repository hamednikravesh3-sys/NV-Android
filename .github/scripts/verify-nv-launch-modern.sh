#!/usr/bin/env bash
set -euo pipefail

PACKAGE="${NV_VERIFY_PACKAGE:-ir.nv.navigation.debug}"
ACTIVITY="${NV_VERIFY_ACTIVITY:-ir.nv.navigation.MainActivity}"
APK_PATH="${NV_VERIFY_APK:-app/build/outputs/apk/debug/app-debug.apk}"
ACTIVITY_COMPONENT="$PACKAGE/$ACTIVITY"
ACTIVITY_DUMPSYS_COMPONENT="$PACKAGE/${ACTIVITY#"$PACKAGE"}"
TEST_LAT="${NV_VERIFY_LAT:-35.6892}"
TEST_LON="${NV_VERIFY_LON:-51.3890}"

activity_state_has_main_activity() {
  local state="$1"
  [[ "$state" == *"$ACTIVITY_COMPONENT"* || "$state" == *"$ACTIVITY_DUMPSYS_COMPONENT"* ]]
}

window_state_has_drawn_main_activity() {
  local state="$1"
  [[ "$state" == *"$ACTIVITY_COMPONENT"* || "$state" == *"$ACTIVITY_DUMPSYS_COMPONENT"* ]] && \
    [[ "$state" != *"Splash Screen $PACKAGE"* ]]
}

window_state_has_anr_dialog() {
  local state="$1"
  [[ "$state" == *"Application Not Responding:"* ]]
}

window_state_has_app_anr_dialog() {
  local state="$1"
  [[ "$state" == *"Application Not Responding: $PACKAGE"* || "$state" == *"Application Not Responding: ir.nv.navigation"* ]]
}

recover_adb() {
  adb kill-server >/dev/null 2>&1 || true
  adb start-server >/dev/null
  adb wait-for-device
}

adb_shell_retry() {
  local attempt
  for attempt in 1 2 3; do
    if adb shell "$@"; then
      return 0
    fi
    echo "adb shell failed (attempt $attempt/3); restarting adb"
    recover_adb
    sleep 2
  done
  return 1
}

rm -f nv-modern-*.txt nv-modern-*.png nv-modern-*.xml
adb wait-for-device

BOOTED=0
for attempt in $(seq 1 90); do
  if [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" == "1" ]]; then
    BOOTED=1
    break
  fi
  sleep 2
done
if [[ "$BOOTED" -ne 1 ]]; then
  echo "Android did not report boot completion"
  exit 1
fi

adb_shell_retry settings put global hide_error_dialogs 1 || true
adb_shell_retry settings put global window_animation_scale 0 || true
adb_shell_retry settings put global transition_animation_scale 0 || true
adb_shell_retry settings put global animator_duration_scale 0 || true
sleep 5

test -s "$APK_PATH"
adb install -r "$APK_PATH"
adb_shell_retry pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION || true
adb_shell_retry pm grant "$PACKAGE" android.permission.ACCESS_COARSE_LOCATION || true

# Seed a deterministic GPS fix before cold start. Emulator command order is longitude, latitude.
adb emu geo fix "$TEST_LON" "$TEST_LAT" >/dev/null 2>&1 || true
sleep 2

adb logcat -c || recover_adb
adb_shell_retry am force-stop "$PACKAGE"
adb_shell_retry am start -W -n "$ACTIVITY_COMPONENT" || adb_shell_retry am start -n "$ACTIVITY_COMPONENT"

PID=""
RESUMED=0
FIRST_DRAW=0
LOCATION_READY=0
for attempt in $(seq 1 90); do
  PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' | awk '{print $1}' || true)"
  ACTIVITY_STATE="$(adb shell dumpsys activity activities 2>/dev/null || true)"
  WINDOW_STATE="$(adb shell dumpsys window windows 2>/dev/null || true)"

  if [[ -n "$PID" && "$ACTIVITY_STATE" == *"ResumedActivity"* ]] && activity_state_has_main_activity "$ACTIVITY_STATE"; then
    RESUMED=1
  fi
  if window_state_has_drawn_main_activity "$WINDOW_STATE"; then
    FIRST_DRAW=1
  fi

  if window_state_has_app_anr_dialog "$WINDOW_STATE"; then
    echo "NV ANR dialog detected"
    break
  fi

  # Launcher/SystemUI may ANR on constrained API 36 CI runners even while NV is fully drawn.
  # Dismiss only unrelated platform dialogs; app ANRs remain fatal.
  if window_state_has_anr_dialog "$WINDOW_STATE"; then
    if [[ "$WINDOW_STATE" == *"Application Not Responding: com.android.launcher3"* ]]; then
      adb_shell_retry am force-stop com.android.launcher3 >/dev/null 2>&1 || true
    else
      adb_shell_retry input keyevent 4 >/dev/null 2>&1 || true
    fi
    sleep 1
  fi

  if (( attempt % 3 == 0 )); then
    adb emu geo fix "$TEST_LON" "$TEST_LAT" >/dev/null 2>&1 || true
    adb shell uiautomator dump /sdcard/nv-modern-ui.xml >/dev/null 2>&1 || true
    adb pull /sdcard/nv-modern-ui.xml nv-modern-ui.xml >/dev/null 2>&1 || true
    if [[ -s nv-modern-ui.xml ]] && grep -q 'موقعیت فعال' nv-modern-ui.xml; then
      LOCATION_READY=1
    fi
  fi

  if [[ "$RESUMED" -eq 1 && "$FIRST_DRAW" -eq 1 && "$LOCATION_READY" -eq 1 ]]; then
    break
  fi

  if (( attempt % 20 == 0 )); then
    adb_shell_retry am start -n "$ACTIVITY_COMPONENT" >/dev/null 2>&1 || true
  fi
  sleep 2
done

adb shell dumpsys activity activities > nv-modern-activity-state.txt || true
adb shell dumpsys window windows > nv-modern-window-state.txt || true
adb logcat -d > nv-modern-logcat.txt || true
adb exec-out screencap -p > nv-modern-launch-screen.png || true
adb shell uiautomator dump /sdcard/nv-modern-ui.xml >/dev/null 2>&1 || true
adb pull /sdcard/nv-modern-ui.xml nv-modern-ui.xml >/dev/null 2>&1 || true

PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' | awk '{print $1}' || true)"
: > nv-modern-app-logcat.txt
if [[ -n "$PID" ]]; then
  adb logcat -d --pid="$PID" > nv-modern-app-logcat.txt 2>/dev/null || true
fi

if [[ -z "$PID" ]]; then
  echo "NV process is not alive on modern Android"
  exit 1
fi
if [[ "$RESUMED" -ne 1 ]]; then
  echo "NV MainActivity did not reach resumed state on modern Android"
  exit 1
fi
if [[ "$FIRST_DRAW" -ne 1 ]]; then
  echo "NV MainActivity never replaced the splash screen with a drawn app window"
  exit 1
fi
if [[ "$LOCATION_READY" -ne 1 ]]; then
  echo "NV did not consume the injected Android 16 GPS fix"
  exit 1
fi

ACTIVITY_STATE_FINAL="$(cat nv-modern-activity-state.txt)"
WINDOW_STATE_FINAL="$(cat nv-modern-window-state.txt)"
if ! activity_state_has_main_activity "$ACTIVITY_STATE_FINAL"; then
  echo "NV MainActivity is missing from activity state"
  exit 1
fi
if ! window_state_has_drawn_main_activity "$WINDOW_STATE_FINAL"; then
  echo "NV MainActivity is not the drawn window after launch"
  exit 1
fi
if window_state_has_app_anr_dialog "$WINDOW_STATE_FINAL"; then
  echo "NV ANR dialog detected in final Android window state"
  exit 1
fi
if grep -E 'FATAL EXCEPTION:' nv-modern-app-logcat.txt; then
  echo "NV fatal exception detected on modern Android"
  exit 1
fi
if grep -E "ANR in ${PACKAGE//./\\.}([[:space:]]|$)" nv-modern-logcat.txt; then
  echo "NV ANR detected on modern Android"
  exit 1
fi

echo "NV Android 16 first-draw and GPS verification passed for $PACKAGE (pid=$PID)"
