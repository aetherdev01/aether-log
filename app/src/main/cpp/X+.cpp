/**
 * X+.cpp
 *
 * Dua fitur native untuk LogLog:
 *
 * 1. FAST LOG SEARCH
 *    Pencarian string (plain) dan pattern (wildcard: * ?) di buffer teks log
 *    yang dikirim dari Kotlin sebagai satu blok besar. Menggunakan Boyer-Moore-
 *    Horspool untuk plain search — jauh lebih cepat dari regex Kotlin di file
 *    ratusan ribu baris.
 *
 *    JNI exports:
 *      nativeSearchLines(content, query, ignoreCase)  → int[] baris yang match
 *      nativeCountMatches(content, query, ignoreCase) → jumlah match (int)
 *
 * 2. LICENSE KEY FORMAT VALIDATOR
 *    Validasi format & checksum kode lisensi di native layer sebelum request
 *    dikirim ke server. Logika checksum di-XOR encode agar tidak terlihat di
 *    bytecode Kotlin / tidak bisa di-patch dengan hex editor pada .dex.
 *
 *    Format lisensi: XXXX-XXXX-XXXX-XXXX (Base36, 4 grup × 4 karakter)
 *    Checksum: byte terakhir grup ke-4 adalah XOR dari semua byte payload.
 *
 *    JNI exports:
 *      nativeValidateLicenseFormat(key) → boolean
 */

#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <cctype>
#include <algorithm>

// ═════════════════════════════════════════════════════════════════════════════
// § 1  FAST LOG SEARCH
// ═════════════════════════════════════════════════════════════════════════════

// ─── Boyer-Moore-Horspool bad-character table ──────────────────────────────
static void buildBmhTable(const std::string& pat, size_t table[256]) {
    size_t m = pat.size();
    for (size_t i = 0; i < 256; ++i) table[i] = m;
    for (size_t i = 0; i < m - 1; ++i)
        table[(unsigned char)pat[i]] = m - 1 - i;
}

// ─── Case-fold helper ──────────────────────────────────────────────────────
static std::string toLower(const std::string& s) {
    std::string r(s);
    for (char& c : r) c = static_cast<char>(std::tolower((unsigned char)c));
    return r;
}

// ─── Wildcard match (* = any sequence, ? = single char) ───────────────────
static bool wildcardMatch(const char* text, size_t tlen,
                           const char* pat,  size_t plen) {
    // DP tabel: dp[i][j] = text[0..i-1] matches pat[0..j-1]
    std::vector<std::vector<bool>> dp(tlen + 1, std::vector<bool>(plen + 1, false));
    dp[0][0] = true;
    for (size_t j = 1; j <= plen; ++j)
        if (pat[j - 1] == '*') dp[0][j] = dp[0][j - 1];

    for (size_t i = 1; i <= tlen; ++i) {
        for (size_t j = 1; j <= plen; ++j) {
            if (pat[j - 1] == '*') {
                dp[i][j] = dp[i - 1][j] || dp[i][j - 1];
            } else if (pat[j - 1] == '?' || pat[j - 1] == text[i - 1]) {
                dp[i][j] = dp[i - 1][j - 1];
            }
        }
    }
    return dp[tlen][plen];
}

static bool hasWildcard(const std::string& s) {
    return s.find('*') != std::string::npos ||
           s.find('?') != std::string::npos;
}

/**
 * Cari semua nomor baris (0-based) dalam `content` yang mengandung `query`.
 * content  : seluruh isi file sebagai satu string (baris dipisah '\n')
 * query    : kata kunci; mendukung wildcard * dan ?
 * ignoreCase: true → case-insensitive
 *
 * Return: vector nomor baris yang match.
 */
static std::vector<int> searchLines(const std::string& content,
                                     const std::string& query,
                                     bool ignoreCase) {
    std::vector<int> result;
    if (query.empty() || content.empty()) return result;

    // Siapkan versi fold jika case-insensitive
    std::string haystack = ignoreCase ? toLower(content) : content;
    std::string needle   = ignoreCase ? toLower(query)   : query;
    bool useWild = hasWildcard(needle);

    if (!useWild) {
        // ── Boyer-Moore-Horspool ─────────────────────────────────────────────
        size_t table[256];
        buildBmhTable(needle, table);
        size_t m = needle.size();
        size_t n = haystack.size();

        // Kita perlu tahu nomor baris untuk setiap posisi match.
        // Iterasi baris sekaligus — jauh lebih cache-friendly.
        size_t lineStart = 0;
        int    lineNo    = 0;
        size_t pos       = 0;

        // Indeks baris per posisi byte terlalu mahal memorinya untuk file besar.
        // Strategi: scan baris satu per satu, dalam tiap baris jalankan BMH.
        while (pos <= n) {
            size_t nextNl = haystack.find('\n', pos);
            if (nextNl == std::string::npos) nextNl = n;

            size_t lineLen = nextNl - pos;
            if (lineLen >= m) {
                // BMH dalam baris ini
                size_t j = m - 1;
                while (j < lineLen) {
                    size_t k = m - 1;
                    size_t i = j;
                    while (k < m && haystack[pos + i] == needle[k]) {
                        if (k == 0) { result.push_back(lineNo); goto nextLine; }
                        --i; --k;
                    }
                    j += table[(unsigned char)haystack[pos + j]];
                }
            }
            nextLine:
            pos    = nextNl + 1;
            lineNo++;
        }
    } else {
        // ── Wildcard: scan baris per baris ──────────────────────────────────
        size_t pos    = 0;
        size_t n      = haystack.size();
        int    lineNo = 0;
        while (pos <= n) {
            size_t nextNl = haystack.find('\n', pos);
            if (nextNl == std::string::npos) nextNl = n;
            size_t lineLen = nextNl - pos;
            if (wildcardMatch(haystack.c_str() + pos, lineLen,
                               needle.c_str(), needle.size())) {
                result.push_back(lineNo);
            }
            pos = nextNl + 1;
            lineNo++;
        }
    }
    return result;
}

