package ir.nv.navigation.navigation

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import ir.nv.navigation.navigation.mapmatching.RawLocationSample
import ir.nv.navigation.navigation.mapmatching.RouteLocalMapMatcher
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteLocalMapMatcherTest {
    private val route = Route(
        points = listOf(Coordinate(35.7000, 51.4000), Coordinate(35.7100, 51.4000)),
        edgeIds = emptyList(), distanceMeters = 1113.0, travelSeconds = 100.0
    )

    @Test fun snapsNearbySampleToRoute() {
        val sample = RawLocationSample(Coordinate(35.7050, 51.4003), 20.0, 0f, 12f, 1L)
        val matched = assertNotNull(RouteLocalMapMatcher.match(route, sample)) as ir.nv.navigation.navigation.mapmatching.MatchedLocation
        assertTrue(kotlin.math.abs(matched.coordinate.longitude - 51.4000) < .0001)
        assertTrue(matched.confidence > .5)
    }

    @Test fun refusesFarSample() {
        val sample = RawLocationSample(Coordinate(35.7050, 51.4100), 20.0, 0f, 10f, 1L)
        assertNull(RouteLocalMapMatcher.match(route, sample))
    }
}
