package ir.nv.navigation.parking

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class ParkingMetadata(
    val availableSpots: Int? = null,
    val pricePerHour: Long? = null,
    val currency: String? = null,
    val provider: String? = null
)

data class ParkingOption(
    val place: Place,
    val walkingDistanceMeters: Double,
    val metadata: ParkingMetadata? = null,
    val score: Double
)

/** Ranks only known facts. Missing occupancy/price is never synthesized. */
object ParkingPlanner {
    fun rank(
        destination: Coordinate,
        candidates: List<Place>,
        metadata: Map<String, ParkingMetadata> = emptyMap(),
        limit: Int = 12
    ): List<ParkingOption> = candidates
        .asSequence()
        .filter { it.coordinate.latitude in -90.0..90.0 && it.coordinate.longitude in -180.0..180.0 }
        .map { place ->
            val walkingMeters = distanceMeters(destination, place.coordinate)
            val details = metadata[place.id]
            val closedPenalty = if (place.isOpen == false) 8_000.0 else 0.0
            val availabilityPenalty = when {
                details?.availableSpots == null -> 0.0
                details.availableSpots <= 0 -> 5_000.0
                else -> -min(details.availableSpots, 20) * 15.0
            }
            val confidencePenalty = (1.0 - place.confidence.coerceIn(0.0, 1.0)) * 600.0
            val score = walkingMeters + closedPenalty + availabilityPenalty + confidencePenalty
            ParkingOption(place, walkingMeters, details, score)
        }
        .sortedWith(compareBy<ParkingOption> { it.score }.thenBy { it.place.name })
        .take(limit.coerceIn(1, 50))
        .toList()

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
