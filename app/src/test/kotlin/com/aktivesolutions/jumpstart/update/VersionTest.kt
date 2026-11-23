package com.aktivesolutions.jumpstart.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test
    fun newer_release_is_detected() {
        assertTrue(Updater.isNewer("1.1.0", "1.0.9"))
        assertTrue(Updater.isNewer("v1.0.10", "v1.0.9"))
        assertTrue(Updater.isNewer("2.0", "1.99.99"))
    }

    @Test
    fun equal_or_older_is_not_an_update() {
        assertFalse(Updater.isNewer("1.0.9", "1.0.9"))
        assertFalse(Updater.isNewer("v1.0.8", "1.0.9"))
        assertFalse(Updater.isNewer("junk", "1.0.9"))
    }
}
