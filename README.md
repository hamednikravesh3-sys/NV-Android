# NV Android

NV is a Persian-first navigation application for Android 10 and newer. This
repository replaces the earlier incomplete IranNavApp upload with a standard,
testable Android project.

## Current architecture (0.8)

- Android minSdk 29 (Android 10), Kotlin and Jetpack Compose
- online-first native map, instant bundled Iran-city search, Photon plus
  Nominatim street/place fallback, and dual-provider routing
- live network monitoring with automatic offline fallback
- user-initiated Iran-only map download from a dedicated in-app panel
- offline Mapsforge vector rendering with no online dependency
- stable public place codes, user-defined numeric personal codes with a dedicated
  persistent menu, recent destinations and
  search by Persian name, Persian/Latin digits, numeric code or `NV:code`
- origin and destination dropdowns
- an on-device A* routing engine
- directed edges for one-way roads
- turn-restriction enforcement using incoming-edge routing state
- up to three online route alternatives with visual selection, route distance,
  travel time and ETA
- reference-matched D/F/H/I interface: bright map-first home, automatic navy
  route-selection and driving surfaces, cyan glow route, glass-like cards,
  one-hand dock, destination bottom sheets and the approved green NV icon
- D/F/H/I navigation flow: map-first home, route-choice sheet, distraction-
  minimized driving HUD, and an always-active attractions/services-ahead sheet
- online OpenStreetMap attractions and services for the next 10 km, with automatic
  offline fallback to the installed Iran data pack
- a live weather card sampled at the actual coordinate exactly 10 km ahead
- GPS speed display and OSRM lane guidance in the active-driving HUD
- real TomTom traffic-flow adapter (enabled only when `NV_TRAFFIC_API_KEY` is
  provided; NV never invents congestion when live data is unavailable)
- destination code sharing as `NV:1845623` with an on-device QR code
- distraction-minimized driving mode with large maneuver HUD, ETA panel,
  live remaining-distance updates, off-route rerouting, floating controls and
  Persian voice guidance
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
tools/build_places.py assigns the initial sequential codes to named OSM
features. Pass `--registry place-codes.json` on every later build to keep each
public code stable when OpenStreetMap adds or renames places.

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

The development APK uses public Photon and OSRM-compatible demo endpoints with
automatic failover. Their public availability is not an SLA; a production
release must use a contracted provider or an NV-operated routing/geocoding
backend. Android code also cannot suppress a Play Protect reputation warning:
the production app must keep one private release key and be distributed through
Google Play while its developer reputation is established.

## License and map attribution

Application source: all rights reserved until a LICENSE is selected.
Iran data-pack generation must preserve OpenStreetMap contributor attribution
and comply with the ODbL.
