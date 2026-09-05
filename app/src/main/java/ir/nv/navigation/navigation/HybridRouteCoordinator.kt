package ir.nv.navigation.navigation

import ir.nv.navigation.core.RouteSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class HybridRouteCoordinator(
    private val onlineProvider: RouteProvider,
    private val offlineProvider: RouteProvider,
    private val trafficProvider: TrafficProvider,
    private val ranker: RouteRanker
) {
    suspend fun plan(
        request: RouteRequest,
        context: RouteIntelligenceContext = RouteIntelligenceContext(request.profile)
    ): RoutePlan {
        val primaryOffline = request.preferOffline || !request.onlineAvailable
        var fallbackUsed = false
        var warning: String? = null

        val initial = if (primaryOffline) {
            if (request.offlineAvailable) offlineProvider.routes(request) else emptyList()
        } else {
            runCatching { onlineProvider.routes(request) }
                .onFailure { warning = it.message }
                .getOrDefault(emptyList())
        }

        val routes = when {
            initial.isNotEmpty() -> initial
            !primaryOffline && request.offlineAvailable -> {
                fallbackUsed = true
                offlineProvider.routes(request)
            }
            primaryOffline && request.onlineAvailable -> {
                fallbackUsed = true
                runCatching { onlineProvider.routes(request) }
                    .onFailure { warning = it.message }
                    .getOrDefault(emptyList())
            }
            else -> emptyList()
        }

        val source = when {
            routes.isEmpty() -> RouteSource.NONE
            primaryOffline && !fallbackUsed -> RouteSource.OFFLINE
            !primaryOffline && !fallbackUsed -> RouteSource.ONLINE
            primaryOffline && fallbackUsed -> RouteSource.ONLINE
            else -> RouteSource.OFFLINE
        }

        val traffic = if (source == RouteSource.ONLINE && request.onlineAvailable) {
            coroutineScope {
                routes.map { route -> async { runCatching { trafficProvider.traffic(route) }.getOrNull() } }
                    .map { it.await() }
            }
        } else List(routes.size) { null }

        val candidates = routes.mapIndexed { index, route ->
            RouteCandidate(route = route, source = source, traffic = traffic.getOrNull(index))
        }
        val ranked = ranker.rank(candidates, context)
        return RoutePlan(
            candidates = ranked,
            selectedIndex = if (ranked.isEmpty()) -1 else 0,
            fallbackUsed = fallbackUsed,
            warning = warning
        )
    }
}
