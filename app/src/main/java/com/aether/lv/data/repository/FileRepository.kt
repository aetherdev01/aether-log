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
    private val dao    : RecentFileDao
) {
    val recentFiles: Flow<List<RecentFile>> = dao.getAllFlow()

    /**
     * Simpan ke riwayat.
     *
     * [activityContext] WAJIB Activity context (bukan Application) agar
     * takePersistableUriPermission berhasil. Application context tidak punya
     * grant URI permission dari SAF/ACTION_VIEW.
     */
    suspend fun saveRecent(
        uri            : Uri,
        lineCount      : Int = 0,
        activityContext: Context? = null
    ) = withContext(Dispatchers.IO) {
        // Selalu pakai activityContext untuk persistable permission
        val ctx = activityContext ?: context

        val isPersisted = when (uri.scheme) {
            "content" -> {
                // Cek apakah sudah punya persistent permission
                val already = ctx.contentResolver.persistedUriPermissions.any { perm ->
                    perm.uri == uri && perm.isReadPermission
                }
                if (already) {
                    true
                } else {
                    try {
                        ctx.contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        true
                    } catch (_: SecurityException) {
                        // URI dari file manager eksternal tidak support persistable — OK
                        false
                    }
                }
            }
            "file" -> true  // file:// selalu bisa dibaca kalau ada permission filesystem
            else   -> false
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

    /**
     * Baca baris dari URI.
     *
     * [activityContext] WAJIB Activity context untuk URI dari SAF / ACTION_VIEW.
     */
    suspend fun readLines(
        uri            : Uri,
        maxLines       : Int = 30_000,
        activityContext: Context? = null
    ): Result<List<String>> = withContext(Dispatchers.IO) {

        // file:// → baca langsung via File
        if (uri.scheme == "file") {
            val path = uri.path ?: return@withContext Result.failure(
                IllegalStateException("Path file tidak valid.")
            )
            return@withContext readFromFile(path, maxLines)
        }

        // Untuk content:// — gunakan activityContext dulu, fallback ke application context
        // CATATAN: Application context TIDAK bisa buka URI dari SAF/ACTION_VIEW yang belum
        // di-persist. Selalu teruskan Activity context dari caller.
        val ctx = activityContext ?: context

        // Test buka stream dulu untuk tangkap error awal
        try {
            ctx.contentResolver.openInputStream(uri)?.close()
        } catch (e: SecurityException) {
            // Coba fallback path langsung (beberapa file manager kirim URI dengan path)
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
            return@withContext Result.failure(
                IllegalStateException("Tidak dapat membuka file: ${e.message}")
            )
        }

        // Baca dengan UTF-8, fallback ISO-8859-1
        val result = runCatching {
            val raw    = ctx.contentResolver.openInputStream(uri)
                ?: return@runCatching Result.failure<List<String>>(
                    IllegalStateException("Stream null — file tidak dapat dibuka.")
                )
            val stream = GzipUtil.wrapIfNeeded(raw)
            stream.bufferedReader(Charsets.UTF_8).use { reader ->
                buildLineList(reader, maxLines)
            }
        }.getOrElse { e ->
            if (e is SecurityException) return@withContext Result.failure(e)
            Result.failure(e)
        }

        if (result.isFailure) {
            runCatching {
                val raw2   = ctx.contentResolver.openInputStream(uri)
                    ?: return@runCatching Result.failure(IllegalStateException("Tidak dapat membuka file."))
                val stream2 = GzipUtil.wrapIfNeeded(raw2)
                stream2.bufferedReader(Charsets.ISO_8859_1).use { reader ->
                    buildLineList(reader, maxLines)
                }
            }.getOrElse { e ->
                if (e is SecurityException) Result.failure(e) else Result.failure(e)
            }
        } else {
            result
        }
    }

    fun isUriAccessible(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
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

    // ── Private helpers ────────────────────────────────────────────────────

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
            if (!file.exists()) return Result.failure(
                java.io.FileNotFoundException("File tidak ditemukan: $path")
            )
            GzipUtil.wrapIfNeeded(file.inputStream())
                .bufferedReader(Charsets.UTF_8)
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
