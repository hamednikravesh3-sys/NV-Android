#!/usr/bin/env bash
set -euo pipefail

APK="$(find "$GITHUB_WORKSPACE/smoke-apk" -type f -name '*.apk' | head -n 1)"
test -n "$APK"
test -s "$APK"

adb devices -l | tee /tmp/nv-v0321-adb-devices.txt
adb install -r -t "$APK" | tee /tmp/nv-v0321-install.txt
grep -q '^Success$' /tmp/nv-v0321-install.txt
adb shell pm list packages | grep -q '^package:ir.nv.navigation.web.debug$'

adb logcat -c || true
adb shell am force-stop ir.nv.navigation.web.debug || true
adb shell am start -W -n ir.nv.navigation.web.debug/app.organicmaps.SplashActivity | tee /tmp/nv-v0321-smoke.txt
sleep 10

grep -q 'Status: ok' /tmp/nv-v0321-smoke.txt
adb shell pidof ir.nv.navigation.web.debug | tee /tmp/nv-v0321-pid.txt
test -s /tmp/nv-v0321-pid.txt
adb shell dumpsys activity activities | grep -q 'ir.nv.navigation.web.debug'
adb logcat -d -t 800 > /tmp/nv-v0321-logcat.txt || true

if grep -E 'FATAL EXCEPTION|Process: ir\.nv\.navigation\.web\.debug.*has died|AndroidRuntime:.*ir\.nv\.navigation\.web\.debug' /tmp/nv-v0321-logcat.txt; then
  echo "NV process crash detected"
  exit 1
fi

echo "NV Android 16 smoke test PASSED"
