package ir.nv.navigation.navigation

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteSource
import ir.nv.navigation.core.TrafficSummary

enum class RouteProfile {
    FASTEST,
    SHORTEST,
    LOW_TRAFFIC,
    ECO,
    SCENIC,
    AVOID_TOLL,
    AVOID_HIGHWAY,
    SMART
}

data class RouteRequest(
    val origin: Coordinate,
    val destination: Coordinate,
    val profile: RouteProfile = RouteProfile.SMART,
    val preferOffline: Boolean = false,
    val onlineAvailable: Boolean = false,
    val offlineAvailable: Boolean = false
)

data class RouteCandidate(
    val route: Route,
    val source: RouteSource,
    val traffic: TrafficSummary? = null,
    val score: Double? = null
)

data class RoutePlan(
    val candidates: List<RouteCandidate>,
    val selectedIndex: Int = 0,
    val fallbackUsed: Boolean = false,
    val warning: String? = null
) {
    val selected: RouteCandidate? get() = candidates.getOrNull(selectedIndex)
}

fun interface RouteProvider {
    suspend fun routes(request: RouteRequest): List<Route>
}

fun interface TrafficProvider {
    suspend fun traffic(route: Route): TrafficSummary?
}

fun interface RouteRanker {
    fun rank(candidates: List<RouteCandidate>, context: RouteIntelligenceContext): List<RouteCandidate>
}

data class RouteIntelligenceContext(
    val profile: RouteProfile,
    val rainOrSnow: Boolean = false,
    val electricVehicle: Boolean = false,
    val preferHighways: Boolean = false,
    val avoidRisk: Boolean = true
)
