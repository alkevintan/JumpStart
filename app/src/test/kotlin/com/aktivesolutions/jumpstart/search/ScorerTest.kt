package com.aktivesolutions.jumpstart.search

import org.junit.Assert.assertEquals
import org.junit.Test

class ScorerTest {
    @Test fun prefix() = assertEquals(3.0, Scorer.score("Messages", "sms", "mes"), 0.0)
    @Test fun wordPrefix() {
        assertEquals(2.5, Scorer.score("Play Store", "", "sto"), 0.0)
        assertEquals(2.5, Scorer.score("Display & brightness", "screen", "bri"), 0.0)
        assertEquals(2.5, Scorer.score("Joy Dela-Cruz", "", "cruz"), 0.0)
    }
    @Test fun substring() = assertEquals(2.0, Scorer.score("Spotify", "music", "tif"), 0.0)
    @Test fun keywordPrefix() = assertEquals(1.0, Scorer.score("Wi-Fi", "network internet", "inter"), 0.0)
    @Test fun keywordSubstringDoesNotCount() = assertEquals(0.0, Scorer.score("Wi-Fi", "network internet", "ternet"), 0.0)
    @Test fun none() = assertEquals(0.0, Scorer.score("Camera", "photo", "zzz"), 0.0)
    @Test fun caseInsensitiveName() = assertEquals(3.0, Scorer.score("CHROME", null, "chr"), 0.0)

    @Test fun engineRanksAndExcludesTop() {
        val apps = listOf(
            Item(Kind.APP, "a", "Maps", "App"),
            Item(Kind.APP, "b", "Gmail", "App", "mail"),
            Item(Kind.APP, "c", "Mail Merge", "App"),
        )
        val r = SearchEngine.search("mail", apps, emptyList(), emptyList())
        assertEquals("Mail Merge", r.top!!.title) // prefix 3 beats substring 2
        assertEquals(listOf("Gmail"), r.groups.single().items.map { it.title })
        assertEquals(true, r.showWeb)
    }
    @Test fun engineAnswerSuppressesTopAndWeb() {
        val r = SearchEngine.search("2+2", listOf(Item(Kind.APP, "a", "2+2 app", "App")), emptyList(), emptyList())
        assertEquals("4", r.answer!!.value)
        assertEquals(null, r.top)
        assertEquals(false, r.showWeb)
    }
    @Test fun engineCaps() {
        val apps = (1..8).map { Item(Kind.APP, "p$it", "App$it", "App") }
        val r = SearchEngine.search("app", apps, emptyList(), emptyList())
        assertEquals(4, r.groups.single().items.size) // 8 matches - top hit, capped at 4
    }

    @Test fun indexedScoreMatchesRawScore() {
        val cases = listOf(
            Triple("Messages", "sms text", "mes"), Triple("Play Store", "", "sto"), Triple("Display & brightness", "screen", "bri"),
            Triple("Spotify", "music", "tif"), Triple("Wi-Fi", "network internet", "inter"), Triple("Camera", "photo", "zzz"),
            Triple("Joy Dela-Cruz", "", "cruz"),
        )
        for ((n, k, q) in cases) {
            val item = Item(Kind.APP, "x", n, "App", k)
            assertEquals(Scorer.score(n, k, q), Scorer.score(item, q), 0.0)
        }
    }
    @Test fun itemPrecomputesTokens() {
        val i = Item(Kind.APP, "x", "Play Store", "App", "play store apps")
        assertEquals("play store", i.nameLower)
        assertEquals(listOf("play", "store"), i.nameWords)
        assertEquals(listOf("play", "store", "apps"), i.keywordTokens)
    }
    @Test fun converterSkipsPlainWords() {
        assertEquals(null, Converter.answerFor("camera"))
        assertEquals("4", Converter.answerFor("2+2")!!.value)
    }
}
