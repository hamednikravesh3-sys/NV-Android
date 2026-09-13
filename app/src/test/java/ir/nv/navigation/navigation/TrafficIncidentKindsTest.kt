package ir.nv.navigation.navigation

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.traffic.IncidentKind
import ir.nv.navigation.traffic.IncidentTrafficEngine
import ir.nv.navigation.traffic.TrafficIncident
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficIncidentKindsTest {
    @Test fun weatherHazardsCarryHigherPenaltyThanGenericTraffic() {
        val now = 1_000L
        fun incident(kind: IncidentKind) = TrafficIncident("x-$kind", kind, Coordinate(35.7, 51.4), 2, now - 1)
        val engine = IncidentTrafficEngine()
        assertTrue(engine.penalty(listOf(incident(IncidentKind.FLOOD)), now) > engine.penalty(listOf(incident(IncidentKind.TRAFFIC)), now))
    }
}
