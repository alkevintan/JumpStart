package com.aktivesolutions.jumpstart.data

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.aktivesolutions.jumpstart.search.Item
import com.aktivesolutions.jumpstart.search.Kind
import com.aktivesolutions.jumpstart.search.Tags
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Launchable apps (CATEGORY_LAUNCHER) plus caches:
 *  - the app list (label, component, keywords) persisted to filesDir/apps.cache so a cold start shows apps
 *    immediately; the real PackageManager scan (loadLabel for every app is the slow part) runs afterwards;
 *  - rendered icons: a byte-bounded in-memory LRU plus PNGs in filesDir/icons keyed by component, size and
 *    the package's lastUpdateTime.
 */
@Stable
class AppRepository(context: Context) {
    private val context = context.applicationContext
    private val pm = this.context.packageManager
    private val filesDir = this.context.filesDir

    // ---- icon memory cache, bounded by bytes ----
    private class CachedIcon(val image: ImageBitmap, val bytes: Int)

    private val icons = object : LruCache<String, CachedIcon>(iconCacheBytes(this.context)) {
        override fun sizeOf(key: String, value: CachedIcon) = value.bytes
    }
    private val updateTimes = ConcurrentHashMap<String, Long>()

    // Hand-picked synonyms for well-known packages (the mockup's per-app keywords).
    private val keywords = mapOf(
        "chrome" to "browser web", "gmail" to "email mail", "maps" to "navigation directions",
        "photos" to "gallery pictures", "calendar" to "events schedule", "clock" to "alarm timer",
        "calculator" to "math", "calc" to "math", "youtube" to "video", "dialer" to "phone call",
        "contacts" to "people", "messaging" to "sms text", "mms" to "sms text", "camera" to "photo",
        "vending" to "play store apps install", "files" to "storage documents", "documentsui" to "files storage documents",
        "settings" to "preferences", "music" to "songs audio", "gallery" to "photos pictures",
        "weather" to "forecast rain", "keep" to "notes memo", "browser" to "web internet",
    )

