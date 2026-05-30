package com.aether.lv.util

import androidx.compose.ui.graphics.Color
import com.aether.lv.ui.theme.*

enum class LogLevel { VERBOSE, DEBUG, INFO, WARNING, ERROR, FATAL, PLAIN }

data class ParsedLine(
    val raw: String,
    val level: LogLevel,
    val color: Color
)

object LogLineParser {
    // Android logcat pattern: "MM-DD HH:MM:SS.mmm  PID  TID LEVEL TAG: message"
    private val LOGCAT_REGEX = Regex("""^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+\s+\d+\s+\d+\s+([VDIWEF])\s""")
    private val LEVEL_MARKERS = mapOf(
        "V/" to LogLevel.VERBOSE, "D/" to LogLevel.DEBUG,
        "I/" to LogLevel.INFO,    "W/" to LogLevel.WARNING,
        "E/" to LogLevel.ERROR,   "F/" to LogLevel.FATAL,
        "[V]" to LogLevel.VERBOSE,"[D]" to LogLevel.DEBUG,
        "[I]" to LogLevel.INFO,   "[W]" to LogLevel.WARNING,
        "[E]" to LogLevel.ERROR,  "[F]" to LogLevel.FATAL,
        "VERBOSE" to LogLevel.VERBOSE, "DEBUG" to LogLevel.DEBUG,
        "INFO"    to LogLevel.INFO,    "WARN"  to LogLevel.WARNING,
        "WARNING" to LogLevel.WARNING, "ERROR" to LogLevel.ERROR,
        "FATAL"   to LogLevel.FATAL,   "CRITICAL" to LogLevel.FATAL,
    )

    fun parse(line: String, applyColors: Boolean): ParsedLine {
        if (!applyColors) return ParsedLine(line, LogLevel.PLAIN, LogColorDefault)
        val upper = line.uppercase()
        // Logcat format check
        LOGCAT_REGEX.find(line)?.let { m ->
            val level = when (m.groupValues[1]) {
                "V" -> LogLevel.VERBOSE; "D" -> LogLevel.DEBUG
                "I" -> LogLevel.INFO;    "W" -> LogLevel.WARNING
                "E" -> LogLevel.ERROR;   "F" -> LogLevel.FATAL
                else -> LogLevel.PLAIN
            }
            return ParsedLine(line, level, colorOf(level))
        }
        // Keyword check
        for ((marker, level) in LEVEL_MARKERS) {
            if (upper.contains(marker)) return ParsedLine(line, level, colorOf(level))
        }
        return ParsedLine(line, LogLevel.PLAIN, LogColorDefault)
    }

    fun colorOf(level: LogLevel): Color = when (level) {
        LogLevel.VERBOSE -> LogColorVerbose
        LogLevel.DEBUG   -> LogColorDebug
        LogLevel.INFO    -> LogColorInfo
        LogLevel.WARNING -> LogColorWarning
        LogLevel.ERROR   -> LogColorError
        LogLevel.FATAL   -> LogColorFatal
        LogLevel.PLAIN   -> LogColorDefault
    }
}
