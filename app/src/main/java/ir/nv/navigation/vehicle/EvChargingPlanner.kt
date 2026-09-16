package ir.nv.navigation.vehicle

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.navigation.EvRoutePreferences
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class ChargingStationMetadata(
    val connectors: Set<String> = emptySet(),
    val availablePorts: Int? = null,
    val powerKw: Double? = null,
    val provider: String? = null
)

data class ChargingStopCandidate(
    val place: Place,
    val distanceFromOriginMeters: Double,
    val detourHeuristicMeters: Double,
    val metadata: ChargingStationMetadata?,
    val connectorCompatible: Boolean
)

data class ChargingNeed(
    val required: Boolean,
    val usableRangeMeters: Double?,
    val routeDistanceMeters: Double,
    val reasonFa: String
)

/** Pure planner: recommends compatible candidates but never invents live charger availability. */
object EvChargingPlanner {
    fun need(routeDistanceMeters: Double, preferences: EvRoutePreferences): ChargingNeed {
        val usable = preferences.normalized().usableRangeMeters()
        return when {
            usable == null -> ChargingNeed(false, null, routeDistanceMeters, "برد خودرو تنظیم نشده است؛ توقف شارژ خودکار پیشنهاد نمی‌شود")
            routeDistanceMeters <= usable -> ChargingNeed(false, usable, routeDistanceMeters, "برد قابل‌استفاده برای مسیر کافی است")
            else -> ChargingNeed(true, usable, routeDistanceMeters, "برد قابل‌استفاده برای کل مسیر کافی نیست؛ توقف شارژ لازم است")
        }
    }

    fun rank(
        origin: Coordinate,
        destination: Coordinate,
        stations: List<Place>,
        preferences: EvRoutePreferences,
        metadata: Map<String, ChargingStationMetadata> = emptyMap(),
        limit: Int = 8
    ): List<ChargingStopCandidate> {
        val requiredConnectors = preferences.normalized().connectorTypes.map(::normalizeConnector).toSet()
        return stations.asSequence().map { place ->
            val details = metadata[place.id]
            val stationConnectors = details?.connectors.orEmpty().map(::normalizeConnector).toSet()
            val compatible = requiredConnectors.isEmpty() || stationConnectors.isEmpty() ||
                stationConnectors.any(requiredConnectors::contains)
            val fromOrigin = distanceMeters(origin, place.coordinate)
            val toDestination = distanceMeters(place.coordinate, destination)
            val direct = distanceMeters(origin, destination)
            ChargingStopCandidate(
                place = place,
                distanceFromOriginMeters = fromOrigin,
                detourHeuristicMeters = (fromOrigin + toDestination - direct).coerceAtLeast(0.0),
                metadata = details,
                connectorCompatible = compatible
            )
        }.filter { it.connectorCompatible }
            .sortedWith(
                compareByDescending<ChargingStopCandidate> { (it.metadata?.availablePorts ?: 0) > 0 }
                    .thenBy { it.detourHeuristicMeters }
                    .thenBy { it.distanceFromOriginMeters }
            )
            .take(limit.coerceIn(1, 30))
            .toList()
    }

    private fun normalizeConnector(value: String): String = value.trim().lowercase().replace(" ", "")

    private fun distanceMeters(a: Coordinate, b: Coordinate): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).let { it * it } +
            cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(min(1.0, h)))
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}
