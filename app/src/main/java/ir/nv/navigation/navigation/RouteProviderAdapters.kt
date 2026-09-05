package ir.nv.navigation.navigation

import ir.nv.navigation.online.OnlineNavigationService
import ir.nv.navigation.routing.AStarRouter
import ir.nv.navigation.traffic.LiveTrafficService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OnlineRouteProviderAdapter(
    private val service: OnlineNavigationService
) : RouteProvider {
    override suspend fun routes(request: RouteRequest) =
        service.routes(request.origin, request.destination)
}

class OfflineRouteProviderAdapter(
    private val routerProvider: () -> AStarRouter?
) : RouteProvider {
    override suspend fun routes(request: RouteRequest) = withContext(Dispatchers.Default) {
        routerProvider()?.routes(request.origin, request.destination).orEmpty()
    }
}

class LiveTrafficProviderAdapter(
    private val service: LiveTrafficService
) : TrafficProvider {
    override suspend fun traffic(route: ir.nv.navigation.core.Route) = withContext(Dispatchers.IO) {
        service.summary(route)
    }
}
