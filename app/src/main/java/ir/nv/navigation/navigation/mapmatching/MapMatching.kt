package ir.nv.navigation.navigation.mapmatching

import ir.nv.navigation.core.Coordinate

data class RawLocationSample(
    val coordinate: Coordinate,
    val speedKmh: Double,
    val bearingDegrees: Float,
    val accuracyMeters: Float,
    val timestampMillis: Long
)

data class MatchedLocation(
    val coordinate: Coordinate,
    val confidence: Double,
    val roadName: String? = null,
    val edgeId: Long? = null
)

fun interface MapMatchingEngine {
    suspend fun match(sample: RawLocationSample): MatchedLocation?
}

class PassThroughMapMatchingEngine : MapMatchingEngine {
    override suspend fun match(sample: RawLocationSample): MatchedLocation = MatchedLocation(
        coordinate = sample.coordinate,
        confidence = confidenceFromAccuracy(sample.accuracyMeters)
    )

    private fun confidenceFromAccuracy(accuracyMeters: Float): Double = when {
        accuracyMeters <= 5f -> 0.95
        accuracyMeters <= 10f -> 0.85
        accuracyMeters <= 20f -> 0.70
        accuracyMeters <= 40f -> 0.50
        else -> 0.30
    }
}
