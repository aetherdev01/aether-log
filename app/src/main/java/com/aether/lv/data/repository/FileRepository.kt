package com.aether.lv.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.aether.lv.data.db.RecentFileDao
import com.aether.lv.data.model.RecentFile
import com.aether.lv.util.FileTypeUtil
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
        val info = queryFileInfo(uri)
        val ext  = FileTypeUtil.extensionOf(info.name)
        if (!FileTypeUtil.isAllowed(ext)) return@withContext
        dao.upsert(
            RecentFile(
                path         = uri.toString(),
                displayName  = info.name,
                fileType     = ext,
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
        maxLines: Int = 20_000
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@runCatching listOf<String>().also {
                    error("Tidak dapat membuka file. Pastikan file masih ada dan izin storage sudah diberikan.")
                }

            stream.bufferedReader(Charsets.UTF_8).use { reader ->
                val lines = ArrayList<String>(minOf(maxLines, 1024))
                var count = 0
                reader.forEachLine { line ->
                    if (count < maxLines) lines.add(line)
                    count++
                }
                if (count > maxLines) {
                    lines.add("── [${count - maxLines} baris lebih dipotong] ──")
                }
                lines
            }
        }.recoverCatching { e ->
            // Coba fallback dengan charset lain jika UTF-8 gagal
            val stream2 = context.contentResolver.openInputStream(uri)
                ?: error("Tidak dapat membuka file.")
            stream2.bufferedReader(Charsets.ISO_8859_1).use { reader ->
                val lines = ArrayList<String>(minOf(maxLines, 1024))
                var count = 0
                reader.forEachLine { line ->
                    if (count < maxLines) lines.add(line)
                    count++
                }
                lines
            }
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
