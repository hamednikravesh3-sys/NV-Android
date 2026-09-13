package ir.nv.navigation.smart

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartMobilityEngineTest {
    private fun route(seconds: Double, meters: Double = 10_000.0) = Route(
        points = listOf(Coordinate(35.70, 51.40), Coordinate(35.75, 51.35)),
        edgeIds = emptyList(),
        distanceMeters = meters,
        travelSeconds = seconds,
        maneuvers = emptyList()
    )

    @Test
    fun `rush chooses actual fastest alternative`() {
        val engine = SmartMobilityEngine()
        val decision = requireNotNull(engine.rush(listOf(route(1_200.0), route(900.0), route(1_500.0)), selectedIndex = 0))
        assertEquals(1, decision.recommendedIndex)
        assertEquals(300.0, decision.savingSeconds, 0.01)
    }

    @Test
    fun `eta confidence improves with live traffic and location`() {
        val engine = SmartMobilityEngine()
        val weak = engine.etaConfidence(route(1_000.0), null, locationActive = false, onlineAvailable = false)
        val strong = engine.etaConfidence(route(1_000.0), 120.0, locationActive = true, onlineAvailable = true)
        assertTrue(strong.confidence > weak.confidence)
        assertEquals(1_120.0, strong.etaSeconds, 0.01)
        assertTrue(strong.uncertaintySeconds < weak.uncertaintySeconds)
    }

    @Test
    fun `time cost never invents money without configured price`() {
        val engine = SmartMobilityEngine()
        val noPrice = engine.timeAndCost(route(600.0, 50_000.0))
        assertNull(noPrice.monetaryCost)
        assertTrue(noPrice.fuelLiters > 0.0)

        val priced = engine.timeAndCost(route(600.0, 50_000.0), fuelPricePerLiter = 10_000L, currency = "IRR")
        assertTrue(requireNotNull(priced.monetaryCost) > 0L)
        assertEquals("IRR", priced.currency)
    }

    @Test
    fun `external mobility providers fail explicitly instead of fabricating live data`() {
        val engine = SmartMobilityEngine()
        assertFalse(engine.transitAvailability().available)
        assertTrue(engine.metroStatus().isEmpty())
        assertFalse(engine.taxiAvailability().available)
        assertNull(engine.taxiEstimate(4_000.0))
    }

    @Test
    fun `assistant routes Persian intent to product screens`() {
        val engine = SmartMobilityEngine()
        assertEquals(SmartFeatureScreen.RUSH, engine.assistant("خیلی عجله دارم سریع‌ترین مسیر", true, true).suggestedScreen)
        assertEquals(SmartFeatureScreen.TAXI, engine.assistant("تاکسی می‌خواهم", true, true).suggestedScreen)
        assertEquals(SmartFeatureScreen.WALKING, engine.assistant("پیاده چقدر طول می‌کشد", true, true).suggestedScreen)
    }
}
