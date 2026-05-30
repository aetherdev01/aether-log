package com.aether.lv.update

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// ─── Model ───────────────────────────────────────────────────────────────────

data class UpdateInfo(
    val latestVersion : String,   // e.g. "1.2"
    val tagName       : String,   // e.g. "v1.2"
    val changelog     : String,
    val downloadUrl   : String,
    val apkSizeBytes  : Long,
    val publishedAt   : String,
    val isNewVersion  : Boolean
)

// GitHub API response shape (hanya field yang dipakai)
private data class GhRelease(
    @SerializedName("tag_name")     val tagName     : String   = "",
    @SerializedName("name")         val name        : String   = "",
    @SerializedName("body")         val body        : String   = "",
    @SerializedName("published_at") val publishedAt : String   = "",
    @SerializedName("prerelease")   val prerelease  : Boolean  = false,
    @SerializedName("draft")        val draft       : Boolean  = false,
    @SerializedName("assets")       val assets      : List<GhAsset> = emptyList()
)

private data class GhAsset(
    @SerializedName("name")                  val name               : String = "",
    @SerializedName("size")                  val size               : Long   = 0,
    @SerializedName("browser_download_url")  val browserDownloadUrl : String = ""
)

// ─── Checker ─────────────────────────────────────────────────────────────────

object UpdateChecker {

    private const val API_URL     = "https://api.github.com/repos/aetherdev01/aether-log/releases/latest"
    private const val PREFS_NAME  = "loglog_update_cache"
    private const val KEY_JSON    = "cached_release_json"
    private const val KEY_TIME    = "cached_at_ms"
    private const val CACHE_TTL   = 6 * 60 * 60 * 1000L  // 6 jam

    private val gson = Gson()

    /**
     * Cek update. Pakai cache jika masih segar.
     * Return null jika jaringan gagal DAN tidak ada cache.
     */
    suspend fun check(context: Context, currentVersion: String): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val release = fetchRelease(prefs) ?: return@withContext null
            parseRelease(release, currentVersion)
        }

    private fun fetchRelease(prefs: SharedPreferences): GhRelease? {
        val now     = System.currentTimeMillis()
        val cachedAt = prefs.getLong(KEY_TIME, 0)

        // Pakai cache jika masih dalam TTL
        if (now - cachedAt < CACHE_TTL) {
            val json = prefs.getString(KEY_JSON, null)
            if (!json.isNullOrEmpty()) {
                return runCatching { gson.fromJson(json, GhRelease::class.java) }.getOrNull()
            }
        }

        // Fetch dari API
        return runCatching {
            val conn = URL(API_URL).openConnection() as HttpURLConnection
            conn.apply {
                requestMethod  = "GET"
                connectTimeout = 8_000
                readTimeout    = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            if (conn.responseCode != 200) return@runCatching null
            val json = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            // Simpan cache
            prefs.edit()
                .putString(KEY_JSON, json)
                .putLong(KEY_TIME, System.currentTimeMillis())
                .apply()

            gson.fromJson(json, GhRelease::class.java)
        }.getOrNull()
    }

    private fun parseRelease(release: GhRelease, currentVersion: String): UpdateInfo? {
        if (release.draft || release.prerelease) return null

        // Ambil APK asset
        val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
            ?: return null

        // Strip 'v' prefix untuk perbandingan versi
        val latestVer = release.tagName.trimStart('v')

        return UpdateInfo(
            latestVersion = latestVer,
            tagName       = release.tagName,
            changelog     = release.body.trim(),
            downloadUrl   = apkAsset.browserDownloadUrl,
            apkSizeBytes  = apkAsset.size,
            publishedAt   = release.publishedAt,
            isNewVersion  = isNewer(latestVer, currentVersion)
        )
    }

    /** Bandingkan versi semver sederhana: "1.2" > "1.1" */
    private fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.split(".").map { it.toIntOrNull() ?: 0 }
        val l = parts(latest)
        val c = parts(current)
        val len = maxOf(l.size, c.size)
        for (i in 0 until len) {
            val lp = l.getOrElse(i) { 0 }
            val cp = c.getOrElse(i) { 0 }
            if (lp > cp) return true
            if (lp < cp) return false
        }
        return false
    }

    /** Force clear cache — buat manual refresh */
    fun clearCache(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
