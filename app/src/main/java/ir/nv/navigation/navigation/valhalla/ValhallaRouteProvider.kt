package ir.nv.navigation.navigation.valhalla

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteManeuver
import ir.nv.navigation.navigation.RouteProfile
import ir.nv.navigation.navigation.RouteProvider
import ir.nv.navigation.navigation.RouteRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ValhallaRouteProvider(
    endpoint: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) : RouteProvider {
    private val routeUrl = endpoint.trim().trimEnd('/') + "/route"

    override suspend fun routes(request: RouteRequest): List<Route> = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("locations", JSONArray()
                .put(location(request.origin))
                .put(location(request.destination)))
            .put("costing", costing(request.profile))
            .put("units", "kilometers")
            .put("alternates", 2)
            .put("directions_options", JSONObject().put("units", "kilometers"))

        val httpRequest = Request.Builder()
            .url(routeUrl)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .header("Accept", "application/json")
            .build()

        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Valhalla HTTP ${response.code}")
            parseResponse(JSONObject(response.body?.string().orEmpty()))
        }
    }

    private fun parseResponse(root: JSONObject): List<Route> {
        val routes = mutableListOf<Route>()
        root.optJSONObject("trip")?.let { trip -> routes += parseTrip(trip) }
        val alternates = root.optJSONArray("alternates")
        if (alternates != null) {
            for (i in 0 until alternates.length()) {
                alternates.optJSONObject(i)?.optJSONObject("trip")?.let { routes += parseTrip(it) }
            }
        }
        if (routes.isEmpty()) throw IOException("Valhalla returned no route")
        return routes
    }

    private fun parseTrip(trip: JSONObject): Route {
        val legs = trip.optJSONArray("legs") ?: throw IOException("Valhalla route has no legs")
        val points = mutableListOf<Coordinate>()
        val maneuvers = mutableListOf<RouteManeuver>()
        var distanceMeters = 0.0
        var travelSeconds = 0.0

        for (legIndex in 0 until legs.length()) {
            val leg = legs.getJSONObject(legIndex)
            val shapePoints = decodePolyline6(leg.optString("shape"))
            if (points.isNotEmpty() && shapePoints.isNotEmpty() && points.last() == shapePoints.first()) {
                points += shapePoints.drop(1)
            } else points += shapePoints

            val summary = leg.optJSONObject("summary")
            distanceMeters += (summary?.optDouble("length", 0.0) ?: 0.0) * 1_000.0
            travelSeconds += summary?.optDouble("time", 0.0) ?: 0.0

            val legManeuvers = leg.optJSONArray("maneuvers")
            if (legManeuvers != null) {
                for (i in 0 until legManeuvers.length()) {
                    val value = legManeuvers.getJSONObject(i)
                    val street = value.optJSONArray("street_names")?.optString(0)?.takeIf(String::isNotBlank)
                    maneuvers += RouteManeuver(
                        instruction = value.optString("instruction").ifBlank { "ادامه مسیر" },
                        roadName = street,
                        distanceMeters = value.optDouble("length", 0.0) * 1_000.0,
                        direction = direction(value.optInt("type", 0)),
                        coordinate = shapePoints.getOrNull(value.optInt("begin_shape_index", -1))
                    )
                }
            }
        }

        if (points.size < 2) throw IOException("Valhalla returned invalid route geometry")
        return Route(
            points = points,
            edgeIds = emptyList(),
            distanceMeters = distanceMeters,
            travelSeconds = travelSeconds,
            maneuvers = maneuvers
        )
    }

    private fun location(coordinate: Coordinate) = JSONObject()
        .put("lat", coordinate.latitude)
        .put("lon", coordinate.longitude)

    private fun costing(profile: RouteProfile): String = when (profile) {
        RouteProfile.ECO -> "auto"
        RouteProfile.SCENIC -> "auto"
        RouteProfile.AVOID_HIGHWAY -> "auto"
        RouteProfile.AVOID_TOLL -> "auto"
        else -> "auto"
    }

    private fun direction(type: Int): RouteManeuver.Direction = when (type) {
        1, 2 -> RouteManeuver.Direction.STRAIGHT
        8, 9, 10 -> RouteManeuver.Direction.RIGHT
        15, 16, 17 -> RouteManeuver.Direction.LEFT
        12, 19 -> RouteManeuver.Direction.UTURN
        4, 5 -> RouteManeuver.Direction.ARRIVE
        else -> RouteManeuver.Direction.STRAIGHT
    }

    private fun decodePolyline6(encoded: String): List<Coordinate> {
        if (encoded.isBlank()) return emptyList()
        val result = ArrayList<Coordinate>()
        var index = 0
        var latitude = 0L
        var longitude = 0L
        while (index < encoded.length) {
            val lat = decodeValue(encoded, index)
            index = lat.nextIndex
            latitude += lat.delta
            val lon = decodeValue(encoded, index)
            index = lon.nextIndex
            longitude += lon.delta
            result += Coordinate(latitude / 1e6, longitude / 1e6)
        }
        return result
    }

    private fun decodeValue(value: String, start: Int): Decoded {
        var result = 0L
        var shift = 0
        var index = start
        while (index < value.length) {
            val b = value[index++].code - 63
            result = result or ((b and 0x1f).toLong() shl shift)
            if (b < 0x20) break
            shift += 5
        }
        val delta = if ((result and 1L) != 0L) (result shr 1).inv() else result shr 1
        return Decoded(delta, index)
    }

    private data class Decoded(val delta: Long, val nextIndex: Int)

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
