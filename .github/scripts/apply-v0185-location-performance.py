from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}: {old!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


# Version bump so the fixed build installs over v0.18.4.
replace_once("app/build.gradle.kts", 'versionCode = 23', 'versionCode = 24')
replace_once("app/build.gradle.kts", 'versionName = "0.18.4"', 'versionName = "0.18.5"')

# Keep the location module aligned with Android 16 builds.
replace_once("core/location/build.gradle.kts", 'compileSdk = 35', 'compileSdk = 36')

# The previous release intentionally rejected almost every fix above 10-12 m.
# On real phones that can make the app appear unable to locate the user at all.
# Accept a provisional fine-location fix quickly, keep streaming better fixes, and
# let route-local map matching stabilize navigation to the driven road.
location_path = "core/location/src/main/java/ir/nv/navigation/location/DeviceLocationProvider.kt"
for old, new in [
    ('const val LOCATION_COLLECTION_WINDOW_MS = 15_000L', 'const val LOCATION_COLLECTION_WINDOW_MS = 5_000L'),
    ('const val TARGET_ACCURACY_METERS = 5f', 'const val TARGET_ACCURACY_METERS = 10f'),
    ('const val EXCELLENT_ACCURACY_METERS = 3f', 'const val EXCELLENT_ACCURACY_METERS = 6f'),
    ('const val ACCEPTABLE_LAST_KNOWN_ACCURACY_METERS = 6f', 'const val ACCEPTABLE_LAST_KNOWN_ACCURACY_METERS = 20f'),
    ('const val MAX_CURRENT_LOCATION_ACCURACY_METERS = 10f', 'const val MAX_CURRENT_LOCATION_ACCURACY_METERS = 60f'),
    ('const val GOOD_NAVIGATION_ACCURACY_METERS = 10f', 'const val GOOD_NAVIGATION_ACCURACY_METERS = 20f'),
    ('const val MAX_NAVIGATION_ACCURACY_METERS = 12f', 'const val MAX_NAVIGATION_ACCURACY_METERS = 65f'),
    ('const val ABSOLUTE_MAX_ACCURACY_METERS = 35f', 'const val ABSOLUTE_MAX_ACCURACY_METERS = 120f'),
    ('const val FRESH_SAMPLE_AGE_MS = 5_000L', 'const val FRESH_SAMPLE_AGE_MS = 8_000L'),
    ('const val MAX_CURRENT_FIX_AGE_MS = 10_000L', 'const val MAX_CURRENT_FIX_AGE_MS = 15_000L'),
    ('const val MAX_LAST_KNOWN_AGE_MS = 12_000L', 'const val MAX_LAST_KNOWN_AGE_MS = 30_000L'),
    ('const val RECENT_LOCATION_MS = 10_000L', 'const val RECENT_LOCATION_MS = 30_000L'),
]:
    replace_once(location_path, old, new)

# Home should show the best available fine fix instead of showing no location.
# The UI already exposes the accuracy in metres; once a better fix arrives the
# degradation guard prevents a weaker later sample from pulling the marker away.
vm_path = "app/src/main/java/ir/nv/navigation/ui/NvViewModel.kt"
replace_once(vm_path, 'const val HOME_LOCATION_ACCURACY_METERS = 12f', 'const val HOME_LOCATION_ACCURACY_METERS = 60f')
replace_once(vm_path, 'const val HOME_ACCURACY_DEGRADATION_METERS = 4f', 'const val HOME_ACCURACY_DEGRADATION_METERS = 8f')

# Reduce map work on the main thread. Bearing is irrelevant on the browsing/home map,
# yet the old code treated every heading change as a full marker + camera update.
map_path = "app/src/main/java/ir/nv/navigation/map/OnlineIranMap.kt"
replace_once(
    map_path,
    '''        val locationChanged = currentLocation != this.currentLocation ||\n            navigationActive != this.navigationActive ||\n            bearingDegrees != this.bearingDegrees''',
    '''        val bearingChanged = navigationActive && bearingDegrees != this.bearingDegrees\n        val locationChanged = currentLocation != this.currentLocation ||\n            navigationActive != this.navigationActive ||\n            bearingChanged'''
)
replace_once(
    map_path,
    '''            navigationZoomLevel != this.navigationZoomLevel ||\n            navigationRecenterToken != this.navigationRecenterToken ||\n            bearingDegrees != this.bearingDegrees''',
    '''            navigationZoomLevel != this.navigationZoomLevel ||\n            navigationRecenterToken != this.navigationRecenterToken ||\n            bearingChanged'''
)
replace_once(map_path, '            minZoom = 15f', '            minZoom = 17.5f')
replace_once(map_path, 'const val CAMERA_ANIMATION_MS = 420', 'const val CAMERA_ANIMATION_MS = 180')
replace_once(map_path, 'const val BROWSE_TILT = 42.0', 'const val BROWSE_TILT = 0.0')
replace_once(map_path, 'const val NAVIGATION_TILT = 58.0', 'const val NAVIGATION_TILT = 52.0')

# Avoid re-animating the home camera for sub-street GPS jitter. Navigation still
# follows every accepted fix; the home camera only moves when the device moved enough.
old_camera = '''        val location = currentLocation ?: return\n        val mustRecenter = navigationRecenterToken != lastRecenterToken\n        if (!followLocation && !mustRecenter) return\n        lastRecenterToken = navigationRecenterToken\n        val position = CameraPosition.Builder()\n            .target(LatLng(location.latitude, location.longitude))\n            .zoom(if (navigationActive) navigationZoomLevel.toDouble() else HOME_ZOOM)\n            .tilt(if (navigationActive) NAVIGATION_TILT else BROWSE_TILT)\n            .bearing(if (navigationActive && bearingDegrees.isFinite()) bearingDegrees.toDouble() else readyMap.cameraPosition.bearing)\n            .build()\n        readyMap.easeCamera(CameraUpdateFactory.newCameraPosition(position), CAMERA_ANIMATION_MS)'''
new_camera = '''        val location = currentLocation ?: return\n        val mustRecenter = navigationRecenterToken != lastRecenterToken\n        if (!followLocation && !mustRecenter) return\n        if (!navigationActive && !mustRecenter) {\n            val cameraTarget = readyMap.cameraPosition.target\n            val cameraCoordinate = Coordinate(cameraTarget.latitude, cameraTarget.longitude)\n            if (coordinateDistanceMeters(cameraCoordinate, location) < HOME_CAMERA_JITTER_METERS) return\n        }\n        lastRecenterToken = navigationRecenterToken\n        val position = CameraPosition.Builder()\n            .target(LatLng(location.latitude, location.longitude))\n            .zoom(if (navigationActive) navigationZoomLevel.toDouble() else HOME_ZOOM)\n            .tilt(if (navigationActive) NAVIGATION_TILT else BROWSE_TILT)\n            .bearing(if (navigationActive && bearingDegrees.isFinite()) bearingDegrees.toDouble() else readyMap.cameraPosition.bearing)\n            .build()\n        readyMap.easeCamera(CameraUpdateFactory.newCameraPosition(position), CAMERA_ANIMATION_MS)'''
replace_once(map_path, old_camera, new_camera)
replace_once(map_path, 'const val MAX_ANIMATED_JUMP_METERS = 120.0', 'const val MAX_ANIMATED_JUMP_METERS = 120.0\n        const val HOME_CAMERA_JITTER_METERS = 8.0')

print("v0.18.5 location/performance patch applied")
