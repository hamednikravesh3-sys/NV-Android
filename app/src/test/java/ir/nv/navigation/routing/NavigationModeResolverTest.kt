package ir.nv.navigation.routing

import ir.nv.navigation.core.RouteSource
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationModeResolverTest {
    @Test
    fun online_is_default_even_when_offline_map_is_installed() {
        assertEquals(
            RouteSource.ONLINE,
            NavigationModeResolver.preferredSource(
                onlineAvailable = true,
                offlineReady = true,
                preferOffline = false
            )
        )
    }

    @Test
    fun offline_is_automatic_when_network_is_lost() {
        assertEquals(
            RouteSource.OFFLINE,
            NavigationModeResolver.preferredSource(
                onlineAvailable = false,
                offlineReady = true,
                preferOffline = false
            )
        )
    }

    @Test
    fun manual_offline_preference_wins() {
        assertEquals(
            RouteSource.OFFLINE,
            NavigationModeResolver.preferredSource(
                onlineAvailable = true,
                offlineReady = true,
                preferOffline = true
            )
        )
    }

    @Test
    fun online_is_used_when_offline_is_requested_but_not_downloaded() {
        assertEquals(
            RouteSource.ONLINE,
            NavigationModeResolver.preferredSource(
                onlineAvailable = true,
                offlineReady = false,
                preferOffline = true
            )
        )
    }

    @Test
    fun no_source_without_network_or_download() {
        assertEquals(
            RouteSource.NONE,
            NavigationModeResolver.preferredSource(
                onlineAvailable = false,
                offlineReady = false,
                preferOffline = false
            )
        )
    }
}
