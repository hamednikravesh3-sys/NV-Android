package ir.nv.navigation.navigation

import ir.nv.navigation.BuildConfig
import ir.nv.navigation.cloud.NvCloudSyncClient
import ir.nv.navigation.offline.OfflinePackCatalog
import ir.nv.navigation.offline.OfflinePackUpdatePolicy
import ir.nv.navigation.routing.NavigationCameraPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Batch25To30IntegrationTest {
    @Test
    fun provinceCatalogHasIndependentDownloadableRegions() {
        val provinces = OfflinePackCatalog.provinces
        assertTrue(provinces.size >= 8)
        assertEquals(provinces.size, provinces.map { it.id }.distinct().size)
        assertTrue(provinces.all { it.estimatedSizeMb > 0 && it.id != "iran" })
        assertFalse(BuildConfig.PROVINCE_PACK_BASE_URL.isBlank())
    }

    @Test
    fun updatePolicyDetectsSameVersionChecksumChange() {
        val installed = OfflinePackUpdatePolicy.InstalledVersion(4, "a".repeat(64), 10)
        val remote = OfflinePackUpdatePolicy.RemoteVersion(4, "b".repeat(64), 1024, 20)
        assertTrue(OfflinePackUpdatePolicy.decide(installed, remote) is OfflinePackUpdatePolicy.Decision.UpdateAvailable)
    }

    @Test
    fun navigationCameraSupportsDrivingZoomPolicyFor3dMap() {
        assertEquals(19, NavigationCameraPolicy.zoomLevel(25, 80.0))
        assertEquals(15, NavigationCameraPolicy.zoomLevel(110, 2_000.0))
    }

    @Test
    fun weatherAndCloudHaveExplicitRuntimeConfigurationContracts() {
        assertTrue(BuildConfig.WEATHER_API_URL.startsWith("https://"))
        val client = NvCloudSyncClient()
        // CI intentionally has no production cloud URL; the transport must fail closed, not use a local fake.
        assertEquals(BuildConfig.CLOUD_API_URL.isNotBlank(), client.isConfigured())
    }
}
