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

    private const val APK_NAME = "loglog-update.apk"

    /**
     * Download APK dengan progress callback (0..100).
     * Return file jika sukses, null jika gagal.
     */
    suspend fun download(
        context     : Context,
        url         : String,
        totalBytes  : Long,
        onProgress  : (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val outFile = File(context.cacheDir, APK_NAME)
            if (outFile.exists()) outFile.delete()

            val conn = URL(url).openConnection() as HttpURLConnection
            conn.apply {
                requestMethod  = "GET"
                connectTimeout = 10_000
                readTimeout    = 30_000
                instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return@runCatching null
            }

            val length = if (totalBytes > 0) totalBytes else conn.contentLengthLong
            conn.inputStream.use { input ->
                outFile.outputStream().use { output ->
                    val buf      = ByteArray(8 * 1024)
                    var written  = 0L
                    var lastPct  = -1
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        written += read
                        if (length > 0) {
                            val pct = ((written * 100) / length).toInt().coerceIn(0, 100)
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
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
     * Trigger system installer untuk file APK yang sudah di-download.
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

    /** Hapus APK cache setelah install */
    fun cleanUp(context: Context) {
        File(context.cacheDir, APK_NAME).delete()
    }
}
