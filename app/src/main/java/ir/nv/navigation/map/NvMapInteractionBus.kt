package ir.nv.navigation.map

import ir.nv.navigation.core.Coordinate

/** Lightweight in-process bridge for map gestures and non-navigation recentering. */
object NvMapInteractionBus {
    @Volatile
    var onDoubleTap: ((Coordinate) -> Unit)? = null

    @Volatile
    var forcedLocation: Coordinate? = null
        private set

    @Volatile
    var recenterToken: Int = 0
        private set

    fun emitDoubleTap(coordinate: Coordinate) {
        onDoubleTap?.invoke(coordinate)
    }

    fun recenterOn(coordinate: Coordinate) {
        forcedLocation = coordinate
        recenterToken += 1
    }

    fun clearListener(listener: (Coordinate) -> Unit) {
        if (onDoubleTap === listener) onDoubleTap = null
    }
}
