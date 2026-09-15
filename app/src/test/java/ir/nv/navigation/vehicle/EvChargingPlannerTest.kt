package ir.nv.navigation.vehicle

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.navigation.EvRoutePreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvChargingPlannerTest {
    @Test fun detectsInsufficientUsableRange() {
        val prefs = EvRoutePreferences(batteryPercent = 50, estimatedRangeKm = 200.0, minimumArrivalBatteryPercent = 10)
        assertTrue(EvChargingPlanner.need(100_000.0, prefs).required)
        assertFalse(EvChargingPlanner.need(70_000.0, prefs).required)
    }

    @Test fun filtersKnownIncompatibleConnectors() {
        val a = Place(1, "A", Coordinate(35.7, 51.4), "ev")
        val b = Place(2, "B", Coordinate(35.71, 51.41), "ev")
        val prefs = EvRoutePreferences(connectorTypes = setOf("CCS2"))
        val ranked = EvChargingPlanner.rank(
            Coordinate(35.6, 51.3), Coordinate(35.8, 51.5), listOf(a, b), prefs,
            mapOf(
                a.id to ChargingStationMetadata(connectors = setOf("CHAdeMO")),
                b.id to ChargingStationMetadata(connectors = setOf("CCS2"))
            )
        )
        assertTrue(ranked.all { it.place.id == b.id })
    }
}
