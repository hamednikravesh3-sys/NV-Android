package ir.nv.navigation.routing

import ir.nv.navigation.core.RouteSource

/**
 * Chooses the map/navigation data source.
 *
 * Default behavior is online-first. A downloaded offline pack is used when the
 * user explicitly requests offline mode or when the network is unavailable.
 */
object NavigationModeResolver {
    fun preferredSource(
        onlineAvailable: Boolean,
        offlineReady: Boolean,
        preferOffline: Boolean
    ): RouteSource = when {
        preferOffline && offlineReady -> RouteSource.OFFLINE
        onlineAvailable -> RouteSource.ONLINE
        offlineReady -> RouteSource.OFFLINE
        else -> RouteSource.NONE
    }
}
