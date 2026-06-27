package com.aether.lv.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.aether.lv.data.db.RecentFileDao
import com.aether.lv.data.model.RecentFile
import com.aether.lv.util.FileTypeUtil
import com.aether.lv.util.GzipUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.FileInputStream

class FileRepository(
    private val context: Context,
    private val dao    : RecentFileDao
) {
    val recentFiles: Flow<List<RecentFile>> = dao.getAllFlow()

    suspend fun saveRecent(
        uri            : Uri,
        lineCount      : Int = 0,
        activityContext: Context? = null
    ) = withContext(Dispatchers.IO) {
        val ctx = activityContext ?: context

        val isPersisted = when (uri.scheme) {
            "content" -> {
                val already = try {
                    ctx.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
                } catch (_: Exception) { false }

                if (already) true
                else {
                    try {
                        ctx.contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        true
                    } catch (_: SecurityException) { false }
                }
            }
            "file" -> true
            else   -> false
        }

        val info = queryFileInfo(uri, ctx)
        val ext  = FileTypeUtil.extensionOf(info.name)
        dao.upsert(RecentFile(
            path         = uri.toString(),
            displayName  = info.name,
            fileType     = ext.ifBlank { "log" },
            sizeBytes    = info.size,
            lastOpenedAt = System.currentTimeMillis(),
            lineCount    = lineCount,
            isPersisted  = isPersisted
        ))
    }

    suspend fun removeRecent(path: String) = dao.deleteByPath(path)
    suspend fun clearHistory()             = dao.clearAll()

    /**
     * Baca baris file dari URI.
     *
     * Strategi baca (prioritas urutan):
     * 1. file:// → FileInputStream langsung
     * 2. content:// via openFileDescriptor (lebih andal di Android 13+/Samsung)
     * 3. Fallback path langsung dari URI jika #2 gagal
     */
    suspend fun readLines(
        uri            : Uri,
        maxLines       : Int = 30_000,
        activityContext: Context? = null
    ): Result<List<String>> = withContext(Dispatchers.IO) {

        // ── 1. file:// ────────────────────────────────────────────────────
        if (uri.scheme == "file") {
            val path = uri.path ?: return@withContext Result.failure(
                IllegalStateException("Path file tidak valid.")
            )
            return@withContext readFromFile(path, maxLines)
        }

        // Gunakan Activity context jika ada — satu-satunya yang punya URI grant
        val ctx = activityContext ?: context

        // ── 2. openFileDescriptor — lebih andal dari openInputStream di Samsung ──
        val pfd: ParcelFileDescriptor? = try {
            ctx.contentResolver.openFileDescriptor(uri, "r")
        } catch (e: SecurityException) {
            // Coba fallback path jika URI punya path filesystem
            val path = uri.path
            if (path != null && java.io.File(path).exists()) {
                return@withContext readFromFile(path, maxLines)
            }
            return@withContext Result.failure(
                SecurityException(
                    "Izin akses file ditolak.\n\n" +
                    "Silakan buka file kembali melalui tombol \"Buka File\" di halaman utama."
                )
            )
        } catch (e: Exception) {
            return@withContext Result.failure(IllegalStateException("Tidak dapat membuka file: ${e.message}"))
        }

        if (pfd == null) {
            return@withContext Result.failure(IllegalStateException("Tidak dapat membuka file descriptor."))
        }

        // ── 3. Baca dari file descriptor ─────────────────────────────────
        val result = runCatching {
            FileInputStream(pfd.fileDescriptor).use { fis ->
                val stream = GzipUtil.wrapIfNeeded(fis)
                stream.bufferedReader(Charsets.UTF_8).use { buildLineList(it, maxLines) }
            }
        }.also {
            // Tutup PFD di luar runCatching agar tidak leak
            try { pfd.close() } catch (_: Exception) { }
        }.getOrElse { e ->
            if (e is SecurityException) return@withContext Result.failure(e)
            // Fallback ISO-8859-1
            try {
                val pfd2 = ctx.contentResolver.openFileDescriptor(uri, "r")
                    ?: return@withContext Result.failure(IllegalStateException("Tidak dapat membuka file."))
                FileInputStream(pfd2.fileDescriptor).use { fis ->
                    val stream = GzipUtil.wrapIfNeeded(fis)
                    stream.bufferedReader(Charsets.ISO_8859_1).use { buildLineList(it, maxLines) }
                }.also { try { pfd2.close() } catch (_: Exception) { } }
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }

        result
    }

    fun isUriAccessible(uri: Uri): Boolean {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (_: Exception) { false }
    }

    suspend fun pruneInaccessibleRecents() = withContext(Dispatchers.IO) {
        dao.getAll().forEach { file ->
            val uri = Uri.parse(file.path)
            if (uri.scheme != "content") return@forEach
            if (!file.isPersisted) return@forEach
            if (!isUriAccessible(uri)) dao.deleteByPath(file.path)
        }
    }

    private fun buildLineList(reader: java.io.BufferedReader, maxLines: Int): Result<List<String>> {
        val lines = ArrayList<String>(minOf(maxLines, 2048))
        var count = 0
        reader.forEachLine { line ->
            if (count < maxLines) lines.add(line)
            count++
        }
        if (count > maxLines) lines.add("── [${count - maxLines} baris lebih dipotong] ──")
        return Result.success(lines)
    }

    private fun readFromFile(path: String, maxLines: Int): Result<List<String>> {
        return try {
            val file = java.io.File(path)
            if (!file.exists()) return Result.failure(java.io.FileNotFoundException("File tidak ditemukan: $path"))
            GzipUtil.wrapIfNeeded(file.inputStream()).bufferedReader(Charsets.UTF_8)
                .use { buildLineList(it, maxLines) }
        } catch (e: SecurityException) { Result.failure(e) }
        catch (e: java.io.IOException)  { Result.failure(e) }
        catch (e: Exception)            { Result.failure(e) }
    }

    private fun queryFileInfo(uri: Uri, ctx: Context = context): FileInfo {
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
        } catch (_: Exception) { }
        return FileInfo(name, size)
    }

    private data class FileInfo(val name: String, val size: Long)
}
