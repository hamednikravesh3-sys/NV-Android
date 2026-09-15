package ir.nv.navigation.routing

import android.database.sqlite.SQLiteDatabase
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.RoadEdge
import java.io.Closeable
import java.io.File
import java.util.LinkedHashMap

class SqliteRoutingGraph(databaseFile: File) : RoutingGraph, Closeable {
    private val db = SQLiteDatabase.openDatabase(
        databaseFile.absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
    )
    private val edgeColumns: Set<String> by lazy {
        db.rawQuery("PRAGMA table_info(edges)", emptyArray()).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }
    }

    private val coordinateCache = object : LinkedHashMap<Long, Coordinate>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Coordinate>?): Boolean = size > CACHE_SIZE
    }

    override fun nearestNode(point: Coordinate): Long? {
        for (window in SEARCH_WINDOWS) {
            val result = db.rawQuery(
                """
                SELECT n.id FROM nodes_index i
                JOIN nodes n ON n.id = i.id
                WHERE i.min_latitude <= ? AND i.max_latitude >= ?
                  AND i.min_longitude <= ? AND i.max_longitude >= ?
                ORDER BY ((n.latitude - ?) * (n.latitude - ?)) +
                         ((n.longitude - ?) * (n.longitude - ?))
                LIMIT 1
                """.trimIndent(),
                arrayOf(
                    (point.latitude + window).toString(), (point.latitude - window).toString(),
                    (point.longitude + window).toString(), (point.longitude - window).toString(),
                    point.latitude.toString(), point.latitude.toString(),
                    point.longitude.toString(), point.longitude.toString()
                )
            ).use { if (it.moveToFirst()) it.getLong(0) else null }
            if (result != null) return result
        }
        return null
    }

    override fun coordinate(nodeId: Long): Coordinate = synchronized(coordinateCache) {
        coordinateCache[nodeId]?.let { return@synchronized it }
        val coordinate = db.rawQuery(
            "SELECT latitude, longitude FROM nodes WHERE id = ?", arrayOf(nodeId.toString())
        ).use { cursor ->
            check(cursor.moveToFirst()) { "Missing routing node $nodeId" }
            Coordinate(cursor.getDouble(0), cursor.getDouble(1))
        }
        coordinateCache[nodeId] = coordinate
        coordinate
    }

    override fun outgoing(nodeId: Long): List<RoadEdge> {
        fun expr(column: String, fallback: String) = if (column in edgeColumns) column else "$fallback AS $column"
        val sql = """
            SELECT id, from_node, to_node, distance_m, travel_seconds, road_name,
                   ${expr("speed_limit_kmh", "NULL")},
                   ${expr("highway_class", "NULL")},
                   ${expr("toll", "0")},
                   ${expr("ferry", "0")},
                   ${expr("surface", "NULL")},
                   ${expr("lane_count", "NULL")}
            FROM edges WHERE from_node = ?
        """.trimIndent()
        return db.rawQuery(sql, arrayOf(nodeId.toString())).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        RoadEdge(
                            id = cursor.getLong(0),
                            fromNode = cursor.getLong(1),
                            toNode = cursor.getLong(2),
                            distanceMeters = cursor.getDouble(3),
                            travelSeconds = cursor.getDouble(4),
                            roadName = cursor.getString(5),
                            speedLimitKmh = if (cursor.isNull(6)) null else cursor.getInt(6),
                            highwayClass = if (cursor.isNull(7)) null else cursor.getString(7),
                            toll = cursor.getInt(8) != 0,
                            ferry = cursor.getInt(9) != 0,
                            surface = if (cursor.isNull(10)) null else cursor.getString(10),
                            laneCount = if (cursor.isNull(11)) null else cursor.getInt(11)
                        )
                    )
                }
            }
        }
    }

    override fun isTurnAllowed(viaNode: Long, incomingEdgeId: Long?, outgoingEdgeId: Long): Boolean {
        if (incomingEdgeId == null) return true
        return db.rawQuery(
            "SELECT 1 FROM turn_restrictions WHERE via_node = ? AND from_edge = ? AND to_edge = ? LIMIT 1",
            arrayOf(viaNode.toString(), incomingEdgeId.toString(), outgoingEdgeId.toString())
        ).use { !it.moveToFirst() }
    }

    override fun close() = db.close()

    private companion object {
        val SEARCH_WINDOWS = doubleArrayOf(0.01, 0.03, 0.1, 0.25)
        const val CACHE_SIZE = 20_000
    }
}
