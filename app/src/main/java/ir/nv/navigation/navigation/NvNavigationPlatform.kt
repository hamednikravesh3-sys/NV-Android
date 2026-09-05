package ir.nv.navigation.navigation

import ir.nv.navigation.ai.route.NvAdaptiveRouteRanker
import ir.nv.navigation.online.OnlineNavigationService
import ir.nv.navigation.routing.AStarRouter
import ir.nv.navigation.traffic.LiveTrafficService

class NvNavigationPlatform(
    onlineService: OnlineNavigationService,
    routerProvider: () -> AStarRouter?,
    liveTrafficService: LiveTrafficService
) {
    val routeCoordinator = HybridRouteCoordinator(
        onlineProvider = OnlineRouteProviderAdapter(onlineService),
        offlineProvider = OfflineRouteProviderAdapter(routerProvider),
        trafficProvider = LiveTrafficProviderAdapter(liveTrafficService),
        ranker = NvAdaptiveRouteRanker()
    )

    val reroutePolicy = ContinuousReroutePolicy()
}
