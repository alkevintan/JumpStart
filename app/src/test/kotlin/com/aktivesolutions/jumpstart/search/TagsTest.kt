package com.aktivesolutions.jumpstart.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagsTest {
    @Test fun facebookLite() = assertEquals(listOf("facebook", "lite"), Tags.packageTokens("com.facebook.lite"))
    @Test fun tiktok() = assertEquals(listOf("zhiliaoapp", "musically"), Tags.packageTokens("com.zhiliaoapp.musically"))
    @Test fun noiseRemoved() = assertEquals(listOf("camera"), Tags.packageTokens("com.oppo.coloros.android.camera"))
    @Test fun shortAndNumericDropped() = assertEquals(listOf("tool"), Tags.packageTokens("com.a1.x2.123.tool.42"))
    @Test fun camelCaseAndUnderscore() =
        assertEquals(listOf("whats", "messenger", "photo", "editor"), Tags.packageTokens("org.whatsMessenger.photo_editor"))
    @Test fun deduped() = assertEquals(listOf("lite"), Tags.packageTokens("lite.lite.Lite"))
    @Test fun packageOfKey() = assertEquals("com.facebook.lite", Tags.packageOf("com.facebook.lite/com.facebook.lite.MainActivity"))

    @Test fun parseTags() =
        assertEquals(listOf("fb lite", "social"), Tags.parseTags("  FB   Lite , social,, Social \n"))
    @Test fun parseTagsNeverSplitsSurrogatePair() {
        val t = Tags.parseTags("a".repeat(Tags.MAX_TAG_LEN - 1) + "\uD83D\uDE00x").single()
        assertTrue(t.isNotEmpty() && !t.last().isHighSurrogate())
    }
    @Test fun parseTagsBoundsAndEmpty() {
        assertEquals(emptyList<String>(), Tags.parseTags(" , ,\n"))
        assertEquals(Tags.MAX_TAGS, Tags.parseTags((1..50).joinToString(",") { "t$it" }).size)
        assertEquals(listOf("say \"hi\"", "o'neil"), Tags.parseTags("say \"hi\", O'Neil"))
    }
    @Test fun packageTokensEmptyAndUnicode() {
        assertEquals(emptyList<String>(), Tags.packageTokens(""))
        assertEquals(listOf("café"), Tags.packageTokens("com.café"))
    }
    @Test fun tagTokensIncludeWholeTagAndWords() =
        assertEquals(listOf("fb lite", "fb", "lite", "social"), Tags.tagTokens("fb lite, social"))

    // ---- scoring ----
    private fun item(name: String, kw: String = "", tags: String = "") = Item(Kind.APP, name, name, "App", kw).withTags(tags)

    @Test fun packageTokenScoresKeywordTier() {
        val fb = item("FB Lite", Tags.packageTokens("com.facebook.lite").joinToString(" "))
        assertEquals(1.0, Scorer.score(fb, "facebook"), 0.0)
    }
    @Test fun nameMatchOutranksPackageToken() {
        val apps = listOf(
            item("Lite", Tags.packageTokens("com.facebook.lite").joinToString(" ")),
            item("Facebook Reader"),
        )
        val r = SearchEngine.search("facebook", apps, emptyList(), emptyList())
        assertEquals("Facebook Reader", r.top!!.title)
        assertEquals(listOf("Lite"), r.groups.single().items.map { it.title })
    }
    @Test fun tagScoresTwoPointFive() {
        assertEquals(2.5, Scorer.score(item("FB Lite", tags = "facebook, social"), "face"), 0.0)
        assertEquals(2.5, Scorer.score(item("Messenger", tags = "fb lite"), "fb l"), 0.0)
    }
    @Test fun tagBeatsPackageTokenButNotPrefix() {
        assertEquals(3.0, Scorer.score(item("Facebook", "facebook", "facebook"), "face"), 0.0)
        val tagged = item("Lite", "facebook", "facebook")
        assertEquals(2.5, Scorer.score(tagged, "facebook"), 0.0)
    }
    @Test fun noTagsNoMatch() = assertEquals(0.0, Scorer.score(item("Camera"), "facebook"), 0.0)
    @Test fun withTagsEmptyClears() = assertTrue(item("Camera", tags = "x").withTags("").tagTokens.isEmpty())
}
