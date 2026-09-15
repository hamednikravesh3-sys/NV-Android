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
    @Test
    fun `explicit mocks are labelled non live and non bookable`() {
        val engine = SmartMobilityEngine(DeterministicMockTransitRealtimeProvider(), DeterministicMockTaxiProvider())
        val metro = engine.metroStatus().single()
        assertFalse(metro.live)
        assertTrue(metro.source.contains("mock"))
        val taxi = requireNotNull(engine.taxiEstimate(4_000.0))
        assertFalse(taxi.bookable)
        assertTrue(taxi.source.contains("mock"))
    }

    @Test
    fun `station transfer ranking respects walking transfer crowding and accessibility preferences`() {
        val engine = SmartMobilityEngine()
        val prefs = TravelPreferences(maxWalkingMeters = 800, maxTransfers = 1, avoidCrowding = true, accessibilityRequired = true)
        val options = listOf(
            StationTransferOption("A", 300.0, 5, 1, 80, true, "test"),
            StationTransferOption("B", 200.0, 6, 0, 20, true, "test"),
            StationTransferOption("C", 900.0, 1, 0, 0, true, "test"),
            StationTransferOption("D", 100.0, 1, 0, 0, false, "test")
        )
        val ranked = engine.rankStationTransfers(options, prefs)
        assertEquals("B", ranked.first().stationName)
        assertEquals(2, ranked.size)
    }

    @Test
    fun `eta risk rises when confidence is poor`() {
        val engine = SmartMobilityEngine()
        val highConfidence = EtaConfidence(1000.0, .9, 60.0, "high")
        val lowConfidence = EtaConfidence(1000.0, .45, 300.0, "low")
        assertTrue(engine.etaRisk(lowConfidence, 250.0).riskScore > engine.etaRisk(highConfidence, 0.0).riskScore)
    }

    @Test
    fun `multimodal mock candidates remain explicitly sourced`() {
        val engine = SmartMobilityEngine(DeterministicMockTransitRealtimeProvider(), DeterministicMockTaxiProvider())
        val plans = engine.multimodalCandidates(route(900.0, 1_000.0), TravelPreferences(maxWalkingMeters = 2_000))
        assertTrue(plans.isNotEmpty())
        assertTrue(plans.flatMap { it.legs }.any { it.source.contains("mock") })
    }

}
