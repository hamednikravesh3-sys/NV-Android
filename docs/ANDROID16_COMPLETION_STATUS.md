# Rahnama Android 16 completion status

## Release target

Rahnama `0.18.0` (`versionCode 19`) is an Android-only native Kotlin/Jetpack Compose release targeting Android 16 (`compileSdk 36`, `targetSdk 36`) with `minSdk 29`.

The release gates require Android SDK 36/build-tools 36.0.0, APK metadata verification, 16 KiB ZIP alignment verification, unit tests, lint/build tasks, and runtime verification on an API 36 emulator. The Android CI also builds and runs the instrumented Android 16 activity test.

## Completed scope

The frozen Android scope includes Persian/RTL UI, online/offline place discovery, emergency discovery, progressive nearby search, online/offline routing and alternatives, route profiles including Safe, vehicle-aware routing (car, motorcycle, truck, EV, bicycle, walking and transit contracts), truck restrictions, EV route constraints, Valhalla/OSRM/offline fallback behavior, local route-geometry map matching fallback, navigation progress, maneuver guidance, speed-limit display, confidence-gated off-route rerouting, multi-sample arrival confirmation, route alerts, weather/traffic adapters, parking-near-destination ranking with driving-to-parking and walking handoff, privacy controls, local-first community reports, Smart Mobility preferences, transfer ranking, ETA risk, rush-mode support, and explicit offline/unavailable states.

Community reports are persisted locally first. Network upload is disabled by default and requires both user consent and an HTTPS `NV_COMMUNITY_REPORT_API_URL` configuration.

## External integrations

The build accepts these deployment-time settings when available:

- `NV_TRAFFIC_API_KEY`
- `NV_GOOGLE_MAPS_API_KEY`
- `NV_CLOUD_API_URL`
- `NV_CODE_REGISTRY_URL`
- `NV_VALHALLA_API_URL`
- `NV_COMMUNITY_REPORT_API_URL`

GTFS/GTFS-RT and taxi booking do not have a supplied production feed/schema/credential in this repository. Production defaults therefore report those providers as unavailable instead of fabricating live data. Deterministic transit/taxi mocks are explicitly labelled `mock-non-live` and `mock-non-bookable` and are for tests/demo only.

The EV charging planner can determine when charging is required and rank compatible known charging candidates. Automatic provider-backed multi-stop charging itinerary insertion requires a real charger availability/provider integration and is not represented as live functionality when that provider is absent.

Privacy consent for location history and analytics is stored fail-closed (disabled by default). The repository does not enable a location-history or analytics collection SDK merely because a consent toggle exists.

## Final verification gate

A revision is considered complete for this frozen Android scope only after the exact revision passes the Android CI build job and the Android 16 compatibility job. Production signing additionally requires the configured release keystore/certificate secrets and the production service configuration expected by the signed-release workflow.
