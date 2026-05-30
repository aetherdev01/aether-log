package com.aether.lv.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entitas Room untuk riwayat file yang pernah dibuka.
 */
@Entity(tableName = "recent_files")
data class RecentFile(
    @PrimaryKey
    val path: String,           // URI string (content:// atau file://)
    val displayName: String,    // Nama file tampil
    val fileType: String,       // "log", "txt", "json", "xml", "yaml", "err", "out"
    val sizeBytes: Long,        // Ukuran file bytes
    val lastOpenedAt: Long,     // Epoch millis
    val lineCount: Int = 0,     // Jumlah baris (diisi saat baca)
)
