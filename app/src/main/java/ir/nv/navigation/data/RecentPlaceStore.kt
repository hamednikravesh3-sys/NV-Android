package ir.nv.navigation.data

import android.content.Context
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import org.json.JSONArray
import org.json.JSONObject

class RecentPlaceStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(limit: Int = 4): List<Place> = read().take(limit).map { item ->
        Place(item.code, item.name, Coordinate(item.latitude, item.longitude), item.category, item.personalCode)
    }

    fun record(place: Place) {
        if (place.category == "device:location") return
        val item = Item(place.code, place.name, place.coordinate.latitude, place.coordinate.longitude, place.category, place.personalCode)
        val updated = listOf(item) + read().filterNot {
            it.code == item.code || (it.latitude == item.latitude && it.longitude == item.longitude)
        }
        write(updated.take(MAX_ITEMS))
    }

    private fun read(): List<Item> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.getJSONObject(index)
                    add(Item(value.getLong("code"), value.getString("name"), value.getDouble("latitude"), value.getDouble("longitude"), value.getString("category"), value.optString("personalCode").ifBlank { null }))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun write(items: List<Item>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().put("code", item.code).put("name", item.name)
                .put("latitude", item.latitude).put("longitude", item.longitude)
                .put("category", item.category).put("personalCode", item.personalCode ?: ""))
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private data class Item(val code: Long, val name: String, val latitude: Double, val longitude: Double, val category: String, val personalCode: String?)

    private companion object {
        const val PREFS = "recent_places"
        const val KEY_ITEMS = "items"
        const val MAX_ITEMS = 12
    }
}
