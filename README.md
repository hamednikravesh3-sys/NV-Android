# NV Android

NV is a Persian-first navigation application for Android 10 and newer. This
repository replaces the earlier incomplete IranNavApp upload with a standard,
testable Android project.

## Current architecture (0.3)

- Android minSdk 29 (Android 10), Kotlin and Jetpack Compose
- online-first native map and online search/routing
- live network monitoring with automatic offline fallback
- user-initiated Iran-only map download from a dedicated in-app panel
- offline Mapsforge vector rendering with no online dependency
- deterministic place codes 1…N and search by Persian name or numeric code
- origin and destination dropdowns
- an on-device A* routing engine
- directed edges for one-way roads
- turn-restriction enforcement using incoming-edge routing state
- route distance, travel time and ETA
- map-first Persian Material interface, light/dark mode and adaptive NV icon
- distraction-minimized driving mode with large maneuver HUD, ETA panel,
  floating controls and Persian voice guidance
- a signed local 30-day trial record with rollback detection
- debug APK CI and a separate secret-backed signed-release workflow
- unit tests proving one-way and forbidden-turn behavior

## Data pack

The APK intentionally does not contain a multi-gigabyte Iran map. At first
launch it downloads one Iran-only archive from the map-v1 GitHub Release:

    iran.nvpack (schema v2)
      manifest.json
      iran.map
      places.db
      routing.db

The exact schemas and integrity rules are documented in docs/DATA_PACK.md.
tools/build_places.py assigns a sequential code to every named OSM feature in a
deterministic order.

The `map-v1` asset is published by the Iran data workflow. It is downloaded
only after the user taps the download action. When connectivity is lost, NV
automatically uses the installed vector map and local routing graph.

## Build

The Android CI workflow runs unit tests and builds the installable debug APK on
every push. Open the Actions tab, select the latest successful Android CI run,
and download the NV-debug-apk artifact.

Debug builds use the repository's intentionally public development keystore so
successive test APKs can update one another. This key must never be used for a
production release.

Local build with JDK 17, Android SDK 35 and Gradle 8.9:

    gradle :app:testDebugUnitTest :app:assembleDebug

## Signed release

Never commit a keystore. Add these encrypted repository secrets:

- NV_KEYSTORE_BASE64
- NV_KEYSTORE_PASSWORD
- NV_KEY_ALIAS
- NV_KEY_PASSWORD

Then manually run Signed Release APK from the Actions tab. Its artifact is the
release-signed app-release.apk.

## Production work still requiring external assets or accounts

The following cannot truthfully be marked finished only by compiling the APK:

- generating and uploading the full Iran MBTiles and routing databases
- connecting a licensed live-traffic feed
- selecting a weather-alert provider and storing its production credential
- creating the Google Play product and verifying purchases on a backend
- Play Integrity backed prevention of trial reset after uninstall/factory reset
- road testing on Android 10–17 devices across representative Iran routes

Local signing alone cannot prevent reinstall abuse. The included signed trial
record detects edits and clock rollback, while production enforcement must bind
Play Integrity verdicts and purchase tokens on a server.

## License and map attribution

Application source: all rights reserved until a LICENSE is selected.
Iran data-pack generation must preserve OpenStreetMap contributor attribution
and comply with the ODbL.
