package ir.nv.navigation.data

import android.database.sqlite.SQLiteDatabase
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.routing.RouteInsightEngine
import java.io.Closeable
import java.io.File

class PlaceRepository(databaseFile: File) : Closeable {
    private val db = SQLiteDatabase.openDatabase(
        databaseFile.absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
    )

    fun search(rawQuery: String, limit: Int = 30): List<Place> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return emptyList()
        val numericCode = query.toLongOrNull()
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
                            category = cursor.getString(4)
                        )
                    )
                }
            }
        }
    }

    fun attractionsAlong(route: Route, limit: Int = 6): List<RouteNotice> {
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
              AND (category LIKE 'tourism:%' OR category LIKE 'historic:%'
                   OR category LIKE 'natural:%')
            LIMIT ?
            """.trimIndent(),
            arrayOf(
                minLatitude.toString(), maxLatitude.toString(), minLongitude.toString(),
                maxLongitude.toString(), MAX_ATTRACTION_CANDIDATES.toString()
            )
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Place(
                            code = cursor.getLong(0),
                            name = cursor.getString(1),
                            coordinate = Coordinate(cursor.getDouble(2), cursor.getDouble(3)),
                            category = cursor.getString(4)
                        )
                    )
                }
            }
        }
        return RouteInsightEngine.attractionsAhead(route, candidates, limit)
    }

    override fun close() = db.close()

    private companion object {
        const val BOUNDS_PADDING_DEGREES = 0.06
        const val MAX_ATTRACTION_CANDIDATES = 3_000
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
