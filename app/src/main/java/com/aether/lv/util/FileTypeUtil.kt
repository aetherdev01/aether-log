package com.aether.lv.util

object FileTypeUtil {

    private val ALLOWED = setOf("log", "txt", "json", "xml", "yaml", "yml", "err", "out", "logcat")

    fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase()

    fun isAllowed(ext: String): Boolean = ext in ALLOWED

    fun isAllowedName(name: String): Boolean = isAllowed(extensionOf(name))

    /** Ikon Material Icon name per tipe file */
    fun iconKey(ext: String): FileIconType = when (ext) {
        "json"           -> FileIconType.JSON
        "xml"            -> FileIconType.XML
        "yaml", "yml"    -> FileIconType.YAML
        "err"            -> FileIconType.ERROR
        "out"            -> FileIconType.OUT
        else             -> FileIconType.LOG   // log, txt, logcat, dll
    }

    fun mimeType(ext: String): String = when (ext) {
        "json"           -> "application/json"
        "xml"            -> "text/xml"
        "yaml", "yml"    -> "text/plain"
        else             -> "text/plain"
    }

    /** Chip label untuk badge tipe */
    fun label(ext: String): String = when (ext) {
        "logcat"         -> "LOGCAT"
        "log"            -> "LOG"
        "txt"            -> "TXT"
        "json"           -> "JSON"
        "xml"            -> "XML"
        "yaml", "yml"    -> "YAML"
        "err"            -> "ERR"
        "out"            -> "OUT"
        else             -> ext.uppercase()
    }
}

enum class FileIconType { LOG, JSON, XML, YAML, ERROR, OUT }
