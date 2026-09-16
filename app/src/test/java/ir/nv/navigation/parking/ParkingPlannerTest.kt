package ir.nv.navigation.parking

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParkingPlannerTest {
    @Test fun ranksNearOpenParkingWithoutInventingMetadata() {
        val destination = Coordinate(35.7000, 51.4000)
        val near = Place(1, "نزدیک", Coordinate(35.7004, 51.4000), "parking", isOpen = true, confidence = .9)
        val far = Place(2, "دور", Coordinate(35.7100, 51.4000), "parking", isOpen = true, confidence = .9)
        val options = ParkingPlanner.rank(destination, listOf(far, near))
        assertEquals(near, options.first().place)
        assertNull(options.first().metadata)
    }
}
