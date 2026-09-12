# NV Production Release Runbook

This document defines the production-only inputs and verification gates required before NV can be declared a final installable release.

## Required production secrets

The `Signed Release APK` workflow fails closed unless all of the following are configured in GitHub Actions secrets:

- `NV_KEYSTORE_BASE64` — Base64 of the real production Android keystore.
- `NV_KEYSTORE_PASSWORD` — production keystore password.
- `NV_KEY_ALIAS` — alias of the production signing key.
- `NV_KEY_PASSWORD` — password of the production signing key.
- `NV_RELEASE_CERT_SHA256` — SHA-256 certificate fingerprint of that exact production signing key. Do not invent or replace this value with a debug certificate.
- `NV_CLOUD_API_URL` — public HTTPS base URL of the deployed NV Cloud Sync worker.
- `NV_CODE_REGISTRY_URL` — public HTTPS base URL of the deployed NV Code Registry worker.
- `NV_TRAFFIC_API_KEY` — production traffic-provider credential when live traffic is enabled for the release.

The release workflow validates the signing certificate in the keystore before building and validates the certificate embedded in the resulting APK again after signing.

## Production service deployment

The Cloud Sync worker is implemented in `backend/cloud-sync/worker.js` and uses its database schema from `backend/cloud-sync/schema.sql`.

The NV Code Registry worker is implemented in `backend/nv-code-registry/worker.js`. Its database must be initialized from `backend/nv-code-registry/schema.sql` and all files in `backend/nv-code-registry/migrations/` must be applied in order. Do not publish a fake D1 database ID or a placeholder production hostname in the repository.

Before starting the signed release workflow, deploy both workers to their real production origins and verify that the configured URLs are the exact public HTTPS origins intended for the Android production build. The release tooling rejects placeholders, private/local addresses, query strings, fragments, duplicate service origins, redirects, non-HTTPS URLs, and incompatible health responses.

## Required health contract

Both configured production services must pass `tools/production_service_probe.py` from the GitHub runner. The probe requires the service-specific `/health` endpoint to be reachable over HTTPS without redirecting and to return the expected JSON service identity and security/cache behavior.

A service that exists only on a developer machine, private network, preview deployment, or placeholder domain is not release-ready.

## Signed APK release gates

The `Signed Release APK` workflow is intentionally manual (`workflow_dispatch`) and must complete successfully for the exact commit intended for release. A successful run must prove all of the following:

1. Backend worker tests and release tooling tests pass.
2. Production configuration validation passes.
3. Both deployed production services pass the live health probe.
4. The production keystore certificate matches `NV_RELEASE_CERT_SHA256`.
5. Release unit tests and `assembleRelease` pass.
6. `apksigner verify` passes on the generated APK.
7. APK signing certificate SHA-256 matches the pinned production certificate.
8. APK identity is exactly `ir.nv.navigation`, versionCode `18`, versionName `0.17.0`, minSdk `29`, targetSdk `35`.
9. APK SHA-256 is generated and recorded.
10. The machine-readable release manifest matches the APK, Git SHA, SDK/version identity, SHA-256, and signing certificate.
11. The signed APK installs and launches successfully on Android 10 / API 29.
12. The signed APK installs and launches successfully on Android 15 / API 35.
13. No app fatal exception or ANR is detected by the release smoke tests.
14. The final signed artifact bundle uploads successfully.

## Final artifact acceptance

Do not declare Item 42 complete until the manual signed release workflow has produced the `NV-signed-release-apk-<git-sha>` artifact for the intended commit and that run has conclusion `success`.

The final bundle contains:

- `app-release.apk`
- `app-release.apk.sha256`
- `app-release-signature.txt`
- `app-release-badging.txt`
- `app-release-provenance.txt`
- `app-release-manifest.json`

The APK SHA-256 and certificate fingerprint in the manifest must agree with the generated APK and signature report. Keep the production keystore and passwords out of the repository and out of diagnostic artifacts.

## External validation after CI

CI establishes reproducible package identity, signing provenance, emulator installation/launch behavior, and production-service reachability. It does not replace physical-road testing, provider licensing, Google Play production checks, Play Integrity/purchase verification, or operational monitoring. Those remain deployment/operations responsibilities and must not be represented as completed by the build pipeline alone.
