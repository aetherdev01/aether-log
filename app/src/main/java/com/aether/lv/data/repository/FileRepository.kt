package com.aether.lv.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.aether.lv.data.db.RecentFileDao
import com.aether.lv.data.model.RecentFile
import com.aether.lv.util.FileTypeUtil
import com.aether.lv.util.GzipUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FileRepository(
    private val context: Context,
    private val dao: RecentFileDao
) {
    // ─── Riwayat ────────────────────────────────────────────────────────────
    val recentFiles: Flow<List<RecentFile>> = dao.getAllFlow()

    suspend fun saveRecent(uri: Uri, lineCount: Int = 0) = withContext(Dispatchers.IO) {
        // Ambil persistent permission agar URI tetap bisa dibuka di sesi berikutnya.
        // ACTION_OPEN_DOCUMENT hanya memberi temporary permission; takePersistableUriPermission
        // membuatnya permanen sehingga file di riwayat bisa dibuka tanpa permission denial.
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // URI dari sumber eksternal (ACTION_SEND/share) tidak support persistable — aman diabaikan
        }

        val info = queryFileInfo(uri)
        val ext  = FileTypeUtil.extensionOf(info.name)
        dao.upsert(
            RecentFile(
                path         = uri.toString(),
                displayName  = info.name,
                fileType     = ext.ifBlank { "log" },
                sizeBytes    = info.size,
                lastOpenedAt = System.currentTimeMillis(),
                lineCount    = lineCount
            )
        )
    }

    suspend fun removeRecent(path: String) = dao.deleteByPath(path)
    suspend fun clearHistory()             = dao.clearAll()

    // ─── Baca konten file ───────────────────────────────────────────────────
    /**
     * Membaca semua baris dari URI dengan penanganan error yang proper.
     * Maksimum [maxLines] baris untuk mencegah OOM pada file sangat besar.
     */
    suspend fun readLines(
        uri: Uri,
        maxLines: Int = 30_000
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        // Coba UTF-8 dulu (dengan auto-decompress jika GZIP)
        val result = runCatching {
            val raw = context.contentResolver.openInputStream(uri)
                ?: return@runCatching Result.failure<List<String>>(
                    IllegalStateException("Tidak dapat membuka file. Pastikan file masih ada dan izin storage sudah diberikan.")
                )
            // Wrap dengan GZIPInputStream secara otomatis jika magic bytes cocok
            val stream = GzipUtil.wrapIfNeeded(raw)
            stream.bufferedReader(Charsets.UTF_8).use { reader ->
                val lines = ArrayList<String>(minOf(maxLines, 2048))
                var count = 0
                reader.forEachLine { line ->
                    if (count < maxLines) lines.add(line)
                    count++
                }
                if (count > maxLines) {
                    lines.add("── [${count - maxLines} baris lebih dipotong] ──")
                }
                Result.success(lines)
            }
        }.getOrElse { e ->
            Result.failure(e)
        }

        // Kalau UTF-8 gagal (misal file binary/latin), fallback ke ISO-8859-1
        // Tetap pakai GzipUtil.wrapIfNeeded agar .gz latin juga ter-handle
        if (result.isFailure) {
            runCatching {
                val raw2 = context.contentResolver.openInputStream(uri)
                    ?: return@runCatching Result.failure(IllegalStateException("Tidak dapat membuka file."))
                val stream2 = GzipUtil.wrapIfNeeded(raw2)
                stream2.bufferedReader(Charsets.ISO_8859_1).use { reader ->
                    val lines = ArrayList<String>(minOf(maxLines, 2048))
                    var count = 0
                    reader.forEachLine { line ->
                        if (count < maxLines) lines.add(line)
                        count++
                    }
                    Result.success(lines)
                }
            }.getOrElse { e -> Result.failure(e) }
        } else {
            result
        }
    }

    /**
     * Info file dari ContentResolver.
     */
    private fun queryFileInfo(uri: Uri): FileInfo {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file.log"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) { /* fallback ke lastPathSegment */ }
        return FileInfo(name, size)
    }

    private data class FileInfo(val name: String, val size: Long)
}
