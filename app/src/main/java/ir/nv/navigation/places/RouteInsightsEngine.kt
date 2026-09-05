package ir.nv.navigation.places

import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.core.TrafficSegment
import ir.nv.navigation.core.TrafficSummary

fun interface RouteNoticeProvider {
    suspend fun notices(route: Route): List<RouteNotice>
}

fun interface RouteTrafficProvider {
    suspend fun traffic(route: Route): RouteTrafficSnapshot?
}

data class RouteTrafficSnapshot(
    val summary: TrafficSummary,
    val segments: List<TrafficSegment> = emptyList()
)

data class RouteInsights(
    val notices: List<RouteNotice>,
    val traffic: TrafficSummary? = null,
    val trafficSegments: List<TrafficSegment> = emptyList()
)

class RouteInsightsEngine(
    private val offlinePlaces: RouteNoticeProvider,
    private val onlinePlaces: RouteNoticeProvider,
    private val weather: RouteNoticeProvider,
    private val traffic: RouteTrafficProvider,
    private val aheadEngine: AheadEngine = AheadEngine(maxItems = 16)
) {
    suspend fun load(route: Route, onlineAvailable: Boolean): RouteInsights {
        val offline = runCatching { offlinePlaces.notices(route) }.getOrDefault(emptyList())
        val remote = if (onlineAvailable) {
            runCatching { onlinePlaces.notices(route) }.getOrDefault(emptyList())
        } else emptyList()
        val weatherNotices = if (onlineAvailable) {
            runCatching { weather.notices(route) }.getOrDefault(emptyList())
        } else emptyList()
        val trafficSnapshot = if (onlineAvailable) {
            runCatching { traffic.traffic(route) }.getOrNull()
        } else null

        return RouteInsights(
            notices = aheadEngine.rank(weatherNotices + offline + remote),
            traffic = trafficSnapshot?.summary,
            trafficSegments = trafficSnapshot?.segments.orEmpty()
        )
    }
}
