package com.aether.lv.license

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "LicenseRepository"

// ─── DataStore ────────────────────────────────────────────────────────────────
private val Context.licenseDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "aether_license")

private object PrefKeys {
    val LICENSE_KEY      = stringPreferencesKey("license_key")
    val IS_PREMIUM       = booleanPreferencesKey("is_premium")
    val EXPIRES_AT       = longPreferencesKey("expires_at")       // epoch ms, 0 = lifetime
    val PRODUCT_ID       = stringPreferencesKey("product_id")
    val PRODUCT_NAME     = stringPreferencesKey("product_name")
    val LAST_VERIFIED_AT = longPreferencesKey("last_verified_at") // epoch ms
    val FEATURES         = stringPreferencesKey("features")       // JSON array string
}

// ─── Data classes ─────────────────────────────────────────────────────────────
data class LicenseState(
    val isPremium      : Boolean = false,
    val licenseKey     : String  = "",
    val expiresAt      : Long    = 0L,     // 0 = lifetime
    val productId      : String  = "",
    val productName    : String  = "",
    val lastVerifiedAt : Long    = 0L,
    val features       : List<String> = emptyList()
) {
    val isNoAds    get() = isPremium && "no_ads" in features
    val isLifetime get() = isPremium && expiresAt == 0L
    val isExpired  get() = !isLifetime && expiresAt > 0L && System.currentTimeMillis() > expiresAt
}

sealed class ActivateResult {
    data class Success(val state: LicenseState) : ActivateResult()
    data class Error(val message: String)        : ActivateResult()
}

// ─── Repository ───────────────────────────────────────────────────────────────
class LicenseRepository(private val context: Context) {

    companion object {
        // Base URL Vercel API — sesuaikan jika domain berubah
        private const val BASE_URL = "https://aether-app-weld.vercel.app/api"

        // Package name dikirim ke server untuk validasi
        private const val PACKAGE_NAME = "com.aether.lv"

        // Cache offline: re-verify tiap 12 jam, toleransi 7 hari offline
        private const val VERIFY_INTERVAL_MS  = 12L * 60 * 60 * 1000
        private const val OFFLINE_GRACE_MS    =  7L * 24 * 60 * 60 * 1000
    }

