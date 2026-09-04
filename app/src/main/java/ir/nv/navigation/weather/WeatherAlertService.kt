package ir.nv.navigation.weather

import ir.nv.navigation.BuildConfig
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.routing.RoutePointSampler
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
        val sample = RoutePointSampler.pointAhead(route, WEATHER_DISTANCE_METERS) ?: return emptyList()
        val url = BuildConfig.WEATHER_API_URL.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", sample.coordinate.latitude.toString())
            .addQueryParameter("longitude", sample.coordinate.longitude.toString())
            .addQueryParameter("current", "temperature_2m,weather_code,precipitation,wind_gusts_10m,visibility")
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
            val document = if (body.startsWith("[")) JSONArray(body).optJSONObject(0) else JSONObject(body)
            val current = document?.optJSONObject("current") ?: return emptyList()
            val warning = warning(current)
            val distanceLabel = if (sample.distanceAheadMeters >= 9_950.0) "۱۰ کیلومتر" else
                "${(sample.distanceAheadMeters / 1_000.0).coerceAtLeast(0.1).let { "%.1f".format(it) }} کیلومتر"
            return listOf(
                RouteNotice(
                    title = if (warning != null) "هشدار هواشناسی در $distanceLabel جلوتر" else
                        "آب‌وهوا در $distanceLabel جلوتر",
                    detail = warning ?: normalConditions(current),
                    distanceAheadMeters = sample.distanceAheadMeters,
                    kind = RouteNotice.Kind.WEATHER
                )
            )
        }
    }

    private fun normalConditions(current: JSONObject): String {
        val code = current.optInt("weather_code", 0)
        val temperature = current.optDouble("temperature_2m", Double.NaN)
        val precipitation = current.optDouble("precipitation", 0.0)
        val gust = current.optDouble("wind_gusts_10m", 0.0)
        val description = weatherDescription(code)
        val temperatureText = if (temperature.isNaN()) "" else " • ${temperature.toInt()}°"
        return "$description$temperatureText • بارش ${"%.1f".format(precipitation)} mm • باد ${gust.toInt()} km/h"
    }

    private fun weatherDescription(code: Int): String = when (code) {
        0 -> "صاف"
        1, 2 -> "کمی ابری"
        3 -> "ابری"
        45, 48 -> "مه‌آلود"
        in 51..67, in 80..82 -> "بارانی"
        in 71..77, in 85..86 -> "برفی"
        in 95..99 -> "رعدوبرق"
        else -> "وضعیت عادی"
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

    private companion object {
        const val WEATHER_DISTANCE_METERS = 10_000.0
    }
}
