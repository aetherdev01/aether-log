package com.aether.lv.util

/**
 * JNI bridge ke libxplus.so (X+.cpp) — Fast Log Search.
 *
 * Gunakan [searchLines] untuk mendapatkan nomor baris yang match,
 * atau [countMatches] jika hanya butuh jumlah total.
 *
 * Thread-safe: semua operasi stateless di sisi native.
 */
object NativeSearch {

    init {
        System.loadLibrary("xplus")
    }

    /**
     * Cari semua baris dalam [content] yang mengandung [query].
     *
     * @param content  Seluruh isi file sebagai satu String (baris dipisah '\n')
     * @param query    Kata kunci; mendukung wildcard `*` (banyak karakter) dan `?` (satu karakter)
     * @param ignoreCase true → pencarian tidak case-sensitive
     * @return IntArray berisi nomor baris (0-based) yang cocok
     */
    @JvmStatic
    external fun nativeSearchLines(
        content    : String,
        query      : String,
        ignoreCase : Boolean,
    ): IntArray

    /**
     * Hitung jumlah baris yang mengandung [query] tanpa mengembalikan daftarnya.
     * Lebih efisien dari `nativeSearchLines(...).size` jika hanya butuh angkanya.
     */
    @JvmStatic
    external fun nativeCountMatches(
        content    : String,
        query      : String,
        ignoreCase : Boolean,
    ): Int

    // ── Convenience wrappers ──────────────────────────────────────────────────

    /**
     * Versi suspend-friendly: jalankan search di coroutine pemanggil.
     * Caller harus dispatch ke Dispatchers.Default sendiri jika perlu.
     */
    fun search(
        lines      : List<String>,
        query      : String,
        ignoreCase : Boolean = true,
    ): List<Int> {
        if (query.isBlank() || lines.isEmpty()) return emptyList()
        val content = lines.joinToString("\n")
        return nativeSearchLines(content, query, ignoreCase).toList()
    }

    /**
     * Hitung match langsung dari List<String>.
     */
    fun count(
        lines      : List<String>,
        query      : String,
        ignoreCase : Boolean = true,
    ): Int {
        if (query.isBlank() || lines.isEmpty()) return 0
        val content = lines.joinToString("\n")
        return nativeCountMatches(content, query, ignoreCase)
    }
}
