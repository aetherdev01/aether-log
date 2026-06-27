package com.aether.lv.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

// ─────────────────────────────────────────────────────────────────────────────
// Warna sintaksis (dark-friendly, kontras cukup di light juga)
// ─────────────────────────────────────────────────────────────────────────────
object SyntaxColors {
    // JSON
    val jsonKey        = Color(0xFF82AAFF)  // biru muda — key
    val jsonString     = Color(0xFFC3E88D)  // hijau — string value
    val jsonNumber     = Color(0xFFF78C6C)  // oranye — number
    val jsonBoolean    = Color(0xFFFF5370)  // merah — true/false/null
    val jsonBrace      = Color(0xFFFFCB6B)  // kuning — {} []
    val jsonColon      = Color(0xFFB0BEC5)  // abu

    // XML / HTML
    val xmlTag         = Color(0xFF82AAFF)  // biru — <tag>
    val xmlAttrName    = Color(0xFFFFCB6B)  // kuning — atribut
    val xmlAttrValue   = Color(0xFFC3E88D)  // hijau — "value"
    val xmlComment     = Color(0xFF546E7A)  // abu gelap — <!-- -->
    val xmlCdata       = Color(0xFF89DDFF)  // cyan — <![CDATA[
    val xmlProlog      = Color(0xFFB0BEC5)  // abu — <?xml ...?>
    val xmlEntity      = Color(0xFFFF5370)  // merah — &amp; dll

    // YAML
    val yamlKey        = Color(0xFF82AAFF)  // biru — key:
    val yamlString     = Color(0xFFC3E88D)  // hijau — "str" / 'str'
    val yamlNumber     = Color(0xFFF78C6C)  // oranye — angka
    val yamlBoolean    = Color(0xFFFF5370)  // merah — true/false/null/yes/no
    val yamlComment    = Color(0xFF546E7A)  // abu — # komentar
    val yamlAnchor     = Color(0xFFB0BEC5)  // abu — &anchor / *alias
    val yamlDirective  = Color(0xFFB0BEC5)  // abu — --- / ...
    val yamlTag        = Color(0xFF89DDFF)  // cyan — !!type

    // LOG / LOGCAT / ERR / OUT
    val logVerbose     = Color(0xFF9E9E9E)
    val logDebug       = Color(0xFF42A5F5)
    val logInfo        = Color(0xFF66BB6A)
    val logWarn        = Color(0xFFFFCA28)
    val logError       = Color(0xFFEF5350)
    val logFatal       = Color(0xFFAB47BC)
    val logTimestamp   = Color(0xFF78909C)  // abu biru — timestamp
    val logPid         = Color(0xFF4DB6AC)  // teal — PID/TID
    val logTag         = Color(0xFFFFD54F)  // kuning — TAG:
    val logPath        = Color(0xFF80CBC4)  // cyan soft — /path/to/file
    val logDefault     = Color(0xFFB0BEC5)

    // TXT — tidak ada highlight khusus; URL & email saja
    val txtUrl         = Color(0xFF82AAFF)
    val txtEmail       = Color(0xFFC3E88D)
    val txtNumber      = Color(0xFFF78C6C)
}

// ─────────────────────────────────────────────────────────────────────────────
// Enum tipe file untuk dispatch
// ─────────────────────────────────────────────────────────────────────────────
enum class SyntaxType {
    NONE,   // plain text / tidak dikenali → tidak ada highlight
    JSON,
    XML,
    YAML,
    LOG,    // log / logcat / err / out
}

