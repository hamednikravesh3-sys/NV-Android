package ir.nv.navigation.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NvRegistryCodeRangeTest {
    @Test
    fun registryRangeIsDistinctFromOfflineAndOsmRanges() {
        assertFalse(PlaceCodes.isRegistryCode(1L))
        assertTrue(PlaceCodes.isRegistryCode(5_000_000_000_000L))
        assertTrue(PlaceCodes.isRegistryCode(5_999_999_999_999L))
        assertFalse(PlaceCodes.isRegistryCode(6_000_000_000_000L))
        assertFalse(PlaceCodes.isRegistryCode(7_000_000_000_001L))
    }

    @Test
    fun registryCodeParticipatesInOnlineIdentityDetection() {
        val identity = PlaceCodes.onlineIdentity(5_000_000_000_123L)
        assertTrue(identity != null && identity.osmType == "nv" && identity.osmId == 5_000_000_000_123L)
    }
}
