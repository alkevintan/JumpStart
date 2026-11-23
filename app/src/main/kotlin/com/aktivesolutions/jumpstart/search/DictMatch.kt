package com.aktivesolutions.jumpstart.search

import androidx.compose.runtime.Immutable

@Immutable
data class Sense(val pos: String, val def: String)

@Immutable
data class DictEntry(val word: String, val phon: String?, val senses: List<Sense>)

/** Backing store for dictFor(); implemented by SQLite in data/ and by fakes in tests. */
interface DictSource {
    fun exact(word: String): DictEntry?
    fun prefix(prefix: String): DictEntry?
}

private val WORD = Regex("^[a-z]{3,}$")

/** True when [q] is a candidate for a dictionary lookup (cheap check, no I/O). */
fun isDictQuery(q: String): Boolean = WORD.matches(q)

/**
 * Exclusive upper bound for the index range scan `w >= prefix AND w < bound` that selects every word starting
 * with [prefix]: the prefix with its last char incremented ("mang" -> "manh"). A trailing U+FFFF can't be
 * incremented, so it is dropped first.
 */
fun prefixUpperBound(prefix: String): String {
    var end = prefix.length
    while (end > 0 && prefix[end - 1] == '￿') end--
    if (end == 0) return "" // no finite bound: caller treats empty as "unbounded"
    return prefix.substring(0, end - 1) + (prefix[end - 1] + 1)
}

/** Matching rules from the mockup's dictFor(): alphabetic >= 3 chars; exact first, else prefix if >= 4 chars. */
fun dictFor(q: String, source: DictSource): DictEntry? {
    if (!isDictQuery(q)) return null
    return source.exact(q) ?: if (q.length >= 4) source.prefix(q) else null
}
