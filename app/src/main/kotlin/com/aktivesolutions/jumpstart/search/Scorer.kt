package com.aktivesolutions.jumpstart.search

/** Port of the mockup's score(): 3 prefix, 2.5 word-prefix or user tag-prefix, 2 substring, 1 keyword-prefix, 0 none. q must be lowercase. */
object Scorer {
    private val wordSplit = Regex("[\\s&-]+")

    fun splitName(nameLower: String): List<String> = nameLower.split(wordSplit)
    fun splitKeywords(extra: String?): List<String> = if (extra.isNullOrEmpty()) emptyList() else extra.split(" ")

    /** Scores pre-tokenised fields; this is the hot path (once per item per keystroke), allocation free. */
    fun score(nameLower: String, nameWords: List<String>, keywordTokens: List<String>, q: String, tagTokens: List<String> = emptyList()): Double {
        if (nameLower.startsWith(q)) return 3.0
        for (w in nameWords) if (w.startsWith(q)) return 2.5
        for (t in tagTokens) if (t.startsWith(q)) return 2.5 // explicit user tag, same as a name-word match
        if (nameLower.contains(q)) return 2.0
        for (k in keywordTokens) if (k.startsWith(q)) return 1.0
        return 0.0
    }

    fun score(item: Item, q: String): Double = score(item.nameLower, item.nameWords, item.keywordTokens, q, item.tagTokens)

    /** Convenience for raw strings (tests, one-offs). */
    fun score(name: String, extra: String?, q: String): Double {
        val n = name.lowercase()
        return score(n, splitName(n), splitKeywords(extra), q)
    }
}
