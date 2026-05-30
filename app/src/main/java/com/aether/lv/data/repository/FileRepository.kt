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

    suspend fun saveRecent(
        uri: Uri,
        lineCount: Int = 0,
        callerContext: android.content.Context? = null
    ) = withContext(Dispatchers.IO) {
        val ctx = callerContext ?: context
        val alreadyPersisted = ctx.contentResolver.persistedUriPermissions.any { perm ->
            perm.uri == uri && perm.isReadPermission
        }
        val isPersisted = if (alreadyPersisted) {
            true
        } else {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                true
            } catch (_: SecurityException) {
                false
            }
        }

        val info = queryFileInfo(uri, ctx)
        val ext  = FileTypeUtil.extensionOf(info.name)
        dao.upsert(
            RecentFile(
                path         = uri.toString(),
                displayName  = info.name,
                fileType     = ext.ifBlank { "log" },
                sizeBytes    = info.size,
                lastOpenedAt = System.currentTimeMillis(),
                lineCount    = lineCount,
                isPersisted  = isPersisted
            )
        )
    }

    suspend fun removeRecent(path: String) = dao.deleteByPath(path)
    suspend fun clearHistory()             = dao.clearAll()

    // ─── Baca konten file ───────────────────────────────────────────────────
    /**
     * Membaca semua baris dari URI.
     *
     * PENTING: Selalu gunakan callerContext (Activity context) untuk URI apapun,
     * baik dari ACTION_VIEW maupun ACTION_OPEN_DOCUMENT (SAF).
     * Application context tidak memiliki grant SAF URI permission dari sistem.
     */
    suspend fun readLines(
        uri: Uri,
        maxLines: Int = 30_000,
        callerContext: android.content.Context? = null
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        // Selalu pakai callerContext — jangan fallback ke application context
        // karena SAF URI permission hanya dipegang oleh Activity/Process yang menerima intent
        val ctx = callerContext ?: context

        // Untuk URI file:// — baca langsung via File, bypass ContentResolver
        if (uri.scheme == "file") {
            return@withContext readFromFile(uri.path ?: return@withContext Result.failure(
                IllegalStateException("Path file tidak valid.")
            ), maxLines)
        }

        // Test akses awal — tangkap SecurityException segera
        try {
            ctx.contentResolver.openInputStream(uri)?.close()
        } catch (e: SecurityException) {
            // Fallback: coba baca via File path langsung (untuk URI non-SAF)
            val path = uri.path
            if (path != null && java.io.File(path).exists()) {
                return@withContext readFromFile(path, maxLines)
            }
            return@withContext Result.failure(
                SecurityException("Izin akses file ditolak. Silakan buka file kembali melalui tombol \"Buka File\".")
            )
        } catch (e: Exception) {
            return@withContext Result.failure(
                IllegalStateException("Tidak dapat membuka file: ${e.message}")
            )
        }

        // Coba UTF-8 dulu (dengan auto-decompress jika GZIP)
        val result = runCatching {
            val raw = ctx.contentResolver.openInputStream(uri)
                ?: return@runCatching Result.failure<List<String>>(
                    IllegalStateException("Stream null — file tidak dapat dibuka.")
                )
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
            if (e is SecurityException) return@withContext Result.failure(e)
            Result.failure(e)
        }

        // Fallback ISO-8859-1 jika UTF-8 decode gagal (file binary/latin)
        if (result.isFailure) {
            runCatching {
                val raw2 = ctx.contentResolver.openInputStream(uri)
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
            }.getOrElse { e ->
                if (e is SecurityException) Result.failure(e)
                else Result.failure(e)
            }
        } else {
            result
        }
    }

    /**
     * Cek apakah URI masih bisa diakses (persistent permission belum dicabut sistem).
     */
    fun isUriAccessible(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (_: SecurityException) { false }
        catch (_: Exception) { false }
    }

    /**
     * Hapus entri riwayat yang URI-nya sudah tidak accessible.
     */
    suspend fun pruneInaccessibleRecents() = withContext(Dispatchers.IO) {
        val all = dao.getAll()
        all.forEach { file ->
            val uri = Uri.parse(file.path)
            if (uri.scheme != "content") return@forEach
            if (!file.isPersisted) return@forEach
            if (!isUriAccessible(uri)) {
                dao.deleteByPath(file.path)
            }
        }
    }

    private fun readFromFile(path: String, maxLines: Int): Result<List<String>> {
        return try {
            val file = java.io.File(path)
            if (!file.exists()) return Result.failure(java.io.FileNotFoundException("File tidak ditemukan: $path"))
            val stream = GzipUtil.wrapIfNeeded(file.inputStream())
            stream.bufferedReader(Charsets.UTF_8).use { reader ->
                val lines = ArrayList<String>(minOf(maxLines, 2048))
                var count = 0
                reader.forEachLine { line ->
                    if (count < maxLines) lines.add(line)
                    count++
                }
                if (count > maxLines) lines.add("── [${count - maxLines} baris lebih dipotong] ──")
                Result.success(lines)
            }
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: java.io.IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun queryFileInfo(uri: Uri, ctx: android.content.Context = context): FileInfo {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file.log"
        var size = 0L
        try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
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
