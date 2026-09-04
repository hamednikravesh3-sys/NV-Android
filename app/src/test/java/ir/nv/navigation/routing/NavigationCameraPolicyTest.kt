package ir.nv.navigation.routing

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationCameraPolicyTest {
    @Test fun zooms_in_near_a_turn() {
        assertEquals(19, NavigationCameraPolicy.zoomLevel(90, 80.0))
    }

    @Test fun zooms_out_on_fast_straight_road() {
        assertEquals(15, NavigationCameraPolicy.zoomLevel(110, 2_000.0))
    }

    @Test fun keeps_city_driving_readable() {
        assertEquals(18, NavigationCameraPolicy.zoomLevel(25, 1_000.0))
    }
}
