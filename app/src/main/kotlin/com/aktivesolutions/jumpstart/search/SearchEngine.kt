package com.aktivesolutions.jumpstart.search

import androidx.compose.runtime.Immutable

enum class Kind { APP, CONTACT, SETTING }

/**
 * One searchable thing. [key] = flattened component (app), phone number (contact) or setting id.
 *
 * The lowercase name, its word tokens and the keyword tokens are computed once when the Item is built
 * (once per app-list load), so scoring a keystroke does no string allocation per item.
 */
@Immutable
data class Item(
    val kind: Kind,
    val key: String,
    val title: String,
    val subtitle: String,
    val keywords: String = "",
    val score: Double = 0.0,
    val nameLower: String = title.lowercase(),
    val nameWords: List<String> = Scorer.splitName(nameLower),
    val keywordTokens: List<String> = Scorer.splitKeywords(keywords),
    /** User tags, comma separated as entered (apps only); [tagTokens] is the precomputed searchable form. */
    val tags: String = "",
    val tagTokens: List<String> = Tags.tagTokens(tags),
) {
    fun withTags(t: String): Item = copy(tags = t, tagTokens = Tags.tagTokens(t))
}

@Immutable
data class Group(val name: String, val items: List<Item>)

@Immutable
data class SearchResult(
    val answer: Answer? = null,
    val top: Item? = null,
    val groups: List<Group> = emptyList(),
    val showWeb: Boolean = false,
)

/** Combines the three sources into a SearchResult using the mockup's ranking rules. */
object SearchEngine {
    /** [answer] is passed in by callers that already computed it (so contacts can be skipped when answered). */
    fun search(
        query: String,
        apps: List<Item>,
        contacts: List<Item>,
        settings: List<Item>,
        answer: Answer? = Converter.answerFor(query),
    ): SearchResult {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return SearchResult()

        // Only matching items are copied (to carry their score); non-matches allocate nothing.
        fun rank(list: List<Item>): List<Item> {
            val out = ArrayList<Item>()
            for (item in list) {
                val s = Scorer.score(item, q)
                if (s > 0) out.add(item.copy(score = s))
            }
            return out.sortedByDescending { it.score } // stable: ties keep source order
        }

        var mA = rank(apps)
        var mC = rank(contacts)
        var mS = rank(settings)
        var top: Item? = null
        if (answer == null) {
            // First item with the highest score in apps, contacts, settings order (same as a stable sort).
            for (l in listOf(mA, mC, mS)) for (item in l) if (top == null || item.score > top.score) top = item
            if (top != null) {
                mA = mA.filter { it !== top }
                mC = mC.filter { it !== top }
                mS = mS.filter { it !== top }
            }
        }
        val groups = buildList {
            if (mA.isNotEmpty()) add(Group("Apps", mA.take(4)))
            if (mC.isNotEmpty()) add(Group("Contacts", mC.take(3)))
            if (mS.isNotEmpty()) add(Group("Settings", mS.take(3)))
        }
        return SearchResult(answer, top, groups, showWeb = answer == null)
    }
}
