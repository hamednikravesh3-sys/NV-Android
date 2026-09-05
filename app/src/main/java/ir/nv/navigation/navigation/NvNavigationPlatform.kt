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
    val routeCoordinator = HybridRouteCoordinator(
        onlineProvider = OnlineRouteProviderAdapter(onlineService),
        offlineProvider = OfflineRouteProviderAdapter(routerProvider),
        trafficProvider = LiveTrafficProviderAdapter(liveTrafficService),
        ranker = NvAdaptiveRouteRanker(),
        signalProvider = WeatherRouteSignalProvider()
    )

    val reroutePolicy = ContinuousReroutePolicy()
}
