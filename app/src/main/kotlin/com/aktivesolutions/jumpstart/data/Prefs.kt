package com.aktivesolutions.jumpstart.data

import android.content.Context

/** SharedPreferences-backed settings, recents and launch counts. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("jumpstart", Context.MODE_PRIVATE)

    var scheme: String
        get() = sp.getString("scheme", "Purple") ?: "Purple"
        set(v) = sp.edit().putString("scheme", v).apply()

    var themedIcons: Boolean
        get() = sp.getBoolean("themedIcons", true)
        set(v) = sp.edit().putBoolean("themedIcons", v).apply()

    /** Edge-swipe search bar over other apps (needs the "display over other apps" permission). */
    var edgeBar: Boolean
        get() = sp.getBoolean("edgeBar", false)
        set(v) = sp.edit().putBoolean("edgeBar", v).apply()

    /** Apps launched from the edge-swipe overlay open in split screen (where supported). */
    var splitLaunch: Boolean
        get() = sp.getBoolean("splitLaunch", true)
        set(v) = sp.edit().putBoolean("splitLaunch", v).apply()

    /** "Right" (default) or "Left". */
    var edgeSide: String
        get() = sp.getString("edgeSide", "Right") ?: "Right"
        set(v) = sp.edit().putString("edgeSide", v).apply()

    /** Version marker of the dictionary asset copied into filesDir (0 = never copied). */
    var dictVersion: Int
        get() = sp.getInt("dictVersion", 0)
        set(v) = sp.edit().putInt("dictVersion", v).apply()

    var recents: List<String>
        get() = sp.getString("recents", "")!!.split('\u0001').filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("recents", v.joinToString("\u0001")).apply()

    fun addRecent(q: String) {
        val t = q.trim()
        if (t.length < 2) return
        recents = (listOf(t) + recents.filter { !it.equals(t, true) }).take(MAX_RECENTS)
    }

    // Parsed once, then kept in memory (only this class writes "launches"). Copy-on-write: callers iterate the
    // returned map off the main thread (suggestedFrom on Default) while bumpLaunch runs on main, so it is never
    // mutated after being handed out.
    @Volatile private var counts: Map<String, Int>? = null

    @Synchronized
    fun launchCounts(): Map<String, Int> = counts ?: (
        sp.getString("launches", "")!!.split('\n').mapNotNull {
            val p = it.split('\t')
            if (p.size == 2) p[1].toIntOrNull()?.let { n -> p[0] to n } else null
        }.toMap(HashMap())
        ).also { counts = it }

    @Synchronized
    fun bumpLaunch(pkg: String) {
        val m = HashMap(launchCounts())
        m[pkg] = (m[pkg] ?: 0) + 1
        counts = m
        sp.edit().putString("launches", m.entries.joinToString("\n") { it.key + "\t" + it.value }).apply()
    }

    // ---- app tags: "componentKey\ttag1, tag2" per line (keys never contain tab/newline; tags are sanitised) ----
    @Volatile private var tagMap: Map<String, String>? = null

    @Synchronized
    fun appTags(): Map<String, String> = tagMap ?: (
        sp.getString("appTags", "")!!.split('\n').mapNotNull {
            val p = it.split('\t')
            if (p.size == 2 && p[1].isNotEmpty()) p[0] to p[1] else null
        }.toMap(HashMap())
        ).also { tagMap = it }

    /** [tags] is the normalised comma-joined string; empty removes the entry. */
    @Synchronized
    fun setAppTags(key: String, tags: String) {
        val m = HashMap(appTags())
        if (tags.isEmpty()) m.remove(key) else m[key] = tags.replace('\t', ' ').replace('\n', ' ')
        tagMap = m
        sp.edit().putString("appTags", m.entries.joinToString("\n") { it.key + "\t" + it.value }).apply()
    }

    @Synchronized
    fun clearAppTags() {
        tagMap = emptyMap()
        sp.edit().remove("appTags").apply()
    }

    companion object {
        const val MAX_RECENTS = 6
    }
}
