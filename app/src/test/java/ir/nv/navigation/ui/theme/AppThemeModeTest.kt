package ir.nv.navigation.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeModeTest {
    @Test fun automatic_mode_follows_system() {
        assertTrue(AppThemeMode.AUTO.resolve(true))
        assertFalse(AppThemeMode.AUTO.resolve(false))
    }

    @Test fun explicit_modes_override_system() {
        assertFalse(AppThemeMode.DAY.resolve(true))
        assertTrue(AppThemeMode.NIGHT.resolve(false))
    }

    @Test fun restores_legacy_setting() {
        assertEquals(AppThemeMode.NIGHT, AppThemeMode.restore(null, true))
        assertEquals(AppThemeMode.DAY, AppThemeMode.restore(null, false))
    }
}
