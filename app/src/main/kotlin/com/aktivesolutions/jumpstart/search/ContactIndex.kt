package com.aktivesolutions.jumpstart.search

import androidx.compose.runtime.Immutable

/** One phone-number row from the contacts provider. */
class ContactRow(val name: String, val number: String, val primary: Boolean)

/**
 * In-memory contact index, built once when permission is granted (and rebuilt when contacts change).
 * One Item per distinct display name (primary number preferred, else the first row), sorted by name
 * case-insensitively; [search] is a pure in-memory substring scan over the precomputed lowercase names.
 */
@Immutable
class ContactIndex private constructor(val items: List<Item>) {
    /** [q] must be trimmed + lowercase. Matches are the first [limit] names (alphabetical) containing [q]. */
    fun search(q: String, limit: Int = LIMIT): List<Item> {
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<Item>()
        for (it in items) {
            if (it.nameLower.contains(q)) {
                out += it
                if (out.size >= limit) break
            }
        }
        return out
    }

    companion object {
        const val LIMIT = 25
        val EMPTY = ContactIndex(emptyList())

        fun build(rows: List<ContactRow>): ContactIndex {
            if (rows.isEmpty()) return EMPTY
            val byName = LinkedHashMap<String, ContactRow>()
            for (r in rows) {
                val cur = byName[r.name]
                if (cur == null || (r.primary && !cur.primary)) byName[r.name] = r
            }
            val items = byName.values.map { Item(Kind.CONTACT, it.number, it.name, it.number) }
            return ContactIndex(items.sortedBy { it.nameLower })
        }
    }
}
