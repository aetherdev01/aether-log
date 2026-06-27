package com.aether.lv.util

object FileTypeUtil {

    private val ALLOWED = setOf("log", "txt", "json", "xml", "yaml", "yml", "err", "out", "logcat", "gz")

    fun extensionOf(name: String): String {
        // Handle double extension: kernel.log.gz → "log.gz"
        val lower = name.lowercase()
        if (lower.endsWith(".log.gz"))  return "log.gz"
        if (lower.endsWith(".txt.gz"))  return "txt.gz"
        if (lower.endsWith(".out.gz"))  return "out.gz"
        if (lower.endsWith(".err.gz"))  return "err.gz"
        return name.substringAfterLast('.', "").lowercase()
    }

    fun isAllowed(ext: String): Boolean = ext in ALLOWED ||
        ext.endsWith(".gz") // tangkap semua varian .gz

    fun isAllowedName(name: String): Boolean = isAllowed(extensionOf(name))

    /** Ikon Material Icon name per tipe file */
    fun iconKey(ext: String): FileIconType = when {
        ext == "json"              -> FileIconType.JSON
        ext == "xml"               -> FileIconType.XML
        ext == "yaml" || ext == "yml" -> FileIconType.YAML
        ext == "err" || ext == "err.gz" -> FileIconType.ERROR
        ext == "out" || ext == "out.gz" -> FileIconType.OUT
        ext.endsWith(".gz")        -> FileIconType.GZ
        else                       -> FileIconType.LOG
    }

    fun mimeType(ext: String): String = when {
        ext == "json"              -> "application/json"
        ext == "xml"               -> "text/xml"
        ext == "yaml" || ext == "yml" -> "text/plain"
        ext.endsWith(".gz")        -> "application/gzip"
        else                       -> "text/plain"
    }

    /** Chip label untuk badge tipe */
    fun label(ext: String): String = when (ext) {
        "logcat"         -> "LOGCAT"
        "log"            -> "LOG"
        "log.gz"         -> "LOG.GZ"
        "txt"            -> "TXT"
        "txt.gz"         -> "TXT.GZ"
        "json"           -> "JSON"
        "xml"            -> "XML"
        "yaml", "yml"    -> "YAML"
        "err"            -> "ERR"
        "err.gz"         -> "ERR.GZ"
        "out"            -> "OUT"
        "out.gz"         -> "OUT.GZ"
        "gz"             -> "GZ"
        else             -> ext.uppercase()
    }
}

enum class FileIconType { LOG, JSON, XML, YAML, ERROR, OUT, GZ }
