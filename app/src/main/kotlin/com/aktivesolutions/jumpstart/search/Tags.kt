package com.aktivesolutions.jumpstart.search

/** Pure helpers for package-name keyword tokens and user tags. */
object Tags {
    private val noise = setOf(
        "com", "org", "net", "android", "app", "apps", "mobile", "client", "launcher", "main", "activity",
        "google", "gms", "sec", "samsung", "oppo", "coloros", "heytap", "oneplus", "miui", "xiaomi", "huawei",
    )
    private val camelBoundary = Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")
    private val separators = Regex("[._\\-\\s]+")

    const val MAX_TAGS = 20
    const val MAX_TAG_LEN = 40

    /** `com.facebook.lite` -> [facebook, lite]; camelCase split, lowercased, noise/short/numeric tokens dropped, deduped. */
    fun packageTokens(pkg: String): List<String> {
        val out = LinkedHashSet<String>()
        for (part in pkg.split(separators)) {
            for (t in part.split(camelBoundary)) {
                val l = t.lowercase()
                if (l.length < 3 || l in noise || l.all { it.isDigit() }) continue
                out += l
            }
        }
        return out.toList()
    }

    /** Package part of a flattened component key (`pkg/cls`), or the key itself. */
    fun packageOf(key: String): String = key.substringBefore('/')

    /** User input "Social, FB lite" -> ["social", "fb lite"]: comma/newline separated, lowercased, trimmed, deduped, bounded. */
    fun parseTags(raw: String): List<String> =
        raw.split(',', '\n').map { it.trim().lowercase().replace(Regex("\\s+"), " ").cut(MAX_TAG_LEN).trimEnd() }
            .filter { it.isNotEmpty() }.distinct().take(MAX_TAGS)

    /** take(n) that never leaves a lone high surrogate (half an emoji) at the end. */
    private fun String.cut(n: Int): String = take(n).let { if (it.isNotEmpty() && it.last().isHighSurrogate()) it.dropLast(1) else it }

    fun joinTags(tags: List<String>): String = tags.joinToString(", ")

    /** Searchable tokens of a stored tag string: each whole tag plus its words ("fb lite, social" -> "fb lite", fb, lite, social). */
    fun tagTokens(tags: String): List<String> {
        if (tags.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        for (t in tags.split(',')) {
            val tag = t.trim()
            if (tag.isEmpty()) continue
            out += tag
            for (w in tag.split(' ')) if (w.isNotEmpty()) out += w
        }
        return out.toList()
    }
}
