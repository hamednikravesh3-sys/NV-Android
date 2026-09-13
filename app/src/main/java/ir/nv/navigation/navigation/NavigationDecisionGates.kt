package ir.nv.navigation.navigation

import ir.nv.navigation.core.Coordinate
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Prevents a reroute from being triggered by a single noisy GPS sample.
 * Poor-accuracy raw fixes are ignored; map-matched fixes get a slightly wider
 * accuracy budget only when the matcher itself is confident.
 */
class OffRouteConfirmationGate(
    private val requiredConsecutiveSamples: Int = 3,
    private val maxRawAccuracyMeters: Float = 35f,
    private val maxMatchedAccuracyMeters: Float = 70f,
    private val minMatchedConfidence: Double = 0.65
) {
    private var consecutive = 0

    fun observe(
        offRoute: Boolean,
        gpsAccuracyMeters: Float,
        mapMatchConfidence: Double?
    ): Boolean {
        val finiteAccuracy = gpsAccuracyMeters.isFinite() && gpsAccuracyMeters >= 0f
        val trustedMatched = mapMatchConfidence != null && mapMatchConfidence >= minMatchedConfidence
        val accuracyLimit = if (trustedMatched) maxMatchedAccuracyMeters else maxRawAccuracyMeters
        val trustedFix = finiteAccuracy && gpsAccuracyMeters <= accuracyLimit

        consecutive = if (offRoute && trustedFix) consecutive + 1 else 0
        return consecutive >= requiredConsecutiveSamples
    }

    fun reset() {
        consecutive = 0
    }
}

/**
 * Arrival is confirmed only after several accurate, slow fixes near the destination.
 * This avoids ending navigation when GPS briefly jumps across the destination.
 */
class ArrivalConfirmationGate(
    private val requiredConsecutiveSamples: Int = 2,
    private val arrivalRadiusMeters: Double = 35.0,
    private val maxAccuracyMeters: Float = 30f,
    private val maxSpeedKmh: Float = 12f
) {
    private var consecutive = 0

    fun observe(
        location: Coordinate,
        destination: Coordinate,
        gpsAccuracyMeters: Float,
        speedKmh: Float
    ): Boolean {
        val trustedFix = gpsAccuracyMeters.isFinite() && gpsAccuracyMeters in 0f..maxAccuracyMeters
        val slowEnough = speedKmh.isFinite() && speedKmh.coerceAtLeast(0f) <= maxSpeedKmh
        val closeEnough = distanceMeters(location, destination) <= arrivalRadiusMeters
        consecutive = if (trustedFix && slowEnough && closeEnough) consecutive + 1 else 0
        return consecutive >= requiredConsecutiveSamples
    }

    fun reset() {
        consecutive = 0
    }

    private fun distanceMeters(a: Coordinate, b: Coordinate): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).let { it * it } +
            cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(min(1.0, h)))
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
