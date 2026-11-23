package com.aktivesolutions.jumpstart.edge

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.aktivesolutions.jumpstart.ui.altFrom

/**
 * Opens quick search on Alt+Space from any app. Used only as a key filter (flagRequestFilterKeyEvents); it requests no
 * window content and ignores all accessibility events.
 *
 * Alt+Space rather than Ctrl+Space / Meta+Space: those are Android's input-language switch and would fight the system.
 */
class ShortcutAccessibilityService : AccessibilityService() {
    // True between a consumed Alt+Space DOWN and its UP (incl. auto-repeats), so the app never sees half the press.
    private var swallowSpace = false

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // The process can be started by this service at boot with no activity: make sure the edge bar appears.
        EdgeBarController.refresh()
    }

    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode != KeyEvent.KEYCODE_SPACE) {
            // Any other key press means a Space UP was lost; don't let the flag eat a later, unrelated Space.
            if (e.action == KeyEvent.ACTION_DOWN) swallowSpace = false
            return false
        }
        if (e.action == KeyEvent.ACTION_UP) {
            val s = swallowSpace
            swallowSpace = false
            return s
        }
        if (e.action != KeyEvent.ACTION_DOWN) return false
        if (swallowSpace && e.repeatCount > 0) return true // held: eat the auto-repeats too
        // Left Alt only, no Ctrl/Shift/Meta: right Alt is AltGr on many layouts and must reach the app.
        if (e.repeatCount != 0 || !altFrom(e.metaState) || (e.metaState and MODS) != 0) {
            swallowSpace = false
            return false
        }
        swallowSpace = true
        EdgeBarController.openSearchFromShortcut(this)
        return true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun start(intent: Intent) {
        // The caller's launch intent already carries NEW_TASK; from a service context it is required anyway.
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    companion object {
        // Tuned for the slow SM-T550. The overlay must be gone so the previous app is the top task before we dock it, and
        // the dock animation must finish before the adjacent launch or it lands fullscreen.
        private const val TOGGLE_DELAY_MS = 300L
        private const val LAUNCH_DELAY_MS = 700L

        @Volatile private var instance: ShortcutAccessibilityService? = null

        fun isConnected() = instance != null

        /**
         * Docks the app now in front (toggle split screen), then starts [intent] into the other pane. Returns false if the
         * service isn't connected (caller launches normally). If the front app isn't resizeable, Android shows its own
         * "doesn't support split screen" toast and doesn't dock; the delayed launch then just opens fullscreen, which is
         * acceptable. If the toggle itself is refused we launch plainly straight away.
         */
        fun launchSplit(intent: Intent): Boolean {
            val s = instance ?: return false
            s.handler.postDelayed({
                if (s.performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)) {
                    s.handler.postDelayed({ s.start(intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)) }, LAUNCH_DELAY_MS)
                } else s.start(intent)
            }, TOGGLE_DELAY_MS)
            return true
        }
        private const val MODS = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_META_ON

        /** True when the user has switched the service on in Accessibility settings. */
        fun isEnabled(ctx: Context): Boolean {
            val me = ComponentName(ctx, ShortcutAccessibilityService::class.java)
            val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return list.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
