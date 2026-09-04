package ir.nv.navigation.weather

import ir.nv.navigation.BuildConfig
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteNotice
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WeatherAlertService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    fun alertsAhead(route: Route): List<RouteNotice> {
        val samples = sampleRoute(route)
        if (samples.isEmpty()) return emptyList()
        val url = BuildConfig.WEATHER_API_URL.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", samples.joinToString(",") { it.coordinate.latitude.toString() })
            .addQueryParameter("longitude", samples.joinToString(",") { it.coordinate.longitude.toString() })
            .addQueryParameter("current", "weather_code,precipitation,wind_gusts_10m,visibility")
            .addQueryParameter("forecast_hours", "1")
            .addQueryParameter("timezone", "auto")
            .apply {
                BuildConfig.WEATHER_API_KEY.takeIf { it.isNotBlank() }
                    ?.let { addQueryParameter("apikey", it) }
            }
            .build()
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Weather HTTP ${response.code}" }
            val body = requireNotNull(response.body).string().trim()
            val documents = if (body.startsWith("[")) {
                JSONArray(body)
            } else {
                JSONArray().put(JSONObject(body))
            }
            return samples.mapIndexedNotNull { index, sample ->
                val current = documents.optJSONObject(index)?.optJSONObject("current")
                    ?: return@mapIndexedNotNull null
                warning(current)?.let { detail ->
                    RouteNotice(
                        title = "هشدار هواشناسی مسیر",
                        detail = detail,
                        distanceAheadMeters = sample.distanceAheadMeters,
                        kind = RouteNotice.Kind.WEATHER
                    )
                }
            }
        }
    }

    private fun warning(current: JSONObject): String? {
        val code = current.optInt("weather_code", 0)
        val precipitation = current.optDouble("precipitation", 0.0)
        val gust = current.optDouble("wind_gusts_10m", 0.0)
        val visibility = current.optDouble("visibility", Double.POSITIVE_INFINITY)
        return when {
            code >= 95 -> "احتمال رعدوبرق؛ سرعت را کاهش دهید"
            code in 71..77 || code in 85..86 -> "بارش برف در مسیر"
            visibility < 1_000 -> "دید کمتر از یک کیلومتر"
            gust >= 60 -> "تندباد تا ${gust.toInt()} کیلومتر بر ساعت"
            precipitation >= 5 -> "بارش شدید در مسیر"
            code in 51..67 || code in 80..82 -> "بارندگی در مسیر"
            else -> null
        }
    }

    private data class Sample(val coordinate: Coordinate, val distanceAheadMeters: Double)

    private fun sampleRoute(route: Route): List<Sample> {
        if (route.points.size < 2 || route.distanceMeters <= 0) return emptyList()
        val indexes = listOf(0.25, 0.55, 0.85).map { fraction ->
            ((route.points.lastIndex) * fraction).toInt().coerceIn(1, route.points.lastIndex)
        }.distinct()
        return indexes.map { index ->
            Sample(
                coordinate = route.points[index],
                distanceAheadMeters = route.distanceMeters * index / route.points.lastIndex
            )
        }
    }
}
