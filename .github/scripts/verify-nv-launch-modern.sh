#!/usr/bin/env bash
set -euo pipefail

PACKAGE="${NV_VERIFY_PACKAGE:-ir.nv.navigation.debug}"
ACTIVITY="${NV_VERIFY_ACTIVITY:-ir.nv.navigation.MainActivity}"
APK_PATH="${NV_VERIFY_APK:-app/build/outputs/apk/debug/app-debug.apk}"
ACTIVITY_COMPONENT="$PACKAGE/$ACTIVITY"
ACTIVITY_DUMPSYS_COMPONENT="$PACKAGE/${ACTIVITY#"$PACKAGE"}"

activity_state_has_main_activity() {
  local state="$1"
  [[ "$state" == *"$ACTIVITY_COMPONENT"* || "$state" == *"$ACTIVITY_DUMPSYS_COMPONENT"* ]]
}

rm -f nv-modern-*.txt nv-modern-*.png
adb wait-for-device
test -s "$APK_PATH"
adb install -r "$APK_PATH"
adb shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION || true
adb shell pm grant "$PACKAGE" android.permission.ACCESS_COARSE_LOCATION || true
adb logcat -c
adb shell am force-stop "$PACKAGE"
adb shell am start -n "$ACTIVITY_COMPONENT"

PID=""
RESUMED=0
for attempt in $(seq 1 60); do
  PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' | awk '{print $1}' || true)"
  ACTIVITY_STATE="$(adb shell dumpsys activity activities 2>/dev/null || true)"
  if [[ -n "$PID" && "$ACTIVITY_STATE" == *"ResumedActivity"* ]] && activity_state_has_main_activity "$ACTIVITY_STATE"; then
    RESUMED=1
    break
  fi
  if (( attempt % 15 == 0 )); then
    adb shell am start -n "$ACTIVITY_COMPONENT" >/dev/null 2>&1 || true
  fi
  sleep 2
done

adb shell dumpsys activity activities > nv-modern-activity-state.txt || true
adb shell dumpsys window windows > nv-modern-window-state.txt || true
adb logcat -d > nv-modern-logcat.txt || true
adb exec-out screencap -p > nv-modern-launch-screen.png || true

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
ACTIVITY_STATE_FINAL="$(cat nv-modern-activity-state.txt)"
if ! activity_state_has_main_activity "$ACTIVITY_STATE_FINAL"; then
  echo "NV MainActivity is missing from activity state"
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

echo "NV modern Android launch verification passed for $PACKAGE (pid=$PID)"
