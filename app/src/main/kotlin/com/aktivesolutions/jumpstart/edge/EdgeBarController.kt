package com.aktivesolutions.jumpstart.edge

import android.app.Activity
import android.app.ActivityOptions
import android.app.Application
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.aktivesolutions.jumpstart.MainActivity
import com.aktivesolutions.jumpstart.SearchOverlayActivity
import com.aktivesolutions.jumpstart.Core
import com.aktivesolutions.jumpstart.data.Prefs

/**
 * A transparent touch strip on one screen edge, shown only while no JumpStart activity is started (i.e. the user is in
 * another app). Swiping inward opens [SearchOverlayActivity] on top of that app.
 *
 * Trade-off: this is a plain overlay window owned by the process, deliberately NOT a foreground service, so there is no
 * persistent notification. A process showing overlay UI gets perceptible priority, but if the system still kills it the
 * bar is gone until the launcher next starts (pressing Home) and [refresh] runs again.
 *
 * Everything here runs on the main thread.
 */
object EdgeBarController {
    private lateinit var app: Application
    private lateinit var prefs: Prefs
    private var started = 0
    private var overlayStarted = 0
    private var bar: View? = null
    private var barSide: Side? = null

    fun init(application: Application) {
        app = application
        prefs = Core.prefs
        // A keyboard attaching/detaching is a configuration change: re-evaluate (the bar hides while one is connected).
        application.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = refresh()
            override fun onLowMemory() {}
        })
        // Not refreshed here: when an activity starts the process, showing now would flash the bar over the launcher
        // while it starts (its lifecycle callbacks refresh). When the accessibility service starts the process at boot
        // with no activity, the service calls refresh() from onServiceConnected.
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                started++
                if (activity is SearchOverlayActivity) overlayStarted++
                refresh()
            }
            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (activity is SearchOverlayActivity) overlayStarted = (overlayStarted - 1).coerceAtLeast(0)
                // Rotation / keyboard attach recreates the activity: its onStart comes right back, so don't flicker the bar.
                if (!activity.isChangingConfigurations) refresh()
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /** Re-evaluates visibility and side; call after the pref, the permission or the started count changes. */
    fun refresh() {
        if (!::app.isInitialized) return
        val side = Side.fromPref(prefs.edgeSide)
        val want = prefs.edgeBar && Settings.canDrawOverlays(app) && started == 0 &&
            !hardKeyboardAttached(app.resources.configuration)
        if (!want) { remove(); return }
        if (bar?.isAttachedToWindow == true && barSide == side) return // else a stale reference: drop and re-add
        remove() // side changed: gravity is fixed at add time, so re-add
        add(side)
    }

    private fun add(side: Side) {
        val d = app.resources.displayMetrics.density
        val w = (14 * d).toInt()
        // <= 200dp: Android caps system-gesture exclusion at 200dp of height per edge.
        val h = (180 * d).toInt()
        val view = EdgeView(app, side, 24 * d)
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            w, h, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = (if (side == Side.LEFT) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL }
        if (Build.VERSION.SDK_INT >= 29) {
            // Best-effort: ask gesture navigation not to take the back swipe from this strip. The system may still
            // ignore it (e.g. OEMs, or when the app isn't foreground-ish).
            view.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
                v.systemGestureExclusionRects = listOf(Rect(0, 0, r - l, b - t))
            }
        }
        // runCatching: the permission can be revoked between the check and here (BadTokenException / SecurityException).
        if (runCatching { (app.getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(view, lp) }.isSuccess) {
            bar = view
            barSide = side
        }
    }

    private fun remove() {
        val v = bar ?: return
        bar = null
        barSide = null
        runCatching { (app.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) }
    }

    /** Alt+Space: search over the current app, or on the launcher if it is the one in front; no-op if already open. */
    fun openSearchFromShortcut(ctx: Context) {
        when {
            overlayStarted > 0 -> {}
            started > 0 -> runCatching {
                // MainActivity is singleTask: this re-delivers to it (onNewIntent) rather than stacking a copy.
                ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_OPEN_SEARCH, true))
            }
            else -> openSearch(ctx)
        }
    }

    private fun openSearch(ctx: Context) {
        // Apps holding SYSTEM_ALERT_WINDOW are exempt from the background-activity-start restrictions, so this works
        // from the overlay window even though no JumpStart activity is visible.
        val i = Intent(ctx, SearchOverlayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opts = ActivityOptions.makeCustomAnimation(ctx, android.R.anim.fade_in, android.R.anim.fade_out).toBundle()
        runCatching { ctx.startActivity(i, opts) }
    }

    private class EdgeView(ctx: Context, private val side: Side, private val thresholdPx: Float) : View(ctx) {
        private var downX = 0f
        private var downY = 0f
        private var fired = false

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; fired = false }
                MotionEvent.ACTION_MOVE -> if (!fired && EdgeSwipe.isOpenSwipe(e.rawX - downX, e.rawY - downY, side, thresholdPx)) {
                    fired = true // once per gesture
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    openSearch(context)
                }
            }
            return true
        }
    }
}
