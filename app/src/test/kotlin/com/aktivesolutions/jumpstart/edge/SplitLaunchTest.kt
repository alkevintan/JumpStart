package com.aktivesolutions.jumpstart.edge

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitLaunchTest {
    private fun m(pref: Boolean, sup: Boolean, split: Boolean, svc: Boolean) = splitMode(pref, sup, split, svc)

    @Test fun prefOffIsPlain() = assertEquals(SplitMode.PLAIN, m(false, true, true, true))
    @Test fun unsupportedIsPlain() = assertEquals(SplitMode.PLAIN, m(true, false, true, true))
    @Test fun alreadySplitIsAdjacent() = assertEquals(SplitMode.ADJACENT, m(true, true, true, false))
    @Test fun alreadySplitWithServiceIsAdjacent() = assertEquals(SplitMode.ADJACENT, m(true, true, true, true))
    @Test fun fullscreenWithServiceToggles() = assertEquals(SplitMode.TOGGLE_THEN_ADJACENT, m(true, true, false, true))
    @Test fun fullscreenNoServiceIsPlain() = assertEquals(SplitMode.PLAIN, m(true, true, false, false))
}
