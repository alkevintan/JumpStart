package com.aktivesolutions.jumpstart.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeGestureTest {
    private val w = 1000f
    private fun c(x: Float, dx: Float, dy: Float, edgeMin: Float = 32f) = classifySwipe(x, w, dx, dy, 100f, edgeMin)

    @Test fun centerDownIsSearch() = assertEquals(HomeSwipe.Search, c(500f, 10f, 200f))
    @Test fun leftEdgeDownIsNotifications() = assertEquals(HomeSwipe.Notifications, c(20f, 5f, 200f))
    @Test fun rightEdgeDownIsNotifications() = assertEquals(HomeSwipe.Notifications, c(980f, -5f, 200f))
    @Test fun upIsNone() = assertEquals(HomeSwipe.None, c(500f, 0f, -300f))
    @Test fun upFromEdgeIsNone() = assertEquals(HomeSwipe.None, c(10f, 0f, -300f))
    @Test fun horizontalIsNone() = assertEquals(HomeSwipe.None, c(10f, 300f, 20f))
    @Test fun diagonalIsNone() = assertEquals(HomeSwipe.None, c(500f, 150f, 200f))
    @Test fun shortIsNone() = assertEquals(HomeSwipe.None, c(500f, 0f, 50f))
    @Test fun edgeBoundary() {
        assertEquals(HomeSwipe.Notifications, c(150f, 0f, 200f))
        assertEquals(HomeSwipe.Search, c(151f, 0f, 200f))
        assertEquals(HomeSwipe.Notifications, c(850f, 0f, 200f))
        assertEquals(HomeSwipe.Search, c(849f, 0f, 200f))
    }
    @Test fun minEdgeAppliesOnNarrowScreens() =
        assertEquals(HomeSwipe.Notifications, classifySwipe(30f, 100f, 0f, 200f, 100f, 32f))
}
