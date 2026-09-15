package ir.nv.navigation.navigation

import ir.nv.navigation.online.OnlineNavigationService
import ir.nv.navigation.routing.AStarRouter
import ir.nv.navigation.traffic.LiveTrafficService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OnlineRouteProviderAdapter(
    private val service: OnlineNavigationService
) : RouteProvider {
    override suspend fun routes(request: RouteRequest) = when (request.vehicleProfile) {
        VehicleProfile.CAR, VehicleProfile.EV -> service.routes(request.origin, request.destination)
        else -> emptyList()
    }
}

class FallbackRouteProvider(
    private val primary: RouteProvider?,
    private val fallback: RouteProvider
) : RouteProvider {
    override suspend fun routes(request: RouteRequest): List<ir.nv.navigation.core.Route> {
        val primaryRoutes = primary?.let { provider ->
            runCatching { provider.routes(request) }.getOrDefault(emptyList())
        }.orEmpty()
        return primaryRoutes.ifEmpty { fallback.routes(request) }
    }
}

class OfflineRouteProviderAdapter(
    private val routerProvider: () -> AStarRouter?
) : RouteProvider {
    override suspend fun routes(request: RouteRequest) = withContext(Dispatchers.Default) {
        if (request.vehicleProfile in setOf(VehicleProfile.TRUCK, VehicleProfile.TRANSIT)) {
            return@withContext emptyList()
        }
        routerProvider()?.routes(
            origin = request.origin,
            destination = request.destination,
            profile = request.profile,
            custom = request.custom,
            vehicleProfile = request.vehicleProfile,
            limit = 4
        ).orEmpty()
    }
}

class LiveTrafficProviderAdapter(
    private val service: LiveTrafficService
) : TrafficProvider {
    override suspend fun traffic(route: ir.nv.navigation.core.Route) = withContext(Dispatchers.IO) {
        service.summary(route)
    }
}
