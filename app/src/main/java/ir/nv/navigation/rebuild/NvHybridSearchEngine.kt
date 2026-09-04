package ir.nv.navigation.rebuild

import ir.nv.navigation.BuildConfig
import ir.nv.navigation.core.Coordinate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class NvHybridSearchEngine {
    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun searchOnline(query: String, limit: Int = 20): List<NvSearchResult> = withContext(Dispatchers.IO) {
        val q = normalize(query)
        if (q.length < 2) return@withContext emptyList()

        val photon = runCatching { searchPhoton(q, limit) }.getOrDefault(emptyList())
        if (photon.size >= 6) return@withContext rank(q, photon).take(limit)

        val nominatim = runCatching { searchNominatim(q, limit) }.getOrDefault(emptyList())
        rank(q, (photon + nominatim).distinctBy { "${it.title}|${it.coordinate.latitude}|${it.coordinate.longitude}" }).take(limit)
    }

    private fun searchPhoton(query: String, limit: Int): List<NvSearchResult> {
        val encoded = enc(query)
        val url = BuildConfig.GEOCODING_API_URL.trimEnd('/') +
            "?q=$encoded&limit=$limit&lang=fa&bbox=44.0,24.0,64.0,40.0"
        val body = get(url)
        val root = JSONObject(body)
        val features = root.optJSONArray("features") ?: JSONArray()
        return buildList {
            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue
                val geometry = feature.optJSONObject("geometry") ?: continue
                val coords = geometry.optJSONArray("coordinates") ?: continue
                if (coords.length() < 2) continue
                val lon = coords.optDouble(0, Double.NaN)
                val lat = coords.optDouble(1, Double.NaN)
                if (!lat.isFinite() || !lon.isFinite()) continue
                val p = feature.optJSONObject("properties") ?: JSONObject()
                val title = p.optString("name").ifBlank { p.optString("street") }.ifBlank { p.optString("city") }
                if (title.isBlank()) continue
                val city = p.optString("city").ifBlank { p.optString("district") }.ifBlank { p.optString("county") }
                val state = p.optString("state")
                val subtitle = listOf(city, state).filter { it.isNotBlank() && it != title }.distinct().joinToString("، ")
                val category = "${p.optString("osm_key", "place")}:${p.optString("osm_value", "unknown")}"
                add(NvSearchResult("photon:${p.optLong("osm_id", i.toLong())}", title, subtitle, Coordinate(lat, lon), category, SearchSource.ONLINE))
            }
        }
    }

    private fun searchNominatim(query: String, limit: Int): List<NvSearchResult> {
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&countrycodes=ir&addressdetails=1&accept-language=fa,en&limit=$limit&q=${enc(query)}"
        val arr = JSONArray(get(url))
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val lat = item.optString("lat").toDoubleOrNull() ?: continue
                val lon = item.optString("lon").toDoubleOrNull() ?: continue
                val address = item.optJSONObject("address") ?: JSONObject()
                val title = item.optString("name").ifBlank {
                    address.optString("road").ifBlank { address.optString("city").ifBlank { item.optString("display_name").substringBefore(',') } }
                }
                if (title.isBlank()) continue
                val area = listOf(
                    address.optString("neighbourhood"), address.optString("suburb"), address.optString("city"),
                    address.optString("town"), address.optString("village"), address.optString("state")
                ).filter { it.isNotBlank() && it != title }.distinct().take(3).joinToString("، ")
                add(NvSearchResult("nominatim:${item.optLong("place_id", i.toLong())}", title, area, Coordinate(lat, lon), item.optString("type", "place"), SearchSource.ONLINE))
            }
        }
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url)
            .header("User-Agent", "NV-Android/2.0 (navigation app; contact: hamednikravesh3@gmail.com)")
            .header("Accept-Language", "fa,en;q=0.8")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    private fun rank(query: String, input: List<NvSearchResult>): List<NvSearchResult> {
        val q = normalize(query)
        return input.sortedWith(compareBy<NvSearchResult> {
            val t = normalize(it.title)
            when {
                t == q -> 0
                t.startsWith(q) -> 1
                t.contains(q) -> 2
                else -> 3
            }
        }.thenBy {
            when {
                it.category.contains("city", true) || it.category.contains("town", true) -> 0
                it.category.contains("road", true) || it.category.contains("street", true) || it.category.contains("highway", true) -> 1
                it.category.contains("suburb", true) || it.category.contains("neighbour", true) -> 2
                else -> 3
            }
        }.thenBy { it.title.length })
    }

    private fun normalize(value: String) = value.trim().lowercase()
        .replace('ي', 'ی').replace('ك', 'ک').replace("\u200c", " ")
        .replace(Regex("\\s+"), " ")

    private fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
