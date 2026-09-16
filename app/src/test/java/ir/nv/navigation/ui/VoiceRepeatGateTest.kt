package ir.nv.navigation.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRepeatGateTest {
    @Test
    fun `guidance and safety alerts are deduplicated independently`() {
        val gate = VoiceRepeatGate(guidanceRepeatWindowMillis = 4_000L, alertRepeatWindowMillis = 12_000L)

        assertTrue(gate.allowGuidance("به راست بپیچید", 1_000L))
        assertFalse(gate.allowGuidance("به راست بپیچید", 2_000L))
        assertTrue(gate.allowGuidance("به راست بپیچید", 6_000L))

        assertTrue(gate.allowAlert("خطر در مسیر", 1_000L))
        assertFalse(gate.allowAlert("خطر در مسیر", 8_000L))
        assertTrue(gate.allowAlert("خطر در مسیر", 14_000L))
    }
}
