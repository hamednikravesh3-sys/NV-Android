package ir.nv.navigation.data

import android.database.sqlite.SQLiteDatabase
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import java.io.Closeable
import java.io.File

class PlaceRepository(databaseFile: File) : Closeable {
    private val db = SQLiteDatabase.openDatabase(
        databaseFile.absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
    )

    fun search(rawQuery: String, limit: Int = 20): List<Place> {
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
                WHERE normalized_name LIKE ? OR name LIKE ?
                ORDER BY CASE WHEN normalized_name = ? THEN 0 ELSE 1 END, code
                LIMIT ?
            """.trimIndent()
            val normalized = PersianText.normalize(query)
            args = arrayOf("$normalized%", "%$query%", normalized, limit.toString())
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

    override fun close() = db.close()
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
