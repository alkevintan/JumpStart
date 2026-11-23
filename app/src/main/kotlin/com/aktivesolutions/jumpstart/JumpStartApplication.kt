package com.aktivesolutions.jumpstart

import android.app.Application
import com.aktivesolutions.jumpstart.edge.EdgeBarController

/** Owns the process-wide edge-swipe bar, which must outlive any single activity. */
class JumpStartApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Core.init(this) // shared prefs / repositories / app list first: the bar and both VMs use them
        EdgeBarController.init(this)
    }
}
