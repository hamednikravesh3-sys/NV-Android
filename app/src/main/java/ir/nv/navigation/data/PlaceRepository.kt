package ir.nv.navigation.data

import android.database.sqlite.SQLiteDatabase
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.routing.RouteInsightEngine
import java.io.Closeable
import java.io.File
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class PlaceRepository(databaseFile: File) : Closeable {
    private val db = SQLiteDatabase.openDatabase(
        databaseFile.absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
    )

    fun search(rawQuery: String, limit: Int = 30): List<Place> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return emptyList()
        val numericCode = PlaceCodes.publicCode(query)
        val sql: String
        val args: Array<String>
        if (numericCode != null) {
            sql = """
                SELECT code, name, latitude, longitude, category
                FROM places WHERE code = ? LIMIT ?
            """.trimIndent()
            args = arrayOf(numericCode.toString(), limit.toString())
        } else {
            sql = """
                SELECT code, name, latitude, longitude, category
                FROM places
                WHERE normalized_name LIKE ? OR normalized_name LIKE ? OR name LIKE ?
                ORDER BY
                  CASE WHEN normalized_name = ? THEN 0 ELSE 1 END,
                  CASE
                    WHEN category IN ('place:city','place:town','place:village','place:suburb') THEN 0
                    WHEN category LIKE 'place:%' THEN 1
                    ELSE 2
                  END,
                  CASE WHEN normalized_name LIKE ? THEN 0 ELSE 1 END,
                  code
                LIMIT ?
            """.trimIndent()
            val normalized = PersianText.normalize(query)
            args = arrayOf(
                "$normalized%",
                "%$normalized%",
                "%$query%",
                normalized,
                "$normalized%",
                limit.toString()
            )
        }
        return db.rawQuery(sql, args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Place(
                            code = cursor.getLong(0),
                            name = cursor.getString(1),
                            coordinate = Coordinate(cursor.getDouble(2), cursor.getDouble(3)),
                            category = cursor.getString(4),
                            source = "offline",
                            confidence = 0.88
                        )
                    )
                }
            }
        }
    }

    /**
     * Radius-bounded offline POI lookup. categoryPatterns are SQL LIKE patterns such as
     * `amenity:pharmacy` or `tourism:%`; callers own the product taxonomy while this
     * repository only knows the persisted OSM-style categories.
     */
    fun nearby(
        center: Coordinate,
        categoryPatterns: List<String>,
        radiusMeters: Int,
        limit: Int = 40
    ): List<Place> {
        if (categoryPatterns.isEmpty()) return emptyList()
        val safeRadius = radiusMeters.coerceIn(1_000, 100_000)
        val safeLimit = limit.coerceIn(1, 100)
        val latitudeDelta = safeRadius / METERS_PER_LATITUDE_DEGREE
        val longitudeScale = cos(Math.toRadians(center.latitude)).let { kotlin.math.abs(it).coerceAtLeast(0.15) }
        val longitudeDelta = safeRadius / (METERS_PER_LATITUDE_DEGREE * longitudeScale)
        val categoryClause = categoryPatterns.joinToString(" OR ") { "category LIKE ?" }
        val candidateLimit = (safeLimit * 40).coerceIn(safeLimit, MAX_NEARBY_CANDIDATES)
        val args = buildList {
            add((center.latitude - latitudeDelta).toString())
            add((center.latitude + latitudeDelta).toString())
            add((center.longitude - longitudeDelta).toString())
            add((center.longitude + longitudeDelta).toString())
            addAll(categoryPatterns)
            add(candidateLimit.toString())
        }.toTypedArray()

        val candidates = db.rawQuery(
            """
                SELECT code, name, latitude, longitude, category
                FROM places
                WHERE latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ?
                  AND ($categoryClause)
                LIMIT ?
            """.trimIndent(),
            args
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Place(
                            code = cursor.getLong(0),
                            name = cursor.getString(1),
                            coordinate = Coordinate(cursor.getDouble(2), cursor.getDouble(3)),
                            category = cursor.getString(4),
                            source = "offline",
                            confidence = 0.88
                        )
                    )
                }
            }
        }

        return candidates.mapNotNull { place ->
            val distance = haversine(center, place.coordinate)
            place.takeIf { distance <= safeRadius + 1.0 }?.copy(distance = distance)
        }.sortedBy { it.distance ?: Double.MAX_VALUE }.take(safeLimit)
    }

    fun noticesAlong(route: Route, limit: Int = 8): List<RouteNotice> {
        if (route.points.size < 2) return emptyList()
        val minLatitude = route.points.minOf { it.latitude } - BOUNDS_PADDING_DEGREES
        val maxLatitude = route.points.maxOf { it.latitude } + BOUNDS_PADDING_DEGREES
        val minLongitude = route.points.minOf { it.longitude } - BOUNDS_PADDING_DEGREES
        val maxLongitude = route.points.maxOf { it.longitude } + BOUNDS_PADDING_DEGREES
        val candidates = db.rawQuery(
            """
            SELECT code, name, latitude, longitude, category
            FROM places
            WHERE latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ?
              AND (
                category LIKE 'tourism:%' OR category LIKE 'historic:%' OR category LIKE 'natural:%'
                OR category IN (
                  'amenity:fuel','amenity:parking','amenity:hospital','amenity:clinic',
                  'amenity:pharmacy','amenity:restaurant','amenity:cafe','amenity:toilets'
                )
              )
            LIMIT ?
            """.trimIndent(),
            arrayOf(
                minLatitude.toString(), maxLatitude.toString(), minLongitude.toString(),
                maxLongitude.toString(), MAX_PLACE_CANDIDATES.toString()
            )
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Place(
                            code = cursor.getLong(0),
                            name = cursor.getString(1),
                            coordinate = Coordinate(cursor.getDouble(2), cursor.getDouble(3)),
                            category = cursor.getString(4),
                            source = "offline",
                            confidence = 0.88
                        )
                    )
                }
            }
        }
        return RouteInsightEngine.placesAhead(route, candidates, limit)
    }

    override fun close() = db.close()

    private fun haversine(a: Coordinate, b: Coordinate): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(min(1.0, h)))
    }

    private companion object {
        const val BOUNDS_PADDING_DEGREES = 0.06
        const val MAX_PLACE_CANDIDATES = 5_000
        const val MAX_NEARBY_CANDIDATES = 4_000
        const val METERS_PER_LATITUDE_DEGREE = 111_320.0
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}

object PersianText {
    fun normalize(value: String): String = value
        .trim()
        .lowercase()
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace("\u200c", "")
        .replace(Regex("\\s+"), " ")
}
