package com.aether.lv.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtil {

    fun formatSize(bytes: Long): String = when {
        bytes < 1024L             -> "$bytes B"
        bytes < 1024L * 1024L     -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
        else                      -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

    fun formatRelativeTime(epochMillis: Long): String {
        val now  = System.currentTimeMillis()
        val diff = now - epochMillis
        return when {
            diff < 60_000L              -> "Baru saja"
            diff < 3_600_000L           -> "${diff / 60_000L} menit lalu"
            diff < 86_400_000L          -> "${diff / 3_600_000L} jam lalu"
            diff < 86_400_000L * 7      -> "${diff / 86_400_000L} hari lalu"
            else -> SimpleDateFormat("dd MMM yyyy", Locale("id")).format(Date(epochMillis))
        }
    }

    fun formatDateTime(epochMillis: Long): String =
        SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale("id")).format(Date(epochMillis))
}
