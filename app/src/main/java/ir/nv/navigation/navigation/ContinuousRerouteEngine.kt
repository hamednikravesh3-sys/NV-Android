package ir.nv.navigation.navigation

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route

class ContinuousRerouteEngine(
    private val coordinator: HybridRouteCoordinator,
    private val trafficProvider: TrafficProvider,
    private val policy: ContinuousReroutePolicy = ContinuousReroutePolicy()
) {
    data class CheckRequest(
        val currentPosition: Coordinate,
        val destination: Coordinate,
        val currentRoute: Route,
        val currentRemainingSeconds: Double,
        val previousTrafficDelaySeconds: Double,
        val onlineAvailable: Boolean,
        val offlineAvailable: Boolean,
        val preferOffline: Boolean,
        val lastRerouteMillis: Long,
        val offRoute: Boolean = false,
        val currentRouteBlocked: Boolean = false,
        val profile: RouteProfile = RouteProfile.SMART
    )

    data class Result(
        val decision: ContinuousReroutePolicy.Decision,
        val replacement: RouteCandidate? = null,
        val currentTrafficDelaySeconds: Double = 0.0
    )

    suspend fun check(
        request: CheckRequest,
        context: RouteIntelligenceContext = RouteIntelligenceContext(request.profile)
    ): Result {
        val currentTraffic = if (request.onlineAvailable) {
            runCatching { trafficProvider.traffic(request.currentRoute) }.getOrNull()
        } else null
        val currentDelay = currentTraffic?.delaySeconds?.coerceAtLeast(0.0) ?: 0.0

        val plan = coordinator.plan(
            RouteRequest(
                origin = request.currentPosition,
                destination = request.destination,
                profile = request.profile,
                preferOffline = request.preferOffline,
                onlineAvailable = request.onlineAvailable,
                offlineAvailable = request.offlineAvailable
            ),
            context
        )
        val replacement = plan.selected
        val alternativeSeconds = replacement?.let { candidate ->
            candidate.route.travelSeconds + (candidate.traffic?.delaySeconds ?: 0.0)
        }
        val decision = policy.evaluate(
            nowMillis = System.currentTimeMillis(),
            lastRerouteMillis = request.lastRerouteMillis,
            offRoute = request.offRoute,
            currentRouteBlocked = request.currentRouteBlocked,
            previousTrafficDelaySeconds = request.previousTrafficDelaySeconds,
            currentTrafficDelaySeconds = currentDelay,
            currentRemainingSeconds = request.currentRemainingSeconds + currentDelay,
            bestAlternativeSeconds = alternativeSeconds
        )
        return Result(
            decision = decision,
            replacement = replacement.takeIf { decision.shouldReroute },
            currentTrafficDelaySeconds = currentDelay
        )
    }
}
