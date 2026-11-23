package com.aktivesolutions.jumpstart.data

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.RecognizerIntent
import android.app.SearchManager

/** All outbound intents in one place. Every call is guarded; returns false if nothing could handle it. */
class ActionLauncher(private val context: Context, private val apps: AppRepository) {

    private fun start(i: Intent): Boolean = try {
        if (context !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun view(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))

    /** System "App info" page for [pkg]. */
    fun appInfo(pkg: String): Boolean = start(
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", pkg, null)),
    )

    /** "Display over other apps" page for this app, falling back to its App info if that screen is missing. */
    fun openOverlaySettings(): Boolean =
        start(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))) ||
            appInfo(context.packageName)

    fun openAccessibilitySettings(): Boolean = start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    /** [adjacent]: ask for the app to open beside the current one (split screen) instead of replacing it. */
    fun openApp(key: String, adjacent: Boolean = false): Boolean =
        apps.launchIntent(key)?.let { if (adjacent) it.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK); start(it) } ?: false

    fun call(number: String) = start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
    fun message(number: String) = start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))))

    fun webSearch(q: String) =
        start(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q)) ||
            start(view("https://www.google.com/search?q=" + Uri.encode(q)))

    fun playStore(q: String) =
        start(view("market://search?q=" + Uri.encode(q))) ||
            start(view("https://play.google.com/store/search?q=" + Uri.encode(q)))

    fun maps(q: String) = start(view("geo:0,0?q=" + Uri.encode(q)))
    fun wikipedia(q: String) = start(view("https://en.wikipedia.org/wiki/Special:Search?search=" + Uri.encode(q)))

    /** Opens the device calculator; falls back to any installed app whose package mentions "calc". */
    fun calculator(allApps: List<com.aktivesolutions.jumpstart.search.Item>): Boolean {
        val sel = try {
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
        } catch (_: Exception) {
            null
        }
        if (sel != null && start(sel)) return true
        val calc = allApps.firstOrNull { it.key.contains("calc", true) || it.title.contains("calc", true) }
        return calc != null && openApp(calc.key)
    }

    /** Pulls down the notification shade via the framework StatusBarManager (needs EXPAND_STATUS_BAR); false if unavailable. */
    fun expandNotifications(): Boolean = expandNotifications(context)

    fun voiceIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

    fun canVoice(): Boolean = voiceIntent().resolveActivity(context.packageManager) != null

    fun openSetting(id: String): Boolean {
        val tries: List<Intent> = when (id) {
            "wifi" -> listOf(Intent(Settings.ACTION_WIFI_SETTINGS))
            "bluetooth" -> listOf(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            "display", "dark" -> listOf(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            "battery" -> listOf(
                Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
                Intent("android.intent.action.POWER_USAGE_SUMMARY"),
            )
            "notifications" -> listOf(
                // System-wide notification settings first; the app-specific screen is only a fallback.
                Intent("android.settings.NOTIFICATION_SETTINGS"),
                Intent("android.settings.APP_NOTIFICATION_SETTINGS")
                    .putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
                    .putExtra("app_package", context.packageName)
                    .putExtra("app_uid", context.applicationInfo.uid),
            )
            "wallpaper" -> listOf(Intent(Intent.ACTION_SET_WALLPAPER))
            "home" -> listOf(Intent(Settings.ACTION_HOME_SETTINGS))
            "sound" -> listOf(Intent(Settings.ACTION_SOUND_SETTINGS))
            "location" -> listOf(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            "apps" -> listOf(Intent(Settings.ACTION_APPLICATION_SETTINGS))
            "storage" -> listOf(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), Intent(Settings.ACTION_MEMORY_CARD_SETTINGS))
            "date" -> listOf(Intent(Settings.ACTION_DATE_SETTINGS))
            "accessibility" -> listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            "about" -> listOf(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
            else -> emptyList()
        }
        return tries.any { start(it) } || start(Intent(Settings.ACTION_SETTINGS))
    }

    /** Intent that asks to become the default home app, or null if the caller should use [openSetting]("home"). */
    fun homeRoleIntent(): Intent? {
        if (Build.VERSION.SDK_INT < 29) return null
        val rm = context.getSystemService(RoleManager::class.java) ?: return null
        if (!rm.isRoleAvailable(RoleManager.ROLE_HOME) || rm.isRoleHeld(RoleManager.ROLE_HOME)) return null
        return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }
}

/** Reflective call: expandNotificationsPanel is a hidden-but-stable framework method. Never throws. */
fun expandNotifications(context: Context): Boolean = try {
    val sbm = context.getSystemService("statusbar")
    if (sbm == null) false else {
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbm)
        true
    }
} catch (_: Throwable) {
    false
}

/**
 * Best-effort "does this device do split screen": API 24+ and the framework's own config bool. Falls back to true when
 * neither bool resolves (they are not public API). Non-resizeable apps simply open fullscreen.
 */
fun splitScreenSupported(): Boolean {
    if (Build.VERSION.SDK_INT < 24) return false
    val r = android.content.res.Resources.getSystem()
    for (name in listOf("config_supportsSplitScreenMultiWindow", "config_supportsMultiWindow")) {
        val id = r.getIdentifier(name, "bool", "android")
        if (id != 0) return r.getBoolean(id)
    }
    return true
}
