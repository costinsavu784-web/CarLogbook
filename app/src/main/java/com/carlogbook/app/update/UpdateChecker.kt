package com.carlogbook.app.update

import android.content.Context
import android.content.pm.PackageManager
import com.carlogbook.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A newer build published on GitHub Releases. */
data class AvailableUpdate(
    val versionName: String,
    val versionCode: Int,
    val notes: String,
    val apkUrl: String,
    val apkSizeBytes: Long
)

/**
 * Looks up the latest GitHub Release and decides whether it is newer than the installed app.
 *
 * Convention: release tags are "v<major>.<minor>[.<patch>]" and the APK asset ends in ".apk".
 * The version is compared numerically, so "v1.10" is newer than "v1.9".
 */
object UpdateChecker {

    private const val PREFS = "updates"
    private const val KEY_LAST_CHECK_DAY = "lastCheckDay"
    private const val USER_AGENT = "CarLogbook-Android"

    private val latestReleaseUrl =
        "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"

    /** Returns true if no automatic check has run today. */
    fun shouldAutoCheck(context: Context): Boolean {
        val today = java.time.LocalDate.now().toEpochDay()
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_CHECK_DAY, -1)
        return last != today
    }

    fun markChecked(context: Context) {
        val today = java.time.LocalDate.now().toEpochDay()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_LAST_CHECK_DAY, today).apply()
    }

    /**
     * Returns the update if one is available, null if the app is current.
     * Throws on network or parsing errors so the caller can show a message.
     */
    suspend fun check(context: Context): AvailableUpdate? = withContext(Dispatchers.IO) {
        val json = fetch(latestReleaseUrl)
        val release = JSONObject(json)
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@withContext null

        val tag = release.getString("tag_name")
        val remote = parseVersion(tag) ?: return@withContext null
        val installed = parseVersion(installedVersionName(context)) ?: return@withContext null
        if (remote <= installed) return@withContext null

        val assets = release.getJSONArray("assets")
        var apkUrl: String? = null
        var apkSize = 0L
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.getString("name").endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.getString("browser_download_url")
                apkSize = asset.optLong("size")
                break
            }
        }
        if (apkUrl == null) return@withContext null

        AvailableUpdate(
            versionName = tag.removePrefix("v"),
            versionCode = remote.code,
            notes = release.optString("body").trim(),
            apkUrl = apkUrl,
            apkSizeBytes = apkSize
        )
    }

    private fun fetch(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (conn.responseCode != 200) error("GitHub returned HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun installedVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    } catch (e: PackageManager.NameNotFoundException) {
        "0"
    }

    /** Parsed "1.2.3" style version, comparable numerically. */
    data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
        val code: Int get() = major * 10_000 + minor * 100 + patch
        override fun compareTo(other: Version): Int = code.compareTo(other.code)
    }

    fun parseVersion(raw: String): Version? {
        val parts = raw.trim().removePrefix("v").removePrefix("V").split('.')
        if (parts.isEmpty() || parts.size > 3) return null
        val nums = parts.map { it.toIntOrNull() ?: return null }
        return Version(nums[0], nums.getOrElse(1) { 0 }, nums.getOrElse(2) { 0 })
    }
}
