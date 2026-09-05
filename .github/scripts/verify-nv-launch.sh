#!/usr/bin/env bash
set -euo pipefail

PACKAGE="ir.nv.navigation.debug"
ACTIVITY="ir.nv.navigation.MainActivity"

adb wait-for-device
adb shell settings put global hide_error_dialogs 1
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION
adb logcat -c
adb shell am force-stop "$PACKAGE"
adb shell am start -n "$PACKAGE/$ACTIVITY"

app_alive=0
for _ in $(seq 1 30); do
  if adb shell pidof "$PACKAGE" >/dev/null 2>&1; then
    app_alive=1
    break
  fi
  sleep 2
done

sleep 8
adb exec-out screencap -p > nv-launch-screen.png
adb shell dumpsys window windows > nv-window-state.txt
adb shell dumpsys activity activities > nv-activity-state.txt
adb logcat -d > nv-logcat.txt

foreground="$(
  grep -E 'mCurrentFocus|mFocusedApp' nv-window-state.txt || true
  grep -E 'mResumedActivity|ResumedActivity:|topResumedActivity' nv-activity-state.txt || true
)"
printf '%s\n' "$foreground"

if [[ "$app_alive" -ne 1 ]]; then
  echo "NV process did not stay alive after launch"
  exit 1
fi

if grep -E 'FATAL EXCEPTION: main|ANR in ir\.nv\.navigation\.debug' nv-logcat.txt; then
  echo "NV fatal exception or ANR detected"
  exit 1
fi

if ! grep -q "$PACKAGE/$ACTIVITY" nv-activity-state.txt; then
  echo "NV MainActivity not present in activity state"
  exit 1
fi

echo "NV launch verification passed"
