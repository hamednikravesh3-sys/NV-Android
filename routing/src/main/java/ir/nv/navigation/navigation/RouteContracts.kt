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
    SAFE,
    SCENIC,
    AVOID_TOLL,
    AVOID_HIGHWAY,
    AVOID_FERRY,
    CUSTOM,
    SMART
}

enum class VehicleProfile {
    CAR,
    MOTORCYCLE,
    TRUCK,
    EV,
    BICYCLE,
    WALKING,
    TRANSIT
}

data class TruckRestrictions(
    val heightMeters: Double? = null,
    val widthMeters: Double? = null,
    val weightTons: Double? = null,
    val lengthMeters: Double? = null,
    val hazardousCargo: Boolean = false
) {
    fun normalized(): TruckRestrictions = copy(
        heightMeters = heightMeters?.coerceIn(0.0, 6.0),
        widthMeters = widthMeters?.coerceIn(0.0, 4.0),
        weightTons = weightTons?.coerceIn(0.0, 80.0),
        lengthMeters = lengthMeters?.coerceIn(0.0, 30.0)
    )
}

data class EvRoutePreferences(
    val batteryPercent: Int = 80,
    val estimatedRangeKm: Double? = null,
    val consumptionWhPerKm: Double? = null,
    val minimumArrivalBatteryPercent: Int = 12,
    val connectorTypes: Set<String> = emptySet()
) {
    fun normalized(): EvRoutePreferences = copy(
        batteryPercent = batteryPercent.coerceIn(0, 100),
        estimatedRangeKm = estimatedRangeKm?.coerceAtLeast(0.0),
        consumptionWhPerKm = consumptionWhPerKm?.coerceAtLeast(0.0),
        minimumArrivalBatteryPercent = minimumArrivalBatteryPercent.coerceIn(0, 100),
        connectorTypes = connectorTypes.map(String::trim).filter(String::isNotEmpty).toSet()
    )

    fun usableRangeMeters(): Double? {
        val range = estimatedRangeKm?.takeIf { it > 0.0 } ?: return null
        val usableBattery = (batteryPercent - minimumArrivalBatteryPercent).coerceAtLeast(0)
        return range * 1_000.0 * usableBattery / 100.0
    }
}

data class CustomRoutePreferences(
    val avoidToll: Boolean = false,
    val avoidHighway: Boolean = false,
    val avoidFerry: Boolean = false,
    val timeWeight: Double = 0.55,
    val distanceWeight: Double = 0.20,
    val energyWeight: Double = 0.15,
    val roadQualityWeight: Double = 0.10
) {
    fun normalized(): CustomRoutePreferences {
        val values = listOf(timeWeight, distanceWeight, energyWeight, roadQualityWeight).map { it.coerceAtLeast(0.0) }
        val sum = values.sum().takeIf { it > 0.0 } ?: 1.0
        return copy(
            timeWeight = values[0] / sum,
            distanceWeight = values[1] / sum,
            energyWeight = values[2] / sum,
            roadQualityWeight = values[3] / sum
        )
    }
}

data class RouteRequest(
    val origin: Coordinate,
    val destination: Coordinate,
    val profile: RouteProfile = RouteProfile.SMART,
    val vehicleProfile: VehicleProfile = VehicleProfile.CAR,
    val preferOffline: Boolean = false,
    val onlineAvailable: Boolean = false,
    val offlineAvailable: Boolean = false,
    val custom: CustomRoutePreferences = CustomRoutePreferences(),
    val truck: TruckRestrictions = TruckRestrictions(),
    val ev: EvRoutePreferences = EvRoutePreferences()
)

data class RouteSignals(
    val roadQualityPenalty: Double? = null,
    val accidentRiskPenalty: Double? = null,
    val weatherPenalty: Double? = null,
    val restrictionPenalty: Double? = null
) {
    fun normalized(): RouteSignals = copy(
        roadQualityPenalty = roadQualityPenalty?.coerceIn(0.0, 1.0),
        accidentRiskPenalty = accidentRiskPenalty?.coerceIn(0.0, 1.0),
        weatherPenalty = weatherPenalty?.coerceIn(0.0, 1.0),
        restrictionPenalty = restrictionPenalty?.coerceIn(0.0, 1.0)
    )
}

data class RouteCandidate(
    val route: Route,
    val source: RouteSource,
    val traffic: TrafficSummary? = null,
    val signals: RouteSignals = RouteSignals(),
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

fun interface RouteSignalProvider {
    suspend fun signals(route: Route, context: RouteIntelligenceContext): RouteSignals
}

fun interface RouteRanker {
    fun rank(candidates: List<RouteCandidate>, context: RouteIntelligenceContext): List<RouteCandidate>
}

data class RouteIntelligenceContext(
    val profile: RouteProfile,
    val rainOrSnow: Boolean = false,
    val electricVehicle: Boolean = false,
    val preferHighways: Boolean = false,
    val avoidRisk: Boolean = true,
    val userTimePriority: Double = 0.5,
    val userEcoPriority: Double = 0.5
)
