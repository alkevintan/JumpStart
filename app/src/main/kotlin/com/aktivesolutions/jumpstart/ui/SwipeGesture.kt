package com.aktivesolutions.jumpstart.ui

enum class HomeSwipe { None, Search, Notifications }

/**
 * Pure classification of a finished/ongoing drag on the home screen, kept free of Android types so it is unit-testable.
 * A swipe is a downward drag of at least [thresholdPx] that is mostly vertical (|dy| > 1.5 * |dx|). If it started in the
 * outer 15% of the width (at least [edgeMinPx]) on either side it asks for the notification shade, otherwise search.
 * Upward and horizontal drags are never a swipe, so the system back gesture is left alone.
 */
fun classifySwipe(startX: Float, widthPx: Float, dx: Float, dy: Float, thresholdPx: Float, edgeMinPx: Float): HomeSwipe {
    if (dy < thresholdPx) return HomeSwipe.None
    if (dy <= 1.5f * kotlin.math.abs(dx)) return HomeSwipe.None
    val edge = maxOf(widthPx * 0.15f, edgeMinPx)
    return if (startX <= edge || startX >= widthPx - edge) HomeSwipe.Notifications else HomeSwipe.Search
}
