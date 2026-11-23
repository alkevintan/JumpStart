package com.aktivesolutions.jumpstart.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** The APK asset of the latest GitHub Release, plus the human-facing bits shown in the prompt. */
data class Release(val version: String, val notes: String, val apkUrl: String, val pageUrl: String)

sealed interface CheckResult {
    data class New(val release: Release) : CheckResult
    data object UpToDate : CheckResult
    data class Error(val message: String) : CheckResult
}

/** UI-facing state for both the Settings card and the automatic update prompt. */
data class UpdateState(
    val checking: Boolean = false,
    val available: Release? = null,
    val upToDate: Boolean = false,
    val error: String? = null,
    val downloading: Boolean = false,
    val progress: Int = -1,
    /** Set only by the launch-time check; the manual check just updates the Settings card. */
    val prompt: Boolean = false,
)

/** GitHub Releases is the update feed: CI attaches the signed APK to every version tag. */
object Updater {
    private const val API = "https://api.github.com/repos/alkevintan/JumpStart/releases/latest"
    private const val USER_AGENT = "JumpStart-Android"

    /** Survives the trip to Settings > Install unknown apps so the download doesn't have to be repeated. */
    @Volatile private var pending: File? = null

    fun installedVersion(ctx: Context): String = runCatching {
        @Suppress("DEPRECATION")
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
    }.getOrDefault("0")

    /** Numeric dotted-version comparison ("v1.2" > "1.10" is false). Non-numeric parts are ignored. */
    fun isNewer(latest: String, current: String): Boolean {
        val a = numbers(latest)
        val b = numbers(current)
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numbers(v: String): List<Int> {
        val s = v.trim()
        return (if (s.startsWith("v", ignoreCase = true)) s.drop(1) else s)
            .split(".").mapNotNull { it.toIntOrNull() }
    }

    suspend fun check(current: String): CheckResult = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            when (val code = conn.responseCode) {
                200 -> Unit
                404 -> return@withContext CheckResult.Error("no releases yet")
                else -> return@withContext CheckResult.Error("GitHub returned $code")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val version = json.optString("tag_name").removePrefix("v")
            val apkUrl = json.optJSONArray("assets")?.let { assets ->
                (0 until assets.length()).asSequence()
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk") }
                    ?.optString("browser_download_url")
            }
            if (version.isEmpty() || apkUrl.isNullOrEmpty())
                return@withContext CheckResult.Error("latest release has no APK")
            val release = Release(version, json.optString("body").take(400), apkUrl, json.optString("html_url"))
            if (isNewer(version, current)) CheckResult.New(release) else CheckResult.UpToDate
        } catch (e: Exception) {
            CheckResult.Error(e.message?.take(80) ?: "network unavailable")
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun download(ctx: Context, url: String, onProgress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            if (conn.responseCode != 200) return@withContext null
            val dir = File(ctx.cacheDir, "update").apply { mkdirs() }
            val file = File(dir, "jumpstart-update.apk")
            if (file.exists()) file.delete()
            val total = conn.contentLength
            conn.inputStream.use { input ->
                FileOutputStream(file).use { out ->
                    val buf = ByteArray(16 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        done += read
                        if (total > 0) onProgress((done * 100 / total).coerceAtMost(100).toInt())
                    }
                }
            }
            file.takeIf { it.length() > 0 }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Returns false when the user must first grant "install unknown apps"; [resumePending] finishes that case. */
    fun install(ctx: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            pending = file
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${ctx.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ctx.startActivity(settings) }
            return false
        }
        pending = null
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updatefileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { ctx.startActivity(intent) }
        return true
    }

    /** Called on every resume; if the install permission was just granted, hand the APK to the installer. */
    fun resumePending(ctx: Context) {
        val file = pending ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) return
        install(ctx, file)
    }
}