// ─── JNI: searchLines → int[] ─────────────────────────────────────────────
extern "C"
JNIEXPORT jintArray JNICALL
Java_com_aether_lv_util_NativeSearch_nativeSearchLines(
        JNIEnv* env, jclass,
        jstring jContent, jstring jQuery, jboolean jIgnoreCase)
{
    const char* rawContent = env->GetStringUTFChars(jContent, nullptr);
    const char* rawQuery   = env->GetStringUTFChars(jQuery,   nullptr);

    std::string content(rawContent);
    std::string query(rawQuery);
    bool ignoreCase = (jIgnoreCase == JNI_TRUE);

    env->ReleaseStringUTFChars(jContent, rawContent);
    env->ReleaseStringUTFChars(jQuery,   rawQuery);

    auto hits = searchLines(content, query, ignoreCase);

    jintArray arr = env->NewIntArray(static_cast<jsize>(hits.size()));
    if (!hits.empty())
        env->SetIntArrayRegion(arr, 0, static_cast<jsize>(hits.size()), hits.data());
    return arr;
}

// ─── JNI: countMatches → int ──────────────────────────────────────────────
extern "C"
JNIEXPORT jint JNICALL
Java_com_aether_lv_util_NativeSearch_nativeCountMatches(
        JNIEnv* env, jclass,
        jstring jContent, jstring jQuery, jboolean jIgnoreCase)
{
    const char* rawContent = env->GetStringUTFChars(jContent, nullptr);
    const char* rawQuery   = env->GetStringUTFChars(jQuery,   nullptr);

    std::string content(rawContent);
    std::string query(rawQuery);
    bool ignoreCase = (jIgnoreCase == JNI_TRUE);

    env->ReleaseStringUTFChars(jContent, rawContent);
    env->ReleaseStringUTFChars(jQuery,   rawQuery);

    return static_cast<jint>(searchLines(content, query, ignoreCase).size());
}

// ═════════════════════════════════════════════════════════════════════════════
// § 2  LICENSE KEY FORMAT VALIDATOR
// ═════════════════════════════════════════════════════════════════════════════
//
// Validasi FORMAT saja — panjang, karakter legal (alphanumeric + tanda pisah),
// dan minimum entropy (tidak semua karakter sama).
//
// Checksum/authenticity sepenuhnya urusan server (/activate endpoint).
// Native layer hanya memastikan string yang masuk masuk akal sebelum
// membuang network request — bukan memvalidasi kode itu sendiri.
//
// Format yang didukung (fleksibel):
//   • Minimal 8 karakter setelah strip tanda '-'
//   • Karakter legal: 0-9, A-Z, a-z, '-'
//   • Tidak boleh semua karakter identik (entropy check)

// Cek apakah karakter legal untuk kode lisensi
static bool isLicenseChar(char c) {
    return std::isalnum((unsigned char)c) || c == '-';
}

// Minimum entropy: payload tidak boleh semua karakter sama
static bool hasEntropy(const std::string& s) {
    if (s.empty()) return false;
    char first = s[0];
    for (char c : s)
        if (c != first) return true;
    return false;
}

/**
 * Validasi format kode lisensi (format saja, bukan checksum).
 * Return: true jika karakter legal, panjang cukup, dan ada variasi karakter.
 */
static bool validateLicenseFormat(const std::string& key) {
    if (key.size() < 8) return false;

    // Semua karakter harus legal
    for (char c : key)
        if (!isLicenseChar(c)) return false;

    // Strip '-' untuk cek panjang payload dan entropy
    std::string payload;
    payload.reserve(key.size());
    for (char c : key)
        if (c != '-') payload += static_cast<char>(std::toupper((unsigned char)c));

    if (payload.size() < 8) return false;
    if (!hasEntropy(payload)) return false;

    return true;
}

// ─── JNI: validateLicenseFormat → boolean ─────────────────────────────────
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_aether_lv_util_LicenseNative_nativeValidateLicenseFormat(
        JNIEnv* env, jclass,
        jstring jKey)
{
    const char* raw = env->GetStringUTFChars(jKey, nullptr);
    std::string key(raw);
    env->ReleaseStringUTFChars(jKey, raw);

    return validateLicenseFormat(key) ? JNI_TRUE : JNI_FALSE;
}
