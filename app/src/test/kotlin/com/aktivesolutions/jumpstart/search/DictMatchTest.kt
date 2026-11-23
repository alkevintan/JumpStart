package com.aktivesolutions.jumpstart.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DictMatchTest {
    private val words = sortedMapOf("cat" to "feline", "catalog" to "list", "mango" to "fruit", "mangrove" to "tree")

    private val src = object : DictSource {
        fun e(w: String) = DictEntry(w, null, listOf(Sense("noun", words.getValue(w))))
        override fun exact(word: String) = if (words.containsKey(word)) e(word) else null
        override fun prefix(prefix: String) = words.keys.filter { it.startsWith(prefix) }.minByOrNull { it.length }?.let(::e)
    }

    @Test fun exactWins() = assertEquals("cat", dictFor("cat", src)!!.word)
    @Test fun noPrefixUnderFourChars() = assertNull(dictFor("mag", src))
    @Test fun prefixFromFourChars() = assertEquals("catalog", dictFor("cata", src)!!.word)
    @Test fun exactBeatsPrefix() = assertEquals("mango", dictFor("mango", src)!!.word)
    @Test fun tooShort() = assertNull(dictFor("ca", src))
    @Test fun nonAlphabetic() {
        assertNull(dictFor("cat1", src))
        assertNull(dictFor("two words", src))
        assertNull(dictFor("Cat", src)) // caller lowercases; uppercase is rejected by the rule
        assertNull(dictFor("", src))
    }
    @Test fun noMatch() = assertNull(dictFor("zebra", src))
}

class PrefixBoundTest {
    @Test fun incrementsLastChar() {
        assertEquals("manh", prefixUpperBound("mang"))
        assertEquals("catb", prefixUpperBound("cata"))
        assertEquals("cb", prefixUpperBound("ca" + '￿'))
    }
    @Test fun rangeCoversExactlyThePrefix() {
        val words = sortedSetOf("mang", "mango", "mangrove", "mangz", "manh", "mani", "man", "mana")
        val lo = "mang"
        val hi = prefixUpperBound(lo)
        assertEquals(words.filter { it.startsWith(lo) }, words.filter { it >= lo && it < hi })
    }
    @Test fun regexGate() {
        assertEquals(true, isDictQuery("abc"))
        assertEquals(false, isDictQuery("ab"))
        assertEquals(false, isDictQuery("ab1"))
    }
}
