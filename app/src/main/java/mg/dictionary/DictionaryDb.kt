package mg.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.text.Normalizer

data class Meaning(
    val id: Long,
    val translation: String,
    val pos: String,
    val defMg: String,
    val defEn: String,
    val exSrc: String,
    val exDst: String,
    val hasImage: Boolean,
    val verified: Boolean
)

data class Result(val headword: String, val meanings: List<Meaning>)

class DictionaryDb(private val context: Context) {

    private val db: SQLiteDatabase by lazy { open() }

    private fun open(): SQLiteDatabase {
        val file: File = context.getDatabasePath("dictionary.db")
        val stamp = context.packageManager
            .getPackageInfo(context.packageName, 0).lastUpdateTime
        val prefs = context.getSharedPreferences("db", Context.MODE_PRIVATE)
        if (!file.exists() || prefs.getLong("stamp", -1L) != stamp) {
            file.parentFile?.mkdirs()
            context.assets.open("dictionary.db").use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            prefs.edit().putLong("stamp", stamp).apply()
        }
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    fun search(query: String, mgToEn: Boolean): List<Result> {
        val src = if (mgToEn) "mg" else "en"
        val dst = if (mgToEn) "en" else "mg"
        val q = normalize(query).replace("%", "").replace("_", "")
        val sql = "SELECT id, $src, $dst, pos, definition_mg, definition_en, " +
            "example_$src, example_$dst, (image IS NOT NULL), verified FROM entries " +
            "WHERE ${src}_norm LIKE ? " +
            "ORDER BY (${src}_norm = ?) DESC, length(${src}_norm), ${src}_norm LIMIT 200"
        val groups = LinkedHashMap<String, MutableList<Meaning>>()
        db.rawQuery(sql, arrayOf("$q%", q)).use { c ->
            while (c.moveToNext()) {
                val head = c.getString(1) ?: continue
                groups.getOrPut(head) { mutableListOf() }.add(
                    Meaning(
                        id = c.getLong(0),
                        translation = c.getString(2) ?: "",
                        pos = c.getString(3) ?: "",
                        defMg = c.getString(4) ?: "",
                        defEn = c.getString(5) ?: "",
                        exSrc = c.getString(6) ?: "",
                        exDst = c.getString(7) ?: "",
                        hasImage = c.getInt(8) == 1,
                        verified = c.getInt(9) == 1
                    )
                )
            }
        }
        return groups.map { Result(it.key, it.value) }
    }

    fun image(id: Long): Bitmap? =
        db.rawQuery("SELECT image FROM entries WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) {
                c.getBlob(0)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            } else null
        }

    companion object {
        fun normalize(s: String): String =
            Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
    }
}
