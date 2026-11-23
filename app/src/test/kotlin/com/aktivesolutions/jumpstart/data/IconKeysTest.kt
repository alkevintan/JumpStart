package com.aktivesolutions.jumpstart.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconKeysTest {
    private val a = "com.google.android.googlequicksearchbox/com.google.android.googlequicksearchbox.SearchActivity"
    private val b = "com.google.android.googlequicksearchbox/com.google.android.voicesearch.VoiceSearchActivity"

    @Test fun sameAppDifferentActivityDiffers() {
        assertNotEquals(IconKeys.hash(a), IconKeys.hash(b))
        assertNotEquals(IconKeys.memKey(a, 100), IconKeys.memKey(b, 100))
        assertNotEquals(IconKeys.diskName(a, 100, 5), IconKeys.diskName(b, 100, 5))
    }
    @Test fun fullHashDeterministicFortyHex() {
        assertEquals(IconKeys.hash(a), IconKeys.hash(a))
        assertEquals(40, IconKeys.hash(a).length)
        assertTrue(IconKeys.hash(a).all { it in "0123456789abcdef" })
    }
    @Test fun sizeAndUpdateTimeInName() {
        assertNotEquals(IconKeys.diskName(a, 100, 5), IconKeys.diskName(a, 150, 5))
        assertNotEquals(IconKeys.diskName(a, 100, 5), IconKeys.diskName(a, 100, 6))
        assertTrue(IconKeys.diskName(a, 100, 5).startsWith(IconKeys.diskPrefix(a, 100)))
        assertTrue(!IconKeys.diskName(a, 100, 5).startsWith(IconKeys.diskPrefix(a, 10)))
    }
    @Test fun dirBumped() = assertNotEquals(IconKeys.LEGACY_DIR, IconKeys.DIR)
}