    /**
     * One Item per launcher *activity*, keyed by its flattened ComponentName. Several packages expose more
     * than one launcher entry (e.g. the Google app's "Google" + "Voice Search"); keying by package hid all
     * but the first. Keying by component also means launch/icon lookups need no shared mutable map.
     * Blocking and slow (a label lookup per app): call off the main thread.
     */
    fun load(): List<Item> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found = pm.queryIntentActivities(intent, 0)
        val out = ArrayList<Item>(found.size)
        val seen = HashSet<String>()
        for (ri in found) {
            val ai = ri.activityInfo ?: continue
            val pkg = ai.packageName
            if (pkg == context.packageName) continue
            val key = ComponentName(pkg, ai.name).flattenToString()
            if (!seen.add(key)) continue
            val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
            val lowerPkg = pkg.lowercase()
            val kw = (keywords.entries.filter { lowerPkg.contains(it.key) }.map { it.value } + Tags.packageTokens(pkg))
                .joinToString(" ")
            out += Item(Kind.APP, key, label, "App", kw)
        }
        return out.sortedBy { it.nameLower }
    }

    // ---- app list cache ----
    private val listCache get() = File(filesDir, "apps.cache")

    /** The list saved by the last [saveList], or null if absent/stale/corrupt. Fast (one small file); call off the main thread. */
    fun readCachedList(): List<Item>? = try {
        val lines = listCache.readLines()
        if (lines.firstOrNull() != cacheHeader()) null
        else lines.drop(1).mapNotNull {
            val p = it.split('\t')
            if (p.size == 3) Item(Kind.APP, p[0], p[1], "App", p[2]) else null
        }.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    fun saveList(list: List<Item>) {
        try {
            val tmp = File(filesDir, "apps.cache.tmp")
            tmp.bufferedWriter().use { w ->
                w.write(cacheHeader()); w.write("\n")
                for (i in list) {
                    w.write("${i.key}\t${i.title.clean()}\t${i.keywords}\n")
                }
            }
            if (!tmp.renameTo(listCache)) tmp.delete()
        } catch (_: Exception) {
        }
    }

    // Labels are locale dependent, so the cache is only valid for the locale (and format) it was written in.
    private fun cacheHeader() = "v2\t${Locale.getDefault().toLanguageTag()}"
    private fun String.clean() = replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    /** [key] is a flattened ComponentName from [load]; a bare package name is also accepted. */
    fun launchIntent(key: String): Intent? {
        val cn = ComponentName.unflattenFromString(key) ?: return pm.getLaunchIntentForPackage(key)
        return Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    // ---- icons ----
    fun cachedIcon(key: String, px: Int): ImageBitmap? = icons.get(IconKeys.memKey(key, px))?.image

    /**
     * Blocking; call off the main thread. Order: memory LRU, disk PNG, render from PackageManager.
     * Bitmaps are rendered at [px] so memory stays small.
     */
    fun loadIcon(key: String, px: Int): ImageBitmap? {
        val cacheKey = IconKeys.memKey(key, px)
        icons.get(cacheKey)?.let { return it.image }
        val cn = ComponentName.unflattenFromString(key) ?: return null
        val disk = diskFile(cn, key, px)
        if (disk != null && disk.exists()) {
            try {
                BitmapFactory.decodeFile(disk.path)?.let { b ->
                    val ib = b.asImageBitmap()
                    icons.put(cacheKey, CachedIcon(ib, b.allocationByteCount))
                    return ib
                }
            } catch (_: Exception) {
            }
            disk.delete()
        }
        val d: Drawable = try {
            pm.getActivityIcon(cn)
        } catch (_: Exception) {
            return null
        }
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        // PackageManager may hand out a shared Drawable; setBounds+draw from two IO threads would race.
        synchronized(drawLock) {
            d.setBounds(0, 0, px, px)
            d.draw(Canvas(bmp))
        }
        val ib = bmp.asImageBitmap()
        icons.put(cacheKey, CachedIcon(ib, bmp.allocationByteCount))
        if (disk != null) writeDisk(disk, bmp)
        return ib
    }

    /** Warms memory (and disk) with the icons at each of [pxSizes]; call off the main thread. */
    fun preload(keys: List<String>, pxSizes: List<Int>) {
        for (k in keys) for (px in pxSizes) loadIcon(k, px)
    }

    private val drawLock = Any()

    private fun iconDir() = File(filesDir, IconKeys.DIR)

    /** icons2/<sha1(full component)>_<px>_<lastUpdateTime>.png, or null if the package can't be resolved. */
    private fun diskFile(cn: ComponentName, key: String, px: Int): File? {
        val lut = updateTimes[cn.packageName] ?: try {
            pm.getPackageInfo(cn.packageName, 0).lastUpdateTime.also { updateTimes[cn.packageName] = it }
        } catch (_: Exception) {
            return null
        }
        return File(iconDir(), IconKeys.diskName(key, px, lut))
    }

    private fun writeDisk(f: File, bmp: Bitmap) {
        try {
            val dir = iconDir().apply { mkdirs() }
            val prefix = f.name.substringBeforeLast('_') + "_" // same component + size, older lastUpdateTime (hash is fixed-width)
            dir.listFiles()?.forEach { if (it.name.startsWith(prefix) && it != f) it.delete() }
            val tmp = File(dir, f.name + ".tmp")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!tmp.renameTo(f)) tmp.delete()
        } catch (_: Exception) {
        }
    }

    /** Drops in-memory icons (an app was updated / replaced). The disk cache self-invalidates via lastUpdateTime. */
    fun invalidateIcons() {
        icons.evictAll()
        updateTimes.clear()
    }

    /** Deletes cached PNGs of apps no longer in [keys]. */
    fun pruneIconDisk(keys: Collection<String>) {
        try {
            File(filesDir, IconKeys.LEGACY_DIR).takeIf { it.exists() }?.deleteRecursively() // pre-1.0.3 key format
            val live = keys.mapTo(HashSet()) { IconKeys.hash(it) }
            iconDir().listFiles()?.forEach { if (it.name.substringBefore('_') !in live) it.delete() }
        } catch (_: Exception) {
        }
    }

    private companion object {
        /** Bytes for the in-memory icon LRU: ~1/12 of the heap limit (1/32 on low-RAM devices), 2..12 MB. */
        fun iconCacheBytes(ctx: Context): Int {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val low = am.isLowRamDevice || am.memoryClass <= 128
            val heapBytes = am.memoryClass.toLong() * 1024 * 1024
            return (heapBytes / (if (low) 32 else 12)).coerceIn(2L * 1024 * 1024, 12L * 1024 * 1024).toInt()
        }
    }
}
