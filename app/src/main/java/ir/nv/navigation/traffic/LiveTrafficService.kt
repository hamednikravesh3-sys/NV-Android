package ir.nv.navigation.traffic

import ir.nv.navigation.BuildConfig
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.TrafficSegment
import ir.nv.navigation.core.TrafficSummary
import ir.nv.navigation.routing.RouteInsightEngine
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Real TomTom flow data. No synthetic congestion is produced when no key is configured. */
class LiveTrafficService {
    private val client = OkHttpClient.Builder()
        .callTimeout(6, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun isConfigured(): Boolean = BuildConfig.TRAFFIC_API_KEY.isNotBlank()

    fun summary(route: Route): TrafficSummary? {
        if (!isConfigured() || route.points.size < 3) return null
        val samples = sample(route.points)
        val segments = samples.mapNotNull(::loadSegment)
        return segments.takeIf { it.isNotEmpty() }?.let(RouteInsightEngine::summarizeTraffic)
    }

    private fun loadSegment(point: Coordinate): TrafficSegment? {
        val url = (BuildConfig.TRAFFIC_API_URL.trimEnd('/') +
            "/traffic/services/4/flowSegmentData/absolute/10/json").toHttpUrl().newBuilder()
            .addQueryParameter("point", "${point.latitude},${point.longitude}")
            .addQueryParameter("unit", "KMPH")
            .addQueryParameter("key", BuildConfig.TRAFFIC_API_KEY)
            .build()
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val flow = JSONObject(response.body?.string().orEmpty()).optJSONObject("flowSegmentData")
                ?: return null
            val currentSpeed = flow.optDouble("currentSpeed", 0.0)
            val freeSpeed = flow.optDouble("freeFlowSpeed", 0.0)
            val currentTime = flow.optDouble("currentTravelTime", 0.0)
            val freeTime = flow.optDouble("freeFlowTravelTime", 0.0)
            val congested = freeSpeed > 0.0 && currentSpeed in 0.1..(freeSpeed * 0.82)
            if (!congested) return null
            val length = (currentSpeed / 3.6 * currentTime).coerceAtLeast(0.0)
            TrafficSegment(
                start = point,
                end = point,
                lengthMeters = length,
                delaySeconds = (currentTime - freeTime).coerceAtLeast(0.0)
            )
        }
    }

    private fun sample(points: List<Coordinate>): List<Coordinate> =
        listOf(0.2, 0.5, 0.8)
            .map { fraction -> points[(points.lastIndex * fraction).toInt().coerceIn(0, points.lastIndex)] }
            .distinct()
}
