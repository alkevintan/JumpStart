package com.aktivesolutions.jumpstart.edge

enum class Side {
    LEFT, RIGHT;

    companion object {
        /** Pref value -> side; anything unknown falls back to the default (Right). */
        fun fromPref(v: String?): Side = if (v == "Left") LEFT else RIGHT
    }
}

/** Pure classification of the edge-bar drag, free of Android types so it is unit-testable (like ui/SwipeGesture.kt). */
object EdgeSwipe {
    /**
     * True when the drag from the touch-down point is inward (right for a LEFT bar, left for a RIGHT bar), at least
     * [thresholdPx] long and mostly horizontal, so a vertical scroll that grazes the bar never opens search.
     */
    fun isOpenSwipe(dx: Float, dy: Float, side: Side, thresholdPx: Float): Boolean {
        val inward = if (side == Side.LEFT) dx else -dx
        return inward >= thresholdPx && kotlin.math.abs(dx) > kotlin.math.abs(dy)
    }
}
