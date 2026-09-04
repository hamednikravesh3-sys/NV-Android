package ir.nv.navigation.routing

/** Camera decisions are deterministic and independently testable. */
object NavigationCameraPolicy {
    fun zoomLevel(speedKmh: Int, distanceToManeuverMeters: Double): Int = when {
        distanceToManeuverMeters in 0.0..140.0 -> 19
        distanceToManeuverMeters in 140.0..420.0 -> 18
        speedKmh >= 100 -> 15
        speedKmh >= 70 -> 16
        speedKmh >= 40 -> 17
        else -> 18
    }
}
