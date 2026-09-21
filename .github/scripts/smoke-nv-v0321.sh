#!/usr/bin/env bash
set -euo pipefail

APK="$(find "$GITHUB_WORKSPACE/smoke-apk" -type f -name '*.apk' | head -n 1)"
test -n "$APK"
test -s "$APK"

adb devices -l | tee /tmp/nv-v0321-adb-devices.txt
adb install -r -t -g "$APK" | tee /tmp/nv-v0321-install.txt
grep -q '^Success$' /tmp/nv-v0321-install.txt
adb shell pm list packages | grep -q '^package:ir.nv.navigation.web.debug$'

adb shell pm grant ir.nv.navigation.web.debug android.permission.ACCESS_FINE_LOCATION || true
adb shell pm grant ir.nv.navigation.web.debug android.permission.ACCESS_COARSE_LOCATION || true
adb shell pm grant ir.nv.navigation.web.debug android.permission.POST_NOTIFICATIONS || true

adb logcat -c || true
adb shell am force-stop ir.nv.navigation.web.debug || true
adb shell am start -n ir.nv.navigation.web.debug/app.organicmaps.SplashActivity | tee /tmp/nv-v0321-smoke.txt
grep -q 'Starting: Intent' /tmp/nv-v0321-smoke.txt

PID=""
for attempt in $(seq 1 60); do
  PID="$(adb shell pidof ir.nv.navigation.web.debug 2>/dev/null | tr -d '\r' || true)"
  if [ -n "$PID" ]; then
    break
  fi
  sleep 2
done
printf '%s\n' "$PID" | tee /tmp/nv-v0321-pid.txt

adb logcat -d -t 2000 > /tmp/nv-v0321-logcat.txt || true
adb shell dumpsys activity activities > /tmp/nv-v0321-activities.txt || true

if grep -E 'FATAL EXCEPTION|Process: ir\.nv\.navigation\.web\.debug.*has died|AndroidRuntime:.*ir\.nv\.navigation\.web\.debug' /tmp/nv-v0321-logcat.txt; then
  echo "NV process crash detected"
  exit 1
fi

if [ -z "$PID" ]; then
  echo "NV process did not stay alive after launch"
  exit 1
fi

sleep 8
PID2="$(adb shell pidof ir.nv.navigation.web.debug 2>/dev/null | tr -d '\r' || true)"
if [ -z "$PID2" ]; then
  adb logcat -d -t 2500 > /tmp/nv-v0321-logcat.txt || true
  echo "NV process exited after initial launch"
  exit 1
fi

grep -q 'ir.nv.navigation.web.debug' /tmp/nv-v0321-activities.txt
echo "NV Android 16 smoke test PASSED"
