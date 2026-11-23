package com.aktivesolutions.jumpstart.edge

enum class SplitMode {
    /** Normal launch. */
    PLAIN,
    /** Split is already active: just ask for the other pane (LAUNCH_ADJACENT). */
    ADJACENT,
    /** Fullscreen: dock the previous app via the accessibility service, then launch adjacent. */
    TOGGLE_THEN_ADJACENT,
}

/**
 * Pure decision for launching an app from the quick-search overlay. FLAG_ACTIVITY_LAUNCH_ADJACENT is ignored on API 24-30
 * unless a docked split already exists, so from a fullscreen app we need the accessibility service to create one first.
 */
fun splitMode(prefOn: Boolean, supported: Boolean, alreadySplit: Boolean, serviceConnected: Boolean): SplitMode = when {
    !prefOn || !supported -> SplitMode.PLAIN
    alreadySplit -> SplitMode.ADJACENT
    serviceConnected -> SplitMode.TOGGLE_THEN_ADJACENT
    else -> SplitMode.PLAIN
}
