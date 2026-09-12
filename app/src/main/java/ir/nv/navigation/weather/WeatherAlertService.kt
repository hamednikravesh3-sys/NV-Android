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
import kotlin.math.roundToInt

class WeatherAlertService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    fun alertsAhead(route: Route): List<RouteNotice> {
        if (route.distanceMeters <= 0.0) return emptyList()
        val sampleDistances = buildList {
            SAMPLE_DISTANCES.filterTo(this) { it < route.distanceMeters }
            add((route.distanceMeters * 0.72).coerceAtLeast(1_000.0).coerceAtMost(route.distanceMeters))
        }.distinctBy { (it / 1_000.0).roundToInt() }
            .sorted()
            .take(MAX_SAMPLES)

        val notices = sampleDistances.mapNotNull { distance ->
            val sample = RoutePointSampler.pointAhead(route, distance) ?: return@mapNotNull null
            val etaSeconds = route.travelSeconds * (sample.distanceAheadMeters / route.distanceMeters).coerceIn(0.0, 1.0)
            val conditions = fetchForecast(sample.coordinate.latitude, sample.coordinate.longitude, etaSeconds)
                ?: return@mapNotNull null
            val warning = warning(conditions)
            if (warning == null && sample.distanceAheadMeters > NORMAL_CONDITIONS_MAX_DISTANCE) return@mapNotNull null
            RouteNotice(
                title = if (warning != null) {
                    "هشدار هواشناسی حدود ${etaLabel(etaSeconds)} دیگر"
                } else {
                    "آب‌وهوا حدود ${etaLabel(etaSeconds)} دیگر"
                },
                detail = warning ?: normalConditions(conditions),
                distanceAheadMeters = sample.distanceAheadMeters,
                kind = RouteNotice.Kind.WEATHER
            )
        }

        return notices
            .distinctBy { Triple(it.title, it.detail, (it.distanceAheadMeters / 2_000.0).toInt()) }
            .sortedBy { it.distanceAheadMeters }
            .take(MAX_NOTICES)
    }

    private fun fetchForecast(latitude: Double, longitude: Double, etaSeconds: Double): JSONObject? {
        val url = BuildConfig.WEATHER_API_URL.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", latitude.toString())
            .addQueryParameter("longitude", longitude.toString())
            .addQueryParameter("hourly", "temperature_2m,weather_code,precipitation,wind_gusts_10m,visibility")
            .addQueryParameter("forecast_hours", "24")
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
            val hourly = document?.optJSONObject("hourly") ?: return null
            val index = (etaSeconds / 3_600.0).roundToInt().coerceIn(0, 23)
            return JSONObject().apply {
                put("temperature_2m", hourly.optJSONArray("temperature_2m")?.optDouble(index, Double.NaN))
                put("weather_code", hourly.optJSONArray("weather_code")?.optInt(index, 0))
                put("precipitation", hourly.optJSONArray("precipitation")?.optDouble(index, 0.0))
                put("wind_gusts_10m", hourly.optJSONArray("wind_gusts_10m")?.optDouble(index, 0.0))
                put("visibility", hourly.optJSONArray("visibility")?.optDouble(index, Double.POSITIVE_INFINITY))
            }
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
            code >= 95 -> "خطر رعدوبرق در مسیر؛ سرعت را کاهش دهید و فاصله طولی را بیشتر کنید"
            code in 71..77 || code in 85..86 -> "برف در مسیر پیش‌بینی شده؛ احتمال لغزندگی وجود دارد"
            visibility < 700 -> "دید بسیار کم، کمتر از ۷۰۰ متر؛ با سرعت ایمن حرکت کنید"
            visibility < 1_500 -> "کاهش دید در مسیر؛ چراغ‌ها و فاصله طولی را کنترل کنید"
            gust >= 75 -> "تندباد شدید تا ${gust.toInt()} کیلومتر بر ساعت"
            gust >= 60 -> "تندباد تا ${gust.toInt()} کیلومتر بر ساعت"
            precipitation >= 7 -> "بارش سنگین در مسیر پیش‌بینی شده"
            precipitation >= 3 -> "بارش قابل توجه در مسیر پیش‌بینی شده"
            code in 51..67 || code in 80..82 -> "بارندگی در مسیر پیش‌بینی شده"
            else -> null
        }
    }

    private fun etaLabel(seconds: Double): String {
        val minutes = (seconds / 60.0).roundToInt().coerceAtLeast(1)
        return if (minutes < 60) "$minutes دقیقه" else {
            val hours = minutes / 60
            val rest = minutes % 60
            if (rest == 0) "$hours ساعت" else "$hours ساعت و $rest دقیقه"
        }
    }

    private companion object {
        val SAMPLE_DISTANCES = listOf(5_000.0, 20_000.0, 50_000.0)
        const val MAX_SAMPLES = 4
        const val MAX_NOTICES = 4
        const val NORMAL_CONDITIONS_MAX_DISTANCE = 6_000.0
    }
}
