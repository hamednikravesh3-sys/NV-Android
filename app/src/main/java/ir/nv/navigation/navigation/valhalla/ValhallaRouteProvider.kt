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
import kotlin.math.pow

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
            .put("locations", JSONArray().put(location(request.origin)).put(location(request.destination)))
            .put("costing", "auto")
            .put("costing_options", JSONObject().put("auto", costingOptions(request)))
            .put("units", "kilometers")
            .put("alternates", 3)
            .put("directions_options", JSONObject().put("units", "kilometers"))

        val httpRequest = Request.Builder().url(routeUrl)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .header("Accept", "application/json")
            .build()
        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Valhalla HTTP ${response.code}")
            parseResponse(JSONObject(response.body?.string().orEmpty()))
        }
    }

    private fun costingOptions(request: RouteRequest): JSONObject {
        val custom = request.custom
        val options = JSONObject()
        val avoidToll = request.profile == RouteProfile.AVOID_TOLL || (request.profile == RouteProfile.CUSTOM && custom.avoidToll)
        val avoidHighway = request.profile == RouteProfile.AVOID_HIGHWAY || (request.profile == RouteProfile.CUSTOM && custom.avoidHighway)
        val avoidFerry = request.profile == RouteProfile.AVOID_FERRY || (request.profile == RouteProfile.CUSTOM && custom.avoidFerry)
        options.put("use_tolls", if (avoidToll) 0.0 else 0.5)
        options.put("use_highways", if (avoidHighway) 0.0 else if (request.profile == RouteProfile.FASTEST) 0.85 else 0.5)
        options.put("use_ferry", if (avoidFerry) 0.0 else 0.5)
        when (request.profile) {
            RouteProfile.SHORTEST -> options.put("shortest", true)
            RouteProfile.ECO -> options.put("use_highways", 0.35).put("use_tolls", 0.25)
            RouteProfile.SCENIC -> options.put("use_highways", 0.2)
            else -> Unit
        }
        return options
    }

    private fun parseResponse(root: JSONObject): List<Route> {
        val routes = mutableListOf<Route>()
        root.optJSONObject("trip")?.let { routes += parseTrip(it) }
        root.optJSONArray("alternates")?.let { alternates ->
            for (i in 0 until alternates.length()) alternates.optJSONObject(i)?.optJSONObject("trip")?.let { routes += parseTrip(it) }
        }
        if (routes.isEmpty()) throw IOException("Valhalla returned no route")
        return routes.distinctBy { route -> route.points.take(16) }.take(4)
    }

    private fun parseTrip(trip: JSONObject): Route {
        val legs = trip.optJSONArray("legs") ?: throw IOException("Valhalla route has no legs")
        val points = mutableListOf<Coordinate>()
        val maneuvers = mutableListOf<RouteManeuver>()
        var distanceMeters = 0.0
        var travelSeconds = 0.0
        val speedLimits = mutableListOf<Int>()

        for (legIndex in 0 until legs.length()) {
            val leg = legs.getJSONObject(legIndex)
            val shapePoints = decodePolyline6(leg.optString("shape"))
            if (points.isNotEmpty() && shapePoints.isNotEmpty() && points.last() == shapePoints.first()) points += shapePoints.drop(1) else points += shapePoints
            val summary = leg.optJSONObject("summary")
            distanceMeters += (summary?.optDouble("length", 0.0) ?: 0.0) * 1_000.0
            travelSeconds += summary?.optDouble("time", 0.0) ?: 0.0

            leg.optJSONArray("maneuvers")?.let { values ->
                for (i in 0 until values.length()) {
                    val value = values.getJSONObject(i)
                    val street = value.optJSONArray("street_names")?.optString(0)?.takeIf(String::isNotBlank)
                    val sign = value.optJSONObject("sign")
                    val exitNumber = firstText(sign?.optJSONArray("exit_numbers"))
                    val junctionName = firstText(sign?.optJSONArray("junction_names")) ?: firstText(sign?.optJSONArray("exit_names"))
                    val speedLimit = value.optInt("speed_limit", 0).takeIf { it > 0 }
                    speedLimit?.let(speedLimits::add)
                    maneuvers += RouteManeuver(
                        instruction = value.optString("instruction").ifBlank { "ادامه مسیر" },
                        roadName = street,
                        distanceMeters = value.optDouble("length", 0.0) * 1_000.0,
                        direction = direction(value.optInt("type", 0)),
                        coordinate = shapePoints.getOrNull(value.optInt("begin_shape_index", -1)),
                        lanes = parseLanes(value.optJSONArray("lanes")),
                        exitNumber = exitNumber,
                        junctionName = junctionName,
                        speedLimitKmh = speedLimit
                    )
                }
            }
        }
        if (points.size < 2) throw IOException("Valhalla returned invalid route geometry")
        val speedRatio = ((distanceMeters / travelSeconds.coerceAtLeast(1.0)) / 13.89)
        val energy = (distanceMeters / 1_000.0) * (1.0 + 0.35 * speedRatio.pow(2.0))
        return Route(points, emptyList(), distanceMeters, travelSeconds, maneuvers, estimatedEnergyIndex = energy, speedLimitsKmh = speedLimits.distinct())
    }

    private fun firstText(values: JSONArray?): String? {
        if (values == null || values.length() == 0) return null
        val first = values.opt(0)
        return when (first) {
            is JSONObject -> first.optString("text").takeIf(String::isNotBlank)
            is String -> first.takeIf(String::isNotBlank)
            else -> null
        }
    }

    private fun parseLanes(values: JSONArray?): List<RouteManeuver.Lane> {
        if (values == null) return emptyList()
        return buildList {
            for (i in 0 until values.length()) {
                val lane = values.optJSONObject(i) ?: continue
                val active = lane.optBoolean("active", lane.optBoolean("valid", false))
                val indications = lane.optJSONArray("indications")
                val indication = indications?.optString(0).orEmpty()
                add(RouteManeuver.Lane(laneDirection(indication), active))
            }
        }
    }

    private fun laneDirection(value: String): RouteManeuver.Direction = when {
        value.contains("left", true) -> RouteManeuver.Direction.LEFT
        value.contains("right", true) -> RouteManeuver.Direction.RIGHT
        else -> RouteManeuver.Direction.STRAIGHT
    }

    private fun location(coordinate: Coordinate) = JSONObject().put("lat", coordinate.latitude).put("lon", coordinate.longitude)

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
        var index = 0; var latitude = 0L; var longitude = 0L
        while (index < encoded.length) {
            val lat = decodeValue(encoded, index); index = lat.nextIndex; latitude += lat.delta
            val lon = decodeValue(encoded, index); index = lon.nextIndex; longitude += lon.delta
            result += Coordinate(latitude / 1e6, longitude / 1e6)
        }
        return result
    }

    private fun decodeValue(value: String, start: Int): Decoded {
        var result = 0L; var shift = 0; var index = start
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
    private companion object { val JSON_MEDIA = "application/json; charset=utf-8".toMediaType() }
}
