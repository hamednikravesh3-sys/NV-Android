package ir.nv.navigation.ui.theme

enum class AppThemeMode(val title: String, val description: String) {
    AUTO("خودکار", "هماهنگ با تنظیمات روز و شب گوشی"),
    DAY("روز", "نقشه روشن با بیشترین خوانایی"),
    NIGHT("شب", "نور کمتر و کنتراست مناسب رانندگی");

    fun resolve(systemDark: Boolean): Boolean = when (this) {
        AUTO -> systemDark
        DAY -> false
        NIGHT -> true
    }

    companion object {
        fun restore(stored: String?, legacyDark: Boolean?): AppThemeMode =
            entries.firstOrNull { it.name == stored } ?: when (legacyDark) {
                true -> NIGHT
                false -> DAY
                null -> AUTO
            }
    }
}