    // ── Device ID (stable, no permission needed) ──────────────────────────────
    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown_device"
    }

    // ── StateFlow of license ──────────────────────────────────────────────────
    val licenseState: Flow<LicenseState> = context.licenseDataStore.data
        .map { prefs ->
            LicenseState(
                isPremium      = prefs[PrefKeys.IS_PREMIUM]       ?: false,
                licenseKey     = prefs[PrefKeys.LICENSE_KEY]      ?: "",
                expiresAt      = prefs[PrefKeys.EXPIRES_AT]       ?: 0L,
                productId      = prefs[PrefKeys.PRODUCT_ID]       ?: "",
                productName    = prefs[PrefKeys.PRODUCT_NAME]     ?: "",
                lastVerifiedAt = prefs[PrefKeys.LAST_VERIFIED_AT] ?: 0L,
                features       = parseFeatureList(prefs[PrefKeys.FEATURES] ?: "")
            )
        }
        .distinctUntilChanged()

    // ── Aktivasi lisensi baru ─────────────────────────────────────────────────
    suspend fun activate(licenseKey: String): ActivateResult = withContext(Dispatchers.IO) {
        val key = licenseKey.trim().uppercase()
        if (key.length < 8) return@withContext ActivateResult.Error("Kode lisensi tidak valid")

        try {
            // 1. Hit /activate endpoint
            val activateBody = JSONObject().apply {
                put("key",         key)
                put("deviceId",    deviceId)
                put("deviceName",  android.os.Build.MODEL)
                put("model",       android.os.Build.MODEL)
                put("packageName", PACKAGE_NAME)
            }
            val activateResp = postJson("$BASE_URL/activate", activateBody)
            Log.d(TAG, "activate response: $activateResp")

            val valid = activateResp.optBoolean("valid", false)
            if (!valid) {
                val error = activateResp.optString("error", "")
                return@withContext ActivateResult.Error(mapServerError(error))
            }

            // 2. Parse & simpan ke DataStore
            val state = parseAndSaveLicense(key, activateResp)
            ActivateResult.Success(state)

        } catch (e: Exception) {
            Log.e(TAG, "activate error: ${e.message}")
            ActivateResult.Error("Gagal menghubungi server. Periksa koneksi internet.")
        }
    }

    // ── Verifikasi ulang (background, setiap buka app) ────────────────────────
    suspend fun verifyIfNeeded(): Unit = withContext(Dispatchers.IO) {
        val prefs = context.licenseDataStore.data.first()
        val key   = prefs[PrefKeys.LICENSE_KEY] ?: return@withContext
        if (key.isBlank()) return@withContext

        val lastVerified = prefs[PrefKeys.LAST_VERIFIED_AT] ?: 0L
        val now          = System.currentTimeMillis()

        if (now - lastVerified < VERIFY_INTERVAL_MS) {
            Log.d(TAG, "verify skipped (cache valid)")
            return@withContext
        }

        try {
            val body = JSONObject().apply {
                put("key",         key)
                put("deviceId",    deviceId)
                put("packageName", PACKAGE_NAME)
            }
            val resp = postJson("$BASE_URL/verify", body)
            Log.d(TAG, "verify response: $resp")

            val valid = resp.optBoolean("valid", false)
            if (valid) {
                parseAndSaveLicense(key, resp)
            } else {
                // Jika error bukan network — cabut premium
                val error = resp.optString("error", "")
                if (error in listOf("NOT_FOUND", "DEVICE_NOT_ACTIVATED", "expired", "suspended", "banned", "revoked")) {
                    revoke()
                }
                // Jika error lain (server issue) → jaga premium dengan grace period
            }
        } catch (e: Exception) {
            Log.w(TAG, "verify network error (grace period active): ${e.message}")
            // Offline grace: kalau > 7 hari offline, cabut premium
            val lastVerified2 = context.licenseDataStore.data.first()[PrefKeys.LAST_VERIFIED_AT] ?: 0L
            if (System.currentTimeMillis() - lastVerified2 > OFFLINE_GRACE_MS) {
                Log.w(TAG, "Offline grace period expired — revoking")
                revoke()
            }
        }
    }

    // ── Hapus lisensi (logout) ────────────────────────────────────────────────
    suspend fun revoke() {
        context.licenseDataStore.edit { it.clear() }
        Log.d(TAG, "license revoked")
    }

    // ── Internal helpers ──────────────────────────────────────────────────────
    private suspend fun parseAndSaveLicense(key: String, json: JSONObject): LicenseState {
        val features  = parseFeatureListFromJson(json.optJSONArray("features"))
        val expiresAt = json.optLong("expiresAt", 0L)
        val productId = json.optString("productId", "basic_1m")
        val productName = json.optString("productName", "Aether Basic")
        val now = System.currentTimeMillis()

        context.licenseDataStore.edit { prefs ->
            prefs[PrefKeys.LICENSE_KEY]      = key
            prefs[PrefKeys.IS_PREMIUM]       = true
            prefs[PrefKeys.EXPIRES_AT]       = expiresAt
            prefs[PrefKeys.PRODUCT_ID]       = productId
            prefs[PrefKeys.PRODUCT_NAME]     = productName
            prefs[PrefKeys.LAST_VERIFIED_AT] = now
            prefs[PrefKeys.FEATURES]         = features.joinToString(",")
        }

        return LicenseState(
            isPremium      = true,
            licenseKey     = key,
            expiresAt      = expiresAt,
            productId      = productId,
            productName    = productName,
            lastVerifiedAt = now,
            features       = features
        )
    }

    private fun postJson(urlString: String, body: JSONObject): JSONObject {
        val url  = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            requestMethod  = "POST"
            connectTimeout = 10_000
            readTimeout    = 10_000
            doOutput       = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "LogLog/$PACKAGE_NAME")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code     = conn.responseCode
        val response = if (code in 200..299) conn.inputStream else conn.errorStream
        val text     = response?.bufferedReader()?.readText() ?: "{}"
        conn.disconnect()
        return JSONObject(text)
    }

    private fun mapServerError(error: String): String = when (error) {
        "NOT_FOUND"            -> "Kode lisensi tidak ditemukan"
        "DEVICE_NOT_ACTIVATED" -> "Device ini belum diaktifkan untuk lisensi ini"
        "DEVICE_LIMIT_REACHED" -> "Batas maksimal device sudah tercapai"
        "expired"              -> "Lisensi sudah kedaluwarsa"
        "suspended"            -> "Lisensi ini di-suspend"
        "banned"               -> "Lisensi ini di-banned"
        "revoked"              -> "Lisensi ini dicabut"
        "PROJECT_API_KEY_REQUIRED" -> "Konfigurasi server error (API key)"
        else                   -> if (error.isNotBlank()) "Error: $error" else "Lisensi tidak valid"
    }

    private fun parseFeatureList(csv: String): List<String> =
        if (csv.isBlank()) emptyList() else csv.split(",").map { it.trim() }

    private fun parseFeatureListFromJson(arr: org.json.JSONArray?): List<String> {
        if (arr == null) return listOf("no_ads")
        return (0 until arr.length()).map { arr.getString(it) }
    }
}
