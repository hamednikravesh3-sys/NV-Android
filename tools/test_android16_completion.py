from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')

class Android16CompletionTest(unittest.TestCase):
    def test_app_targets_android16_release_version(self):
        text = read('app/build.gradle.kts')
        self.assertRegex(text, r'compileSdk\s*=\s*36')
        self.assertRegex(text, r'targetSdk\s*=\s*36')
        self.assertRegex(text, r'versionCode\s*=\s*22')
        self.assertIn('versionName = "0.18.5"', text)
        self.assertIn('COMMUNITY_REPORT_API_URL', text)
        self.assertIn('NV_CODE_REGISTRY_URL', text)

    def test_ci_exercises_android16_instrumentation_and_16k_alignment(self):
        text = read('.github/workflows/android.yml')
        self.assertIn('platforms;android-36', text)
        self.assertIn('build-tools;36.0.0', text)
        self.assertIn(':app:assembleDebugAndroidTest', text)
        self.assertIn(':app:connectedDebugAndroidTest', text)
        self.assertRegex(text, r'zipalign[^\n]*-P 16')
        self.assertRegex(text, r'api-level:\s*36')
        test = read('app/src/androidTest/java/ir/nv/navigation/MainActivityAndroid16Test.kt')
        self.assertIn('targetSdkVersion', test)
        self.assertIn('LAYOUT_DIRECTION_RTL', test)

    def test_release_pipeline_is_android16_not_android15(self):
        text = read('.github/workflows/release.yml')
        self.assertIn('platforms;android-36', text)
        self.assertIn('build-tools;36.0.0', text)
        self.assertRegex(text, r'api-level:\s*36')
        self.assertIn("versionCode='21'", text)
        self.assertIn("versionName='0.18.2'", text)
        self.assertNotRegex(text, r'android-35|build-tools;35|api-level:\s*35')

    def test_navigation_gates_and_vehicle_constraints_are_integrated(self):
        text = read('app/src/main/java/ir/nv/navigation/ui/NvViewModel.kt')
        self.assertIn('OffRouteConfirmationGate', text)
        self.assertIn('ArrivalConfirmationGate', text)
        self.assertIn('offRouteConfirmationGate.observe', text)
        self.assertIn('arrivalConfirmationGate.observe', text)
        self.assertIn('profile = snapshot.routeProfile', text)
        self.assertIn('vehicleProfile = snapshot.vehicleProfile', text)
        self.assertIn('truck = snapshot.truckRestrictions', text)
        self.assertIn('ev = snapshot.evRoutePreferences', text)
        self.assertIn('RouteLocalMapMatcher.match', text)

    def test_current_location_route_is_atomic_and_home_has_no_pair_autoroute(self):
        vm = read('app/src/main/java/ir/nv/navigation/ui/NvViewModel.kt')
        home = read('app/src/main/java/ir/nv/navigation/ui/RahnamaHome.kt')
        self.assertIn('fun routeFromCurrentLocationTo(', vm)
        self.assertIn('DEVICE_LOCATION_SNAPSHOT_CATEGORY', vm)
        self.assertIn('viewModel.routeFromCurrentLocationTo(place)', home)
        self.assertNotIn('lastPair', home)
        self.assertNotIn('pairKey', home)
        self.assertNotIn('onRefineDestination', home)

    def test_active_nearby_and_emergency_use_application_layer(self):
        v14 = read('app/src/main/java/ir/nv/navigation/ui/NvReferenceV14.kt')
        emergency = read('app/src/main/java/ir/nv/navigation/ui/RahnamaEmergencyUi.kt')
        self.assertNotIn('NearbySearchCoordinator(', v14)
        self.assertNotIn('OnlinePlacesService()', emergency)
        self.assertIn('discoverNearby', v14)
        self.assertIn('discoverEmergency', emergency)
        self.assertIn('NearbyCategory.EMERGENCY', read('app/src/main/java/ir/nv/navigation/ui/RahnamaHome.kt'))
        self.assertIn('NearbyCategory.HOSPITAL', read('app/src/main/java/ir/nv/navigation/ui/RahnamaHome.kt'))
        self.assertIn('NearbyCategory.PHARMACY', read('app/src/main/java/ir/nv/navigation/ui/RahnamaHome.kt'))
        self.assertIn('NearbyCategory.PARKING', read('app/src/main/java/ir/nv/navigation/ui/RahnamaHome.kt'))

    def test_parking_handoff_is_explicit(self):
        text = read('app/src/main/java/ir/nv/navigation/ui/NvViewModel.kt')
        self.assertRegex(text, r'fun searchParkingNearDestination\([^)]*\)')
        self.assertIn('fun routeToParking(', text)
        self.assertIn('fun continueWalkingAfterParking()', text)
        self.assertIn('VehicleProfile.WALKING', text)
        self.assertIn('parkingFinalDestination', text)

    def test_privacy_defaults_fail_closed_and_community_upload_is_opt_in(self):
        privacy = read('app/src/main/java/ir/nv/navigation/privacy/PrivacySettings.kt')
        community = read('app/src/main/java/ir/nv/navigation/community/CommunityReports.kt')
        vm = read('app/src/main/java/ir/nv/navigation/ui/NvViewModel.kt')
        self.assertRegex(privacy, r'strictMode:\s*Boolean\s*=\s*true')
        for setting in ['locationHistory', 'analytics', 'communityUploads', 'cloudSync']:
            self.assertRegex(privacy, rf'{setting}:\s*Boolean\s*=\s*false')
        self.assertIn('UnavailableCommunityReportRemote', community)
        self.assertIn('communityUploads', vm)
        self.assertIn('COMMUNITY_REPORT_API_URL', vm)

    def test_smart_mobility_does_not_fake_live_providers(self):
        text = read('app/src/main/java/ir/nv/navigation/smart/SmartMobility.kt')
        self.assertIn('UnavailableTransitRealtimeProvider', text)
        self.assertIn('UnavailableTaxiProvider', text)
        self.assertIn('mock-non-live', text)
        self.assertIn('mock-non-bookable', text)
        engine_ctor = re.search(r'class SmartMobilityEngine\((.*?)\) \{', text, re.S)
        self.assertIsNotNone(engine_ctor)
        ctor = engine_ctor.group(1)
        self.assertIn('UnavailableTransitRealtimeProvider', ctor)
        self.assertIn('UnavailableTaxiProvider', ctor)

    def test_real_world_feedback_fixes_are_wired_to_active_shell(self):
        vm = read('app/src/main/java/ir/nv/navigation/ui/NvViewModel.kt')
        home = read('app/src/main/java/ir/nv/navigation/ui/RahnamaHome.kt')
        v15 = read('app/src/main/java/ir/nv/navigation/ui/NvReferenceV15.kt')
        v16 = read('app/src/main/java/ir/nv/navigation/ui/NvReferenceV16.kt')
        v17 = read('app/src/main/java/ir/nv/navigation/ui/NvReferenceV17.kt')
        assistant = read('app/src/main/java/ir/nv/navigation/ui/RahnamaSmartRouteAssistant.kt')
        qr = read('app/src/main/java/ir/nv/navigation/ui/NvQrScannerOverlay.kt')
        driving = read('app/src/main/java/ir/nv/navigation/ui/RahnamaLiveDrivingScreen.kt')
        self.assertIn('locationFailureMessage()', vm)
        self.assertIn('isLocationEnabled()', vm)
        self.assertNotIn('موقعیت فعلی پیدا نشد؛ GPS را روشن کنید', vm)
        self.assertIn('ensureHomeLocationTracking()', vm)
        self.assertIn('text = "SOS"', v16)
        self.assertIn('ProvinceDownloadOverlay(', home)
        self.assertIn('شهرستان‌های هر استان', home)
        self.assertIn('RahnamaSmartRouteAssistant(', v17)
        self.assertNotIn('RahnamaCodePickerOverlay(', v17)
        self.assertIn('viewModel.routeFromCurrentLocationTo(candidate, selectedVehicle)', assistant)
        self.assertIn('registry.allocateOnline', qr)
        self.assertNotIn('OutlinedTextField', qr)
        self.assertIn('val activeRouteOnly = listOf(route)', driving)
        self.assertIn('selectedRouteIndex = 0', driving)
        self.assertIn('if (state.offRoute)', driving)
        self.assertIn('RahnamaLiveDrivingScreen(', v15)

    def test_location_provider_rejects_loose_fixes_and_refines_fast(self):
        location = read('core/location/src/main/java/ir/nv/navigation/location/DeviceLocationProvider.kt')
        self.assertIn('MAX_CURRENT_LOCATION_ACCURACY_METERS = 10f', location)
        self.assertIn('MAX_NAVIGATION_ACCURACY_METERS = 20f', location)
        self.assertIn('if (accuracy > navigationAccuracyLimit) return', location)
        self.assertIn('NAVIGATION_UPDATE_MS = 500L', location)
        self.assertIn('return accuracyPenalty + ageSeconds * 0.5', location)
        self.assertIn('fun updates(): Flow<NavigationFix>', location)

if __name__ == '__main__':
    unittest.main()
