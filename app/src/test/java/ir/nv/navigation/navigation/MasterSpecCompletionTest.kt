package ir.nv.navigation.navigation

import ir.nv.navigation.ai.AccidentRiskPredictionEngine
import ir.nv.navigation.ai.ParkingCandidate
import ir.nv.navigation.ai.ParkingPredictionEngine
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.navigation.guidance.DriverRoadContext
import ir.nv.navigation.navigation.guidance.DriverSafetyEngine
import ir.nv.navigation.traffic.HistoricalTrafficModel
import ir.nv.navigation.traffic.HistoricalTrafficSample
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneOffset
import java.time.ZonedDateTime

class MasterSpecCompletionTest {
    @Test fun overspeedWarningUsesThreshold() {
        val result = DriverSafetyEngine(5).evaluate(86, DriverRoadContext(speedLimitKmh = 80, exitNumber = "12"))
        assertTrue(result.overspeed)
        assertEquals(6, result.overspeedByKmh)
    }

    @Test fun historicalTrafficProducesDelay() {
        val model = HistoricalTrafficModel(listOf(HistoricalTrafficSample(DayOfWeek.MONDAY, 480, 25.0, 50.0)))
        val t = ZonedDateTime.of(2026, 9, 14, 8, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(1.0, model.expectedDelayRatio(t, ZoneOffset.UTC)!!, 0.001)
    }

    @Test fun accidentRiskAndParkingAreRanked() {
        val risk = AccidentRiskPredictionEngine().risk(AccidentRiskPredictionEngine.Signal(true, true, 100.0, 0.5, 0.4))
        assertTrue(risk > 0.4)
        val place = Place(1, "Parking", Coordinate(35.0, 51.0), "parking")
        val ranked = ParkingPredictionEngine().rank(listOf(ParkingCandidate(place, 0.9, 100.0), ParkingCandidate(place.copy(code=2), 0.2, 300.0)))
        assertEquals(2L, ranked.first().place.code)
    }
}
