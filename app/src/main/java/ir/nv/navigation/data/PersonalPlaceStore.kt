package ir.nv.navigation.data

import android.content.Context
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import org.json.JSONArray
import org.json.JSONObject

class PersonalPlaceStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(): List<Place> = read().mapIndexed { index, item ->
        Place(
            code = PERSONAL_CODE_BASE + index,
            name = item.name,
            coordinate = Coordinate(item.latitude, item.longitude),
            category = "personal:${item.code}",
            personalCode = item.code
        )
    }

    fun search(query: String): List<Place> {
        val cleanQuery = PlaceCodes.normalizeDigits(query).trim()
            .replace(Regex("^NV\\s*[:#-]?\\s*", RegexOption.IGNORE_CASE), "")
        val normalized = PersianText.normalize(cleanQuery)
        return all().filter { place ->
            val code = place.personalCode.orEmpty()
            PersianText.normalize(place.name).contains(normalized) ||
                code.equals(cleanQuery, ignoreCase = true) ||
                code.contains(cleanQuery, ignoreCase = true)
        }
    }

    fun save(code: String, name: String, coordinate: Coordinate): Result<Unit> = runCatching {
        val cleanCode = PersonalCodeRules.normalize(code)
        requireNotNull(cleanCode) { "کد شخصی باید یک عدد بین ۱ تا ۹۹۹٬۹۹۹٬۹۹۹ باشد" }
        val current = read().toMutableList()
        require(current.none { it.code == cleanCode }) {
            "این کد شخصی قبلاً استفاده شده است"
        }
        current += Item(cleanCode, name.trim().ifBlank { "مکان شخصی" }, coordinate.latitude, coordinate.longitude)
        write(current)
    }

    fun delete(code: String) {
        val cleanCode = PersonalCodeRules.normalize(code) ?: return
        write(read().filterNot { it.code == cleanCode })
    }

    private fun read(): List<Item> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        Item(
                            code = item.getString("code"),
                            name = item.getString("name"),
                            latitude = item.getDouble("latitude"),
                            longitude = item.getDouble("longitude")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun write(items: List<Item>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("code", item.code)
                    .put("name", item.name)
                    .put("latitude", item.latitude)
                    .put("longitude", item.longitude)
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private data class Item(
        val code: String,
        val name: String,
        val latitude: Double,
        val longitude: Double
    )

    private companion object {
        const val PREFS = "personal_places"
        const val KEY_ITEMS = "items"
        const val PERSONAL_CODE_BASE = -1_000_000_000L
    }
}
