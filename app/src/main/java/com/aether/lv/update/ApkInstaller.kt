package com.aether.lv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ApkInstaller {

    /**
     * Download file update dengan nama asli dari GitHub Releases.
     *
     * [fileName] : nama file sesuai asset GitHub (mis. "loglog-v1.5-release.apk").
     *              Dipakai sebagai nama file di cache, sehingga nama yang tersimpan
     *              di device sama persis dengan yang dirilis.
     * [url]      : browser_download_url dari GitHub API.
     * [totalBytes]: ukuran file untuk kalkulasi progress; 0 = pakai Content-Length.
     * [onProgress]: callback 0..100.
     *
     * Return File jika sukses, null jika gagal.
     */
    suspend fun download(
        context    : Context,
        url        : String,
        fileName   : String,
        totalBytes : Long,
        onProgress : (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            // Sanitasi nama file — buang karakter berbahaya untuk path
            val safeName = fileName
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .ifBlank { "update.apk" }

            val outFile = File(context.cacheDir, safeName)
            if (outFile.exists()) outFile.delete()

            val conn = URL(url).openConnection() as HttpURLConnection
            conn.apply {
                requestMethod          = "GET"
                connectTimeout         = 10_000
                readTimeout            = 30_000
                instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return@runCatching null
            }

            val length = if (totalBytes > 0) totalBytes else conn.contentLengthLong
            conn.inputStream.use { input ->
                outFile.outputStream().use { output ->
                    val buf     = ByteArray(8 * 1024)
                    var written = 0L
                    var lastPct = -1
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        written += read
                        if (length > 0) {
                            val pct = ((written * 100) / length).toInt().coerceIn(0, 100)
                            if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                        }
                    }
                }
            }
            conn.disconnect()
            onProgress(100)
            outFile
        }.getOrNull()
    }

    /**
     * Trigger system installer untuk APK yang sudah di-download.
     */
    fun install(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.update.provider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Hapus semua file cache update (semua .apk & .zip di cacheDir dengan prefix loglog/aether).
     * Lebih aman dari hapus hardcoded "update.apk" yang tidak ada lagi.
     */
    fun cleanUp(context: Context) {
        context.cacheDir.listFiles()?.forEach { file ->
            val n = file.name.lowercase()
            if ((n.endsWith(".apk") || n.endsWith(".zip")) &&
                (n.contains("loglog") || n.contains("aether") || n.contains("update"))) {
                file.delete()
            }
        }
    }
}
