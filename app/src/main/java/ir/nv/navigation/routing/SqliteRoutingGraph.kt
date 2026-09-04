package ir.nv.navigation.routing

import android.database.sqlite.SQLiteDatabase
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.RoadEdge
import java.io.Closeable
import java.io.File

class SqliteRoutingGraph(databaseFile: File) : RoutingGraph, Closeable {
    private val db = SQLiteDatabase.openDatabase(
        databaseFile.absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
    )

    override fun nearestNode(point: Coordinate): Long? =
        db.rawQuery(
            """
            SELECT id FROM nodes
            WHERE latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ?
            ORDER BY ABS(latitude - ?) + ABS(longitude - ?)
            LIMIT 1
            """.trimIndent(),
            arrayOf(
                (point.latitude - SEARCH_WINDOW).toString(),
                (point.latitude + SEARCH_WINDOW).toString(),
                (point.longitude - SEARCH_WINDOW).toString(),
                (point.longitude + SEARCH_WINDOW).toString(),
                point.latitude.toString(),
                point.longitude.toString()
            )
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

    override fun coordinate(nodeId: Long): Coordinate =
        db.rawQuery(
            "SELECT latitude, longitude FROM nodes WHERE id = ?",
            arrayOf(nodeId.toString())
        ).use { cursor ->
            check(cursor.moveToFirst()) { "Missing routing node $nodeId" }
            Coordinate(cursor.getDouble(0), cursor.getDouble(1))
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
        const val SEARCH_WINDOW = 0.25
    }
}
