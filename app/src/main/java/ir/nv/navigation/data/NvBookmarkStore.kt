package ir.nv.navigation.data

import android.content.Context
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place

class NvBookmarkStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): Place? {
        if (!prefs.contains(KEY_LAT) || !prefs.contains(KEY_LON)) return null
        return Place(
            code = BOOKMARK_CODE,
            name = prefs.getString(KEY_NAME, "بوک‌مارک NV").orEmpty().ifBlank { "بوک‌مارک NV" },
            coordinate = Coordinate(
                prefs.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return null,
                prefs.getString(KEY_LON, null)?.toDoubleOrNull() ?: return null
            ),
            category = "bookmark:nv",
            personalCode = prefs.getString(KEY_NV_CODE, null)
        )
    }

    fun save(place: Place) {
        prefs.edit()
            .putString(KEY_NAME, place.name)
            .putString(KEY_LAT, place.coordinate.latitude.toString())
            .putString(KEY_LON, place.coordinate.longitude.toString())
            .putString(KEY_NV_CODE, place.personalCode)
            .apply()
    }

    fun attachCode(code: String) {
        prefs.edit().putString(KEY_NV_CODE, code).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS = "nv_bookmark"
        const val KEY_NAME = "name"
        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"
        const val KEY_NV_CODE = "nv_code"
        const val BOOKMARK_CODE = -8_300_000_000L
    }
}
