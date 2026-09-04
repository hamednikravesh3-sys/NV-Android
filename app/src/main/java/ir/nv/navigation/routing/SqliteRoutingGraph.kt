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

    private val coordinateCache = object : LinkedHashMap<Long, Coordinate>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Coordinate>?): Boolean =
            size > CACHE_SIZE
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
                    (point.latitude + window).toString(),
                    (point.latitude - window).toString(),
                    (point.longitude + window).toString(),
                    (point.longitude - window).toString(),
                    point.latitude.toString(),
                    point.latitude.toString(),
                    point.longitude.toString(),
                    point.longitude.toString()
                )
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
            if (result != null) return result
        }
        return null
    }

    override fun coordinate(nodeId: Long): Coordinate = synchronized(coordinateCache) {
        coordinateCache[nodeId]?.let { return@synchronized it }
        val coordinate = db.rawQuery(
            "SELECT latitude, longitude FROM nodes WHERE id = ?",
            arrayOf(nodeId.toString())
        ).use { cursor ->
            check(cursor.moveToFirst()) { "Missing routing node $nodeId" }
            Coordinate(cursor.getDouble(0), cursor.getDouble(1))
        }
        coordinateCache[nodeId] = coordinate
        coordinate
    }

    override fun outgoing(nodeId: Long): List<RoadEdge> =
        db.rawQuery(
            """
            SELECT id, from_node, to_node, distance_m, travel_seconds, road_name
            FROM edges WHERE from_node = ?
            """.trimIndent(),
            arrayOf(nodeId.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        RoadEdge(
                            id = cursor.getLong(0),
                            fromNode = cursor.getLong(1),
                            toNode = cursor.getLong(2),
                            distanceMeters = cursor.getDouble(3),
                            travelSeconds = cursor.getDouble(4),
                            roadName = cursor.getString(5)
                        )
                    )
                }
            }
        }

    override fun isTurnAllowed(
        viaNode: Long,
        incomingEdgeId: Long?,
        outgoingEdgeId: Long
    ): Boolean {
        if (incomingEdgeId == null) return true
        return db.rawQuery(
            """
            SELECT 1 FROM turn_restrictions
            WHERE via_node = ? AND from_edge = ? AND to_edge = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(viaNode.toString(), incomingEdgeId.toString(), outgoingEdgeId.toString())
        ).use { cursor -> !cursor.moveToFirst() }
    }

    override fun close() = db.close()

    private companion object {
        val SEARCH_WINDOWS = doubleArrayOf(0.01, 0.03, 0.1, 0.25)
        const val CACHE_SIZE = 20_000
    }
}
