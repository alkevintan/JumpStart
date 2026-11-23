package com.aktivesolutions.jumpstart.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDoneException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteStatement
import com.aktivesolutions.jumpstart.search.DictEntry
import com.aktivesolutions.jumpstart.search.DictSource
import com.aktivesolutions.jumpstart.search.Sense
import com.aktivesolutions.jumpstart.search.prefixUpperBound
import java.io.File

/**
 * Offline WordNet 3.0 dictionary (assets/dictionary.db, built by tools/build_dictionary.py).
 *
 * Schema: e(w TEXT PRIMARY KEY, s TEXT) WITHOUT ROWID, so both the exact lookup (w = ?) and the prefix lookup
 * (w >= ? AND w < ?) are index range scans on the table's own b-tree.
 *
 * Copied to app storage only when [ASSET_VERSION] differs from the marker stored in [Prefs] (no per-start file checks),
 * then opened once and held for the process lifetime with two compiled statements. Results (including misses)
 * are kept in a 128-entry LRU. All methods block: call off the main thread ([warmUp] does the one-time open).
 */
class Dictionary(private val context: Context, private val prefs: Prefs) : DictSource {
    private val lock = Any()
    private var db: SQLiteDatabase? = null
    private var exactStmt: SQLiteStatement? = null
    private var prefixStmt: SQLiteStatement? = null
    // Set after a failed copy/open so we don't re-copy 15 MB on every keystroke (e.g. storage full).
    private var failed = false
    private val cache = LruMap<String, Cached<DictEntry>>(CACHE_ENTRIES)

    /** Copies (if needed) and opens the database; call once from a background thread at startup. */
    fun warmUp() { open() }

    private fun open(): SQLiteDatabase? = synchronized(lock) {
        db?.let { return it }
        if (failed) return null
        val dir = File(context.filesDir, "dict")
        val f = File(dir, "dictionary-v$ASSET_VERSION.db")
        val tmp = File(dir, "dictionary-v$ASSET_VERSION.tmp")
        try {
            if (prefs.dictVersion != ASSET_VERSION) {
                // Marker changed (first run or new asset version). An intact copy from before the marker existed is adopted.
                if (!f.exists() || f.length() == 0L) {
                    dir.mkdirs()
                    context.assets.open("dictionary.db").use { i -> tmp.outputStream().use { o -> i.copyTo(o, 64 * 1024) } }
                    if (!tmp.renameTo(f)) throw java.io.IOException("rename failed")
                }
                dir.listFiles()?.forEach { if (it.name.startsWith("dictionary-") && it.name != f.name) it.delete() }
                prefs.dictVersion = ASSET_VERSION
            }
            val d = SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
            exactStmt = d.compileStatement("SELECT s FROM e WHERE w = ?")
            prefixStmt = d.compileStatement("SELECT w FROM e WHERE w >= ?1 AND w < ?2 ORDER BY length(w), w LIMIT 1")
            d.also { db = it }
        } catch (_: Exception) {
            tmp.delete()
            f.delete()
            prefs.dictVersion = 0
            failed = true
            null
        }
    }

    private fun parse(word: String, s: String) = DictEntry(
        word, null,
        s.split('\n').mapNotNull {
            val p = it.split('\t', limit = 2)
            if (p.size == 2) Sense(p[0], p[1]) else null
        },
    )

    private fun SQLiteStatement.firstString(): String? = try {
        simpleQueryForString()
    } catch (_: SQLiteDoneException) {
        null // no row
    }

    /** Cache-through lookup. A corrupt/unreadable DB yields null (not cached) rather than crashing the search. */
    private inline fun cached(key: String, load: () -> DictEntry?): DictEntry? {
        cache.get(key)?.let { return it.value }
        if (open() == null) return null
        val v = try {
            synchronized(lock) { load() }
        } catch (_: SQLiteException) {
            return null
        }
        cache.put(key, Cached(v))
        return v
    }

    private fun lookup(word: String): DictEntry? {
        val st = exactStmt ?: return null
        st.clearBindings()
        st.bindString(1, word)
        return st.firstString()?.let { parse(word, it) }
    }

    override fun exact(word: String): DictEntry? = cached("e:$word") { lookup(word) }

    override fun prefix(prefix: String): DictEntry? = cached("p:$prefix") {
        val st = prefixStmt ?: return@cached null
        st.clearBindings()
        st.bindString(1, prefix)
        st.bindString(2, prefixUpperBound(prefix))
        st.firstString()?.let { w -> lookup(w) }
    }

    companion object {
        /** Bump when assets/dictionary.db changes; triggers a one-time re-copy. */
        const val ASSET_VERSION = 1
        const val CACHE_ENTRIES = 128
    }
}
