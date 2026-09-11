package ir.nv.navigation.navigation

import ir.nv.navigation.BuildConfig
import ir.nv.navigation.ai.AdaptiveEtaPredictor
import ir.nv.navigation.ai.HistoricalTrafficPredictor
import ir.nv.navigation.ai.NvRoutePredictionEngine
import ir.nv.navigation.ai.route.NvAdaptiveRouteRanker
import ir.nv.navigation.ai.route.NvPredictiveRouteOptimizer
import ir.nv.navigation.navigation.guidance.GuidanceEngine
import ir.nv.navigation.navigation.mapmatching.MapMatchingEngine
import ir.nv.navigation.navigation.mapmatching.PassThroughMapMatchingEngine
import ir.nv.navigation.navigation.valhalla.ValhallaMapMatchingEngine
import ir.nv.navigation.online.OnlineNavigationService
import ir.nv.navigation.places.AheadEngine
import ir.nv.navigation.routing.AStarRouter
import ir.nv.navigation.traffic.LiveTrafficService
import ir.nv.navigation.weather.WeatherRouteSignalProvider

class NvNavigationPlatform(
    onlineService: OnlineNavigationService,
    routerProvider: () -> AStarRouter?,
    liveTrafficService: LiveTrafficService,
    val mapMatchingEngine: MapMatchingEngine = defaultMapMatchingEngine()
) {
    private val trafficProvider = LiveTrafficProviderAdapter(liveTrafficService)
    val predictionEngine = NvRoutePredictionEngine(
        etaPredictor = AdaptiveEtaPredictor(),
        trafficPredictor = HistoricalTrafficPredictor(emptyList())
    )

    val routeCoordinator = HybridRouteCoordinator(
        onlineProvider = OnlineRouteProviderAdapter(onlineService),
        offlineProvider = OfflineRouteProviderAdapter(routerProvider),
        trafficProvider = trafficProvider,
        ranker = NvAdaptiveRouteRanker(),
        signalProvider = WeatherRouteSignalProvider(),
        predictiveOptimizer = NvPredictiveRouteOptimizer(predictionEngine)
    )

    val reroutePolicy = ContinuousReroutePolicy()

    val continuousRerouteEngine = ContinuousRerouteEngine(
        coordinator = routeCoordinator,
        trafficProvider = trafficProvider,
        policy = reroutePolicy
    )

    val guidanceEngine = GuidanceEngine()
    val aheadEngine = AheadEngine(maxItems = 16)

    companion object {
        internal fun defaultMapMatchingEngine(): MapMatchingEngine {
            val endpoint = BuildConfig.VALHALLA_API_URL.trim()
            return if (endpoint.isNotEmpty()) ValhallaMapMatchingEngine(endpoint)
            else PassThroughMapMatchingEngine()
        }
    }
}
