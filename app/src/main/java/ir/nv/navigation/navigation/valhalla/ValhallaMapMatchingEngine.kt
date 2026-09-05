package ir.nv.navigation.navigation.valhalla

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.navigation.mapmatching.MapMatchingEngine
import ir.nv.navigation.navigation.mapmatching.MatchedLocation
import ir.nv.navigation.navigation.mapmatching.RawLocationSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

class ValhallaMapMatchingEngine(
    endpoint: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
) : MapMatchingEngine {
    private val traceUrl = endpoint.trim().trimEnd('/') + "/trace_attributes"
    private val history = ArrayDeque<RawLocationSample>()

    override suspend fun match(sample: RawLocationSample): MatchedLocation? {
        synchronized(history) {
            history.addLast(sample)
            while (history.size > MAX_SAMPLES) history.removeFirst()
        }
        val samples = synchronized(history) { history.toList() }
        if (samples.size < 2) return MatchedLocation(sample.coordinate, confidence(sample.accuracyMeters))

        return withContext(Dispatchers.IO) {
            runCatching { requestMatch(samples) }
                .getOrElse { MatchedLocation(sample.coordinate, confidence(sample.accuracyMeters)) }
        }
    }

    private fun requestMatch(samples: List<RawLocationSample>): MatchedLocation? {
        val shape = JSONArray()
        samples.forEach { sample ->
            shape.put(
                JSONObject()
                    .put("lat", sample.coordinate.latitude)
                    .put("lon", sample.coordinate.longitude)
                    .put("time", sample.timestampMillis / 1_000)
                    .put("accuracy", sample.accuracyMeters.toDouble())
            )
        }
        val payload = JSONObject()
            .put("shape", shape)
            .put("costing", "auto")
            .put("shape_match", "map_snap")
            .put("filters", JSONObject()
                .put("action", "include")
                .put("attributes", JSONArray().put("matched.point").put("edge.names")))

        val request = Request.Builder()
            .url(traceUrl)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .header("Accept", "application/json")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            parse(JSONObject(response.body?.string().orEmpty()), samples.last())
        }
    }

    private fun parse(root: JSONObject, fallback: RawLocationSample): MatchedLocation {
        val points = root.optJSONArray("matched_points")
        val last = points?.optJSONObject((points.length() - 1).coerceAtLeast(0))
        val lat = last?.optDouble("lat", Double.NaN) ?: Double.NaN
        val lon = last?.optDouble("lon", Double.NaN) ?: Double.NaN
        val coordinate = if (lat.isFinite() && lon.isFinite()) Coordinate(lat, lon) else fallback.coordinate
        val type = last?.optString("type").orEmpty()
        val matchConfidence = when (type) {
            "matched" -> 0.95
            "interpolated" -> 0.80
            else -> confidence(fallback.accuracyMeters)
        }
        return MatchedLocation(coordinate = coordinate, confidence = matchConfidence)
    }

    private fun confidence(accuracy: Float): Double = when {
        accuracy <= 5f -> 0.95
        accuracy <= 10f -> 0.85
        accuracy <= 20f -> 0.70
        accuracy <= 40f -> 0.50
        else -> 0.30
    }

    private companion object {
        const val MAX_SAMPLES = 6
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
