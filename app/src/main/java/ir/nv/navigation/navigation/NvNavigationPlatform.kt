package ir.nv.navigation.navigation

import ir.nv.navigation.ai.route.NvAdaptiveRouteRanker
import ir.nv.navigation.online.OnlineNavigationService
import ir.nv.navigation.routing.AStarRouter
import ir.nv.navigation.traffic.LiveTrafficService
import ir.nv.navigation.weather.WeatherRouteSignalProvider

class NvNavigationPlatform(
    onlineService: OnlineNavigationService,
    routerProvider: () -> AStarRouter?,
    liveTrafficService: LiveTrafficService
) {
    private val trafficProvider = LiveTrafficProviderAdapter(liveTrafficService)

    val routeCoordinator = HybridRouteCoordinator(
        onlineProvider = OnlineRouteProviderAdapter(onlineService),
        offlineProvider = OfflineRouteProviderAdapter(routerProvider),
        trafficProvider = trafficProvider,
        ranker = NvAdaptiveRouteRanker(),
        signalProvider = WeatherRouteSignalProvider()
    )

    val reroutePolicy = ContinuousReroutePolicy()

    val continuousRerouteEngine = ContinuousRerouteEngine(
        coordinator = routeCoordinator,
        trafficProvider = trafficProvider,
        policy = reroutePolicy
    )
}
