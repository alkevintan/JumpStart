package com.aktivesolutions.jumpstart.data

import com.aktivesolutions.jumpstart.search.Item
import com.aktivesolutions.jumpstart.search.Kind

/** Static list of device Settings shortcuts (id -> intents live in ActionLauncher). */
object SettingsCatalog {
    private fun s(id: String, title: String, kw: String) = Item(Kind.SETTING, id, title, "Settings", kw)

    val items: List<Item> = listOf(
        s("wifi", "Wi-Fi", "network internet wireless"),
        s("bluetooth", "Bluetooth", "pair devices"),
        s("display", "Display & brightness", "screen"),
        s("battery", "Battery", "power saver"),
        s("dark", "Dark theme", "night mode display"),
        s("notifications", "Notifications", "alerts"),
        s("wallpaper", "Wallpaper", "background"),
        s("home", "Default home app", "launcher"),
        s("sound", "Sound", "volume ringtone vibration"),
        s("location", "Location", "gps"),
        s("apps", "Apps", "applications manage"),
        s("storage", "Storage", "space memory"),
        s("date", "Date & time", "clock timezone"),
        s("accessibility", "Accessibility", "talkback font"),
        s("about", "About phone", "device info version"),
    )
}
