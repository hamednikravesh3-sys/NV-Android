package ir.nv.navigation.places

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbySearchPolicyTest {
    @Test
    fun progressiveRadiusSequenceIsExact() {
        assertEquals(listOf(5_000), NearbySearchPolicy.progressiveRadiiMeters(5_000))
        assertEquals(listOf(5_000, 10_000), NearbySearchPolicy.progressiveRadiiMeters(10_000))
        assertEquals(listOf(5_000, 10_000, 25_000), NearbySearchPolicy.progressiveRadiiMeters(25_000))
        assertEquals(listOf(5_000, 10_000, 25_000, 50_000), NearbySearchPolicy.progressiveRadiiMeters(50_000))
        assertEquals(listOf(5_000, 10_000, 25_000, 50_000, 100_000), NearbySearchPolicy.progressiveRadiiMeters(100_000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedRadiusIsRejected() {
        NearbySearchPolicy.progressiveRadiiMeters(18_000)
    }

    @Test
    fun categoryTaxonomyHasNoHomeOrWorkPseudoCategory() {
        val ids = NearbyCategory.entries.map { it.id }.toSet()
        assertTrue(setOf("emergency", "hospital", "pharmacy", "police", "fire", "rescue", "parking", "fuel", "ev", "parks", "recreation", "restaurants", "cafe", "hotel", "shopping", "cinema", "attractions", "repair", "bank", "atm", "services").all(ids::contains))
        assertFalse("home" in ids)
        assertFalse("work" in ids)
    }

    @Test
    fun resultsOutsideSelectedRadiusAreRemoved() {
        val center = Coordinate(35.0, 51.0)
        val near = Place(1, "near", Coordinate(35.02, 51.0), "amenity:pharmacy")
        val far = Place(2, "far", Coordinate(35.10, 51.0), "amenity:pharmacy")
        val result = NearbySearchPolicy.filterWithinRadius(center, listOf(near, far), 5_000)
        assertEquals(listOf(1L), result.map { it.code })
        assertTrue((result.first().distance ?: Double.MAX_VALUE) in 2_000.0..3_000.0)
    }

    @Test
    fun alongRouteUsesCorridorDistanceNotDistanceFromOrigin() {
        val route = Route(
            points = listOf(Coordinate(35.0, 51.0), Coordinate(35.0, 52.0)),
            edgeIds = emptyList(),
            distanceMeters = 90_000.0,
            travelSeconds = 5_000.0
        )
        val besideMiddle = Place(3, "middle", Coordinate(35.01, 51.5), "amenity:fuel")
        val result = NearbySearchPolicy.filterAlongRoute(route, listOf(besideMiddle), 5_000)
        assertEquals(1, result.size)
        assertTrue((result.first().distance ?: Double.MAX_VALUE) < 2_000.0)
    }
}
