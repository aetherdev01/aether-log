package com.aether.lv.util

/**
 * JNI bridge ke libxplus.so (X+.cpp) — License Key Format Validator.
 *
 * Validasi format dan checksum kode lisensi dilakukan di native layer
 * sehingga logika tidak terekspos di bytecode Kotlin / DEX.
 *
 * Ini adalah validasi CLIENT-SIDE (format & checksum saja).
 * Validasi penuh (aktif/expired/device binding) tetap dilakukan
 * oleh server via [LicenseRepository.activate].
 *
 * Format lisensi yang valid:
 *   XXXX-XXXX-XXXX-XXXX  (Base36: 0-9, A-Z, 4 grup × 4 karakter)
 */
object LicenseNative {

    // Library yang sama dengan NativeSearch — xplus sudah diload di NativeSearch.init,
    // tapi load di sini juga aman (Android hanya load sekali per proses).
    init {
        System.loadLibrary("xplus")
    }

    /**
     * Validasi format dan checksum kode lisensi.
     *
     * @param key Kode lisensi (contoh: "A1B2-C3D4-E5F6-G7H8")
     * @return true jika format valid — belum tentu aktif di server
     */
    @JvmStatic
    external fun nativeValidateLicenseFormat(key: String): Boolean

    // ── Convenience wrapper ───────────────────────────────────────────────────

    /**
     * Normalisasi + validasi. Hapus spasi, ubah ke uppercase, lalu cek native.
     */
    fun isValidFormat(raw: String): Boolean {
        val key = raw.trim().uppercase().replace(" ", "")
        return nativeValidateLicenseFormat(key)
    }
}
