package com.aktivesolutions.jumpstart.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LruMapTest {
    @Test fun evictsLeastRecentlyUsed() {
        val m = LruMap<String, Int>(2)
        m.put("a", 1); m.put("b", 2)
        m.get("a") // a is now most recent
        m.put("c", 3)
        assertNull(m.get("b"))
        assertEquals(1, m.get("a"))
        assertEquals(3, m.get("c"))
        assertEquals(2, m.size())
    }
    @Test fun negativeResultsAreCacheable() {
        val m = LruMap<String, Cached<String>>(4)
        m.put("zzz", Cached(null))
        assertNotNull(m.get("zzz"))   // a hit...
        assertNull(m.get("zzz")!!.value) // ...that records "no such word"
        assertNull(m.get("other"))    // genuine miss
    }
}
