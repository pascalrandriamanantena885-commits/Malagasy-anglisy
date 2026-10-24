package mg.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.text.Normalizer

data class Meaning(val translation: String, val pos: String, val exSrc: String, val exDst: String)
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
        val sql = "SELECT $src, $dst, pos, example_$src, example_$dst FROM entries " +
            "WHERE ${src}_norm LIKE ? " +
            "ORDER BY (${src}_norm = ?) DESC, length(${src}_norm), ${src}_norm LIMIT 200"
        val groups = LinkedHashMap<String, MutableList<Meaning>>()
        db.rawQuery(sql, arrayOf("$q%", q)).use { c ->
            while (c.moveToNext()) {
                val head = c.getString(0) ?: continue
                groups.getOrPut(head) { mutableListOf() }.add(
                    Meaning(
                        c.getString(1) ?: "",
                        c.getString(2) ?: "",
                        c.getString(3) ?: "",
                        c.getString(4) ?: ""
                    )
                )
            }
        }
        return groups.map { Result(it.key, it.value) }
    }

    companion object {
        fun normalize(s: String): String =
            Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
    }
}
