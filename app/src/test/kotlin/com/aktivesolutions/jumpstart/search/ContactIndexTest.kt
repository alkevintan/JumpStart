package com.aktivesolutions.jumpstart.search

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactIndexTest {
    private val idx = ContactIndex.build(
        listOf(
            ContactRow("zoe Adams", "300", false),
            ContactRow("Joy Dela-Cruz", "100", false),
            ContactRow("Joy Dela-Cruz", "101", true),   // primary wins for the same name
            ContactRow("alan Joyce", "200", false),
            ContactRow("Bob", "400", false),
            ContactRow("Bob", "401", false),             // first row wins when none is primary
        ),
    )

    @Test fun sortedCaseInsensitivelyAndDeduplicated() =
        assertEquals(listOf("alan Joyce", "Bob", "Joy Dela-Cruz", "zoe Adams"), idx.items.map { it.title })

    @Test fun primaryNumberPreferred() = assertEquals("101", idx.search("dela").single().key)
    @Test fun firstNumberWhenNoPrimary() = assertEquals("400", idx.search("bob").single().key)
    @Test fun caseInsensitiveSubstring() = assertEquals(listOf("alan Joyce", "Joy Dela-Cruz"), idx.search("joy").map { it.title })
    @Test fun noMatchAndEmptyQuery() {
        assertEquals(emptyList<Item>(), idx.search("xyz"))
        assertEquals(emptyList<Item>(), idx.search(""))
    }
    @Test fun capsAtLimitInNameOrder() {
        val rows = (1..40).map { ContactRow("Contact %02d".format(it), "$it", false) }
        val r = ContactIndex.build(rows).search("contact")
        assertEquals(25, r.size)
        assertEquals("Contact 01", r.first().title)
    }
    @Test fun itemsAreContactKindWithNumberSubtitle() {
        val it = idx.search("bob").single()
        assertEquals(Kind.CONTACT, it.kind)
        assertEquals("400", it.subtitle)
    }
    @Test fun scorerRanksIndexedContacts() {
        val r = SearchEngine.search("joy", emptyList(), idx.search("joy"), emptyList())
        assertEquals("Joy Dela-Cruz", r.top!!.title) // prefix (3) beats substring (2)
    }
}
