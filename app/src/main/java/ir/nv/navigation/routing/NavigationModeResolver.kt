package ir.nv.navigation.routing

import ir.nv.navigation.core.RouteSource

object NavigationModeResolver {
    fun preferredSource(
        onlineAvailable: Boolean,
        offlineReady: Boolean,
        preferOffline: Boolean
    ): RouteSource = when {
        offlineReady -> RouteSource.OFFLINE
        onlineAvailable -> RouteSource.ONLINE
        else -> RouteSource.NONE
    }
}