fun syntaxTypeOf(fileName: String): SyntaxType {
    val ext = FileTypeUtil.extensionOf(fileName)
    return when {
        ext == "json"                    -> SyntaxType.JSON
        ext == "xml"                     -> SyntaxType.XML
        ext == "yaml" || ext == "yml"    -> SyntaxType.YAML
        ext == "log"   || ext == "logcat"
            || ext == "log.gz"
            || ext == "err"  || ext == "err.gz"
            || ext == "out"  || ext == "out.gz"
            || ext == "txt"  || ext == "txt.gz"
                                         -> SyntaxType.LOG
        else                             -> SyntaxType.NONE
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Entry point utama
// ─────────────────────────────────────────────────────────────────────────────
object SyntaxHighlighter {

    /**
     * Ubah [text] menjadi [AnnotatedString] dengan warna sintaksis sesuai [type].
     * Dipanggil dari ViewModel di coroutine Dispatchers.Default.
     * Jika teks terlalu panjang (> [maxChars]), kembalikan plain tanpa highlight
     * agar UI tidak lag.
     */
    fun highlight(text: String, type: SyntaxType, maxChars: Int = 120_000): AnnotatedString {
        if (type == SyntaxType.NONE || text.length > maxChars) {
            return AnnotatedString(text)
        }
        return when (type) {
            SyntaxType.JSON -> highlightJson(text)
            SyntaxType.XML  -> highlightXml(text)
            SyntaxType.YAML -> highlightYaml(text)
            SyntaxType.LOG  -> highlightLog(text)
            else            -> AnnotatedString(text)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JSON
    // ─────────────────────────────────────────────────────────────────────────
    private fun highlightJson(text: String): AnnotatedString = buildAnnotatedString {
        append(text)

        // Braces & brackets
        addJsonColorPattern(text, Regex("[{}\\[\\]]"), SyntaxColors.jsonBrace)
        // Colon & comma
        addJsonColorPattern(text, Regex("[:,]"), SyntaxColors.jsonColon)
        // Numbers (standalone, not inside strings)
        addJsonColorPattern(
            text,
            Regex("""(?<!["\w])-?\d+(\.\d+)?([eE][+-]?\d+)?(?!["\w])"""),
            SyntaxColors.jsonNumber
        )
        // true / false / null
        addJsonColorPattern(
            text,
            Regex("""\b(true|false|null)\b"""),
            SyntaxColors.jsonBoolean
        )
        // String values (before keys so keys override)
        addJsonStringSpans(text, isKey = false)
        // Keys  — "key":
        addJsonStringSpans(text, isKey = true)
    }

    private fun AnnotatedString.Builder.addJsonColorPattern(
        text: String, regex: Regex, color: Color
    ) {
        for (m in regex.findAll(text)) {
            addStyle(SpanStyle(color = color), m.range.first, m.range.last + 1)
        }
    }

    private fun AnnotatedString.Builder.addJsonStringSpans(text: String, isKey: Boolean) {
        // Scan karakter demi karakter untuk temukan string literal yang valid
        var i = 0
        while (i < text.length) {
            if (text[i] == '"') {
                val start = i
                i++
                while (i < text.length) {
                    if (text[i] == '\\') { i += 2; continue }
                    if (text[i] == '"') { i++; break }
                    i++
                }
                val end = i
                // Tentukan apakah ini key: setelah penutup " harus ada ':'
                val afterClose = text.substring(end).trimStart()
                val looksLikeKey = afterClose.startsWith(":")
                if (isKey && looksLikeKey) {
                    addStyle(SpanStyle(color = SyntaxColors.jsonKey, fontWeight = FontWeight.SemiBold), start, end)
                } else if (!isKey && !looksLikeKey) {
                    addStyle(SpanStyle(color = SyntaxColors.jsonString), start, end)
                }
            } else {
                i++
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // XML
    // ─────────────────────────────────────────────────────────────────────────
    private val XML_COMMENT   = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
    private val XML_CDATA     = Regex("""<!\[CDATA\[.*?]]>""", RegexOption.DOT_MATCHES_ALL)
    private val XML_PROLOG    = Regex("""<\?.*?\?>""", RegexOption.DOT_MATCHES_ALL)
    private val XML_DOCTYPE   = Regex("""<!DOCTYPE[^>]*>""", RegexOption.IGNORE_CASE)
    private val XML_TAG_FULL  = Regex("""</?[A-Za-z_][A-Za-z0-9_:.-]*(\s[^>]*)?>""", RegexOption.DOT_MATCHES_ALL)
    private val XML_ATTR_VAL  = Regex(""""[^"]*"|'[^']*'""")
    private val XML_ATTR_NAME = Regex("""\s([A-Za-z_:][A-Za-z0-9_:.-]*)(?=\s*=)""")
    private val XML_ENTITY    = Regex("""&[A-Za-z#][A-Za-z0-9]*;""")

    private fun highlightXml(text: String): AnnotatedString = buildAnnotatedString {
        append(text)
        // Urutan penting: komentar & CDATA duluan (override segalanya)
        for (m in XML_COMMENT.findAll(text))
            addStyle(SpanStyle(color = SyntaxColors.xmlComment, fontStyle = FontStyle.Italic), m.range.first, m.range.last + 1)
        for (m in XML_CDATA.findAll(text))
            addStyle(SpanStyle(color = SyntaxColors.xmlCdata), m.range.first, m.range.last + 1)
        for (m in XML_PROLOG.findAll(text))
            addStyle(SpanStyle(color = SyntaxColors.xmlProlog), m.range.first, m.range.last + 1)
        for (m in XML_DOCTYPE.findAll(text))
            addStyle(SpanStyle(color = SyntaxColors.xmlProlog), m.range.first, m.range.last + 1)
        // Tag lengkap → biru
        for (m in XML_TAG_FULL.findAll(text)) {
            addStyle(SpanStyle(color = SyntaxColors.xmlTag), m.range.first, m.range.last + 1)
            val tagText = m.value
            val tagStart = m.range.first
            // Attr value → hijau
            for (av in XML_ATTR_VAL.findAll(tagText))
                addStyle(SpanStyle(color = SyntaxColors.xmlAttrValue), tagStart + av.range.first, tagStart + av.range.last + 1)
            // Attr name → kuning
            for (an in XML_ATTR_NAME.findAll(tagText))
                addStyle(SpanStyle(color = SyntaxColors.xmlAttrName), tagStart + an.range.first + 1, tagStart + an.range.last + 1)
        }
        // Entity
        for (m in XML_ENTITY.findAll(text))
            addStyle(SpanStyle(color = SyntaxColors.xmlEntity), m.range.first, m.range.last + 1)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // YAML
    // ─────────────────────────────────────────────────────────────────────────
    private val YAML_COMMENT   = Regex("""#[^\n]*""")
    private val YAML_DIRECTIVE = Regex("""^(---|\.\.\.)\s*$""", RegexOption.MULTILINE)
    private val YAML_ANCHOR    = Regex("""[&*][A-Za-z_][A-Za-z0-9_-]*""")
    private val YAML_TAG_TYPE  = Regex("""!![A-Za-z]+""")
    private val YAML_KEY       = Regex("""^(\s*)((?:[^:'"#\n{}\[\]|>]|:[^ \n])+)(?=\s*:(?:\s|$))""", RegexOption.MULTILINE)
    private val YAML_DQUOTE    = Regex(""""(?:[^"\\]|\\.)*"""")
    private val YAML_SQUOTE    = Regex("""'(?:[^']|'')*'""")
    private val YAML_NUMBER    = Regex("""\b-?\d+(\.\d+)?([eE][+-]?\d+)?\b""")
    private val YAML_BOOLEAN   = Regex("""\b(true|false|null|yes|no|on|off|True|False|Null|Yes|No|On|Off|TRUE|FALSE|NULL)\b""")

    private fun highlightYaml(text: String): AnnotatedString = buildAnnotatedString {
        append(text)
        // Directive ---/...
        for (m in YAML_DIRECTIVE.findAll(text))
            addStyle(SpanStyle(color = SyntaxColors.yamlDirective, fontWeight = FontWeight.Bold), m.range.first, m.range.last + 1)
        // Numbers
        for (m in YAML_NUMBER.findAll(text))
            addStyle(SpanStyle(color = SyntaxColors.yamlNumber), m.range.first, m.range.last + 1)
        // Booleans
        for (m in YAML_BOOLEAN.findAll(text))
            addStyle(SpanStyle(color = SyntaxColors.yamlBoolean), m.range.first, m.range.last + 1)
        // Keys
        for (m in YAML_KEY.findAll(text)) {
            val keyGroup = m.groups[2] ?: continue
            val s = m.range.first + (keyGroup.range.first - m.range.first)
            addStyle(SpanStyle(color = SyntaxColors.yamlKey, fontWeight = FontWeight.SemiBold), s, s + keyGroup.value.length)
        }
        // Double-quoted strings
        for (m in YAML_DQUOTE.findAll(text))
            addStyle(SpanStyle(color = SyntaxColors.yamlString), m.range.first, m.range.last + 1)
        // Single-quoted strings
        for (m in YAML_SQUOTE.findAll(text))
            addStyle(SpanStyle(color = SyntaxColors.yamlString), m.range.first, m.range.last + 1)
        // Anchors & aliases
        for (m in YAML_ANCHOR.findAll(text))
            addStyle(SpanStyle(color = SyntaxColors.yamlAnchor), m.range.first, m.range.last + 1)
        // !!type tags
        for (m in YAML_TAG_TYPE.findAll(text))
            addStyle(SpanStyle(color = SyntaxColors.yamlTag), m.range.first, m.range.last + 1)
        // Comments (harus terakhir agar override segalanya di baris itu)
        for (m in YAML_COMMENT.findAll(text))
            addStyle(SpanStyle(color = SyntaxColors.yamlComment, fontStyle = FontStyle.Italic), m.range.first, m.range.last + 1)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOG / LOGCAT / ERR / OUT / TXT
    // Highlight per-baris: deteksi level → warnai seluruh baris +
    // sub-pattern (timestamp, PID, TAG, path, URL)
    // ─────────────────────────────────────────────────────────────────────────

    // Android logcat: "MM-DD HH:MM:SS.mmm  PID  TID LEVEL TAG: message"
    private val LOGCAT_FULL = Regex(
        """^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+)\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+([^:]+:)(.*)$"""
    )
    // Timestamp genérik: ISO-8601, common log format
    private val TIMESTAMP_GENERIC = Regex(
        """\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:[.,]\d+)?(?:Z|[+-]\d{2}:?\d{2})?"""
    )
    // Timestamp pendek: HH:MM:SS atau MM-DD HH:MM:SS
    private val TIMESTAMP_SHORT = Regex(
        """\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+|\d{2}:\d{2}:\d{2}(?:[.,]\d+)?"""
    )
    // Path Unix/Android
    private val PATH_REGEX = Regex("""(?<![A-Za-z0-9_])/(?:[\w.+-]+/)+[\w.+-]*""")
    // URL
    private val URL_REGEX  = Regex("""https?://[^\s"'<>]+""")
    // Level keyword dalam teks bebas
    private val LEVEL_REGEX = Regex(
        """\b(VERBOSE|TRACE|DEBUG|INFO|WARN(?:ING)?|ERROR|ERR|FATAL|CRITICAL|SEVERE|WTF)\b""",
        RegexOption.IGNORE_CASE
    )
    // Level bracket: [D] [I] [W] [E] dll
    private val LEVEL_BRACKET = Regex("""\[([VDIWEF])\]""", RegexOption.IGNORE_CASE)

    private fun logLevelColor(level: String): Color = when (level.uppercase().trimEnd(':')) {
        "V", "VERBOSE", "TRACE"              -> SyntaxColors.logVerbose
        "D", "DEBUG"                         -> SyntaxColors.logDebug
        "I", "INFO"                          -> SyntaxColors.logInfo
        "W", "WARN", "WARNING"               -> SyntaxColors.logWarn
        "E", "ERR", "ERROR"                  -> SyntaxColors.logError
        "F", "FATAL", "CRITICAL", "SEVERE",
        "WTF"                                -> SyntaxColors.logFatal
        else                                 -> SyntaxColors.logDefault
    }

    private fun highlightLog(text: String): AnnotatedString = buildAnnotatedString {
        append(text)

        // ── Pass 1: Logcat format lengkap (per baris) ────────────────────────
        for (m in LOGCAT_FULL.findAll(text)) {
            val base = m.range.first
            val g    = m.groupValues
            val lineColor = logLevelColor(g[4])

            // Timestamp grup 1
            val tsRange = m.groups[1]?.range
            if (tsRange != null)
                addStyle(SpanStyle(color = SyntaxColors.logTimestamp), base + tsRange.first - base, base + tsRange.last - base + 1)

            // PID TID grup 2 & 3
            val pidRange = m.groups[2]?.range
            val tidRange = m.groups[3]?.range
            if (pidRange != null)
                addStyle(SpanStyle(color = SyntaxColors.logPid), base + pidRange.first - base, base + pidRange.last - base + 1)
            if (tidRange != null)
                addStyle(SpanStyle(color = SyntaxColors.logPid), base + tidRange.first - base, base + tidRange.last - base + 1)

            // Level karakter grup 4
            val lvlRange = m.groups[4]?.range
            if (lvlRange != null)
                addStyle(SpanStyle(color = lineColor, fontWeight = FontWeight.Bold), base + lvlRange.first - base, base + lvlRange.last - base + 1)

            // TAG: grup 5
            val tagRange = m.groups[5]?.range
            if (tagRange != null)
                addStyle(SpanStyle(color = SyntaxColors.logTag, fontWeight = FontWeight.SemiBold), base + tagRange.first - base, base + tagRange.last - base + 1)
        }

        // ── Pass 2: Timestamp generik ────────────────────────────────────────
        for (m in TIMESTAMP_GENERIC.findAll(text))
            addStyle(SpanStyle(color = SyntaxColors.logTimestamp), m.range.first, m.range.last + 1)
        for (m in TIMESTAMP_SHORT.findAll(text))
            addStyle(SpanStyle(color = SyntaxColors.logTimestamp), m.range.first, m.range.last + 1)

        // ── Pass 3: Level keyword bebas ──────────────────────────────────────
        for (m in LEVEL_REGEX.findAll(text))
            addStyle(SpanStyle(color = logLevelColor(m.value), fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
        for (m in LEVEL_BRACKET.findAll(text))
            addStyle(SpanStyle(color = logLevelColor(m.groupValues[1]), fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)

        // ── Pass 4: Path & URL ───────────────────────────────────────────────
        for (m in PATH_REGEX.findAll(text))
            addStyle(SpanStyle(color = SyntaxColors.logPath), m.range.first, m.range.last + 1)
        for (m in URL_REGEX.findAll(text))
            addStyle(SpanStyle(color = SyntaxColors.txtUrl), m.range.first, m.range.last + 1)
    }
}
