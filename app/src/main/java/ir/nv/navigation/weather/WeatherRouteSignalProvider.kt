package ir.nv.navigation.weather

import ir.nv.navigation.core.Route
import ir.nv.navigation.navigation.RouteIntelligenceContext
import ir.nv.navigation.navigation.RouteSignalProvider
import ir.nv.navigation.navigation.RouteSignals

class WeatherRouteSignalProvider(
    private val service: WeatherAlertService = WeatherAlertService()
) : RouteSignalProvider {
    override suspend fun signals(route: Route, context: RouteIntelligenceContext): RouteSignals {
        val notices = service.alertsAhead(route)
        if (notices.isEmpty()) return RouteSignals()
        val penalty = notices
            .mapNotNull { notice -> penaltyFromDetail(notice.detail) }
            .maxOrNull()
            ?: return RouteSignals()
        return RouteSignals(weatherPenalty = penalty)
    }

    private fun penaltyFromDetail(detail: String): Double? = when {
        "رعدوبرق" in detail -> 1.00
        "بارش برف" in detail -> 0.90
        "دید کمتر" in detail -> 0.90
        "تندباد" in detail -> 0.85
        "بارش شدید" in detail -> 0.80
        "بارندگی" in detail -> 0.55
        else -> null
    }
}
