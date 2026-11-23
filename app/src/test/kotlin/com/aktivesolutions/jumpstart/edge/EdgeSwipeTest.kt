package com.aktivesolutions.jumpstart.edge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeSwipeTest {
    private fun s(dx: Float, dy: Float, side: Side) = EdgeSwipe.isOpenSwipe(dx, dy, side, 60f)

    @Test fun leftBarInwardIsOpen() = assertTrue(s(80f, 10f, Side.LEFT))
    @Test fun rightBarInwardIsOpen() = assertTrue(s(-80f, -10f, Side.RIGHT))
    @Test fun leftBarOutwardIsNot() = assertFalse(s(-80f, 0f, Side.LEFT))
    @Test fun rightBarOutwardIsNot() = assertFalse(s(80f, 0f, Side.RIGHT))
    @Test fun belowThresholdIsNot() = assertFalse(s(59f, 0f, Side.LEFT))
    @Test fun exactThresholdIsOpen() = assertTrue(s(60f, 0f, Side.LEFT))
    @Test fun verticalDominantIsNot() = assertFalse(s(80f, 120f, Side.LEFT))
    @Test fun equalAxesIsNot() = assertFalse(s(80f, 80f, Side.LEFT))
    @Test fun sideFromPref() {
        assertEquals(Side.LEFT, Side.fromPref("Left"))
        assertEquals(Side.RIGHT, Side.fromPref("Right"))
        assertEquals(Side.RIGHT, Side.fromPref(null))
        assertEquals(Side.RIGHT, Side.fromPref("junk"))
    }
}
