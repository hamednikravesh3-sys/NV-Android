#!/usr/bin/env bash
set -euo pipefail

BASE_SCRIPT=".github/scripts/verify-nv-launch-modern.sh"
PATCHED_SCRIPT="$(mktemp)"
trap 'rm -f "$PATCHED_SCRIPT"' EXIT

python3 - "$BASE_SCRIPT" "$PATCHED_SCRIPT" <<'PY'
from pathlib import Path
import sys

src = Path(sys.argv[1]).read_text(encoding="utf-8")
needle = "tap_text 'SOS' || { echo \"NV Android 16 could not open SOS and emergency services\"; exit 1; }"
replacement = r'''# SOS is intentionally validated from a fresh Home state. Route comparison leaves
# a selected route card active, where the Home SOS control is not composed.
adb_shell am force-stop "$PACKAGE"
adb_shell am start -W -n "$COMPONENT" || adb_shell am start -n "$COMPONENT"
adb emu geo fix "$TEST_LON" "$TEST_LAT" >/dev/null 2>&1 || true

SOS_HOME_READY=0
for attempt in $(seq 1 45); do
  if (( attempt % 3 == 0 )); then adb emu geo fix "$TEST_LON" "$TEST_LAT" >/dev/null 2>&1 || true; fi
  if dump_ui nv-modern-ui.xml && ui_has 'SOS' nv-modern-ui.xml; then SOS_HOME_READY=1; break; fi
  sleep 1
done
[[ "$SOS_HOME_READY" -eq 1 ]] || { echo "NV Android 16 Home did not expose the SOS entry after a clean relaunch"; exit 1; }
tap_text 'SOS' || { echo "NV Android 16 could not open SOS and emergency services"; exit 1; }'''

if needle not in src:
    raise SystemExit("SOS verification anchor not found in base script")
patched = src.replace(needle, replacement, 1)
Path(sys.argv[2]).write_text(patched, encoding="utf-8")
PY

chmod +x "$PATCHED_SCRIPT"
exec bash "$PATCHED_SCRIPT"
