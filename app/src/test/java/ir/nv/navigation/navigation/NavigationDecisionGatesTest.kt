package ir.nv.navigation.navigation

import ir.nv.navigation.core.Coordinate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationDecisionGatesTest {
    @Test
    fun offRouteRequiresThreeConsecutiveTrustedSamples() {
        val gate = OffRouteConfirmationGate()
        assertFalse(gate.observe(offRoute = true, gpsAccuracyMeters = 12f, mapMatchConfidence = 0.8))
        assertFalse(gate.observe(offRoute = true, gpsAccuracyMeters = 12f, mapMatchConfidence = 0.8))
        assertTrue(gate.observe(offRoute = true, gpsAccuracyMeters = 12f, mapMatchConfidence = 0.8))
    }

    @Test
    fun poorGpsAccuracyBreaksOffRouteSequence() {
        val gate = OffRouteConfirmationGate()
        assertFalse(gate.observe(true, 10f, null))
        assertFalse(gate.observe(true, 150f, null))
        assertFalse(gate.observe(true, 10f, null))
        assertFalse(gate.observe(true, 10f, null))
        assertTrue(gate.observe(true, 10f, null))
    }

    @Test
    fun highConfidenceMapMatchCanUseWiderAccuracyBudget() {
        val gate = OffRouteConfirmationGate()
        assertFalse(gate.observe(true, 55f, 0.9))
        assertFalse(gate.observe(true, 55f, 0.9))
        assertTrue(gate.observe(true, 55f, 0.9))
    }

    @Test
    fun arrivalNeedsTwoAccurateSlowFixesNearDestination() {
        val gate = ArrivalConfirmationGate()
        val destination = Coordinate(35.7000, 51.4000)
        val nearby = Coordinate(35.7001, 51.4001)
        assertFalse(gate.observe(nearby, destination, gpsAccuracyMeters = 8f, speedKmh = 4f))
        assertTrue(gate.observe(nearby, destination, gpsAccuracyMeters = 8f, speedKmh = 4f))
    }

    @Test
    fun fastOrInaccurateFixDoesNotConfirmArrival() {
        val gate = ArrivalConfirmationGate()
        val destination = Coordinate(35.7000, 51.4000)
        val nearby = Coordinate(35.7001, 51.4001)
        assertFalse(gate.observe(nearby, destination, gpsAccuracyMeters = 8f, speedKmh = 35f))
        assertFalse(gate.observe(nearby, destination, gpsAccuracyMeters = 80f, speedKmh = 2f))
        assertFalse(gate.observe(nearby, destination, gpsAccuracyMeters = 8f, speedKmh = 2f))
        assertTrue(gate.observe(nearby, destination, gpsAccuracyMeters = 8f, speedKmh = 2f))
    }
}
