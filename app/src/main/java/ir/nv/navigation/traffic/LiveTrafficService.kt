package ir.nv.navigation.traffic

import ir.nv.navigation.BuildConfig
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.TrafficSegment
import ir.nv.navigation.core.TrafficSummary
import ir.nv.navigation.core.TrafficReport
import ir.nv.navigation.routing.RouteInsightEngine
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Real TomTom flow data. No synthetic congestion is produced when no key is configured. */
class LiveTrafficService {
    private val client = OkHttpClient.Builder()
        .callTimeout(6, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun isConfigured(): Boolean = BuildConfig.TRAFFIC_API_KEY.isNotBlank()

    fun report(route: Route): TrafficReport? {
        if (!isConfigured() || route.points.size < 3) return null
        val segments = sample(route.points).mapNotNull(::loadSegment)
        if (segments.isEmpty()) return null
        val congested = segments.filter { it.delaySeconds > 1.0 }
        return TrafficReport(
            summary = RouteInsightEngine.summarizeTraffic(congested),
            segments = segments
        )
    }

    fun summary(route: Route): TrafficSummary? = report(route)?.summary

    private fun loadSegment(sample: Sample): TrafficSegment? {
        val url = (BuildConfig.TRAFFIC_API_URL.trimEnd('/') +
            "/traffic/services/4/flowSegmentData/absolute/10/json").toHttpUrl().newBuilder()
            .addQueryParameter("point", "${sample.probe.latitude},${sample.probe.longitude}")
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
            if (freeSpeed <= 0.0 || currentSpeed <= 0.0) return null
            val congested = currentSpeed <= freeSpeed * 0.82
            val length = distance(sample.start, sample.end)
            TrafficSegment(
                start = sample.start,
                end = sample.end,
                lengthMeters = length,
                delaySeconds = if (congested) (currentTime - freeTime).coerceAtLeast(0.0) else 0.0
            )
        }
    }

    private fun sample(points: List<Coordinate>): List<Sample> {
        val radius = (points.lastIndex / 12).coerceAtLeast(1)
        return listOf(0.15, 0.35, 0.55, 0.75, 0.9).map { fraction ->
            val index = (points.lastIndex * fraction).toInt().coerceIn(0, points.lastIndex)
            Sample(
                start = points[(index - radius).coerceAtLeast(0)],
                end = points[(index + radius).coerceAtMost(points.lastIndex)],
                probe = points[index]
            )
        }.distinctBy { it.probe }
    }

    private fun distance(a: Coordinate, b: Coordinate): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(min(1.0, h)))
    }

    private data class Sample(val start: Coordinate, val end: Coordinate, val probe: Coordinate)

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
