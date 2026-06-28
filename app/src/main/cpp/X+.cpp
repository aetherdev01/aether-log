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
// Format: XXXX-XXXX-XXXX-XXXX
//   • Setiap karakter: Base36 (0-9, A-Z)
//   • 4 grup × 4 karakter, dipisah '-'
//   • Total panjang: 19 karakter
//
// Checksum (obfuscated):
//   Byte terakhir (karakter ke-15 dari payload) harus sama dengan
//   XOR semua 15 karakter payload sebelumnya, di-AND dengan mask
//   yang disimpan XOR-encoded di native (tidak terlihat di bytecode).
//
// ─── XOR-encoded checksum mask ────────────────────────────────────────────
// Mask asli: 0x1F  (Base36: karakter valid = nilai 0..35, 0x1F = 31)
// XOR key  : 0xA3
// Encoded  : 0x1F ^ 0xA3 = 0xBC
static constexpr uint8_t ENC_MASK    = 0xBC;
static constexpr uint8_t MASK_XORKEY = 0xA3;

// Minimum entropy: payload tidak boleh semua karakter sama
static bool hasEntropy(const std::string& payload) {
    char first = payload[0];
    for (char c : payload)
        if (c != first) return true;
    return false;
}

// Base36 decode nilai karakter (0-35), return -1 jika invalid
static int b36val(char c) {
    c = static_cast<char>(std::toupper((unsigned char)c));
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'A' && c <= 'Z') return c - 'A' + 10;
    return -1;
}

/**
 * Validasi format & checksum kode lisensi.
 * Return: true jika format valid dan checksum cocok.
 */
static bool validateLicenseFormat(const std::string& key) {
    // Panjang harus 19: XXXX-XXXX-XXXX-XXXX
    if (key.size() != 19) return false;

    // Posisi tanda '-'
    if (key[4] != '-' || key[9] != '-' || key[14] != '-') return false;

    // Ekstrak payload (16 karakter, tanpa '-')
    std::string payload;
    payload.reserve(16);
    for (size_t i = 0; i < key.size(); ++i) {
        if (key[i] == '-') continue;
        int v = b36val(key[i]);
        if (v < 0) return false;          // karakter bukan Base36
        payload += static_cast<char>(v);  // simpan nilai numeriknya
    }
    if (payload.size() != 16) return false;

    // Entropy check
    if (!hasEntropy(payload)) return false;

    // Checksum: XOR 15 byte pertama, bandingkan dengan byte ke-16
    // Decode mask dari native storage
    uint8_t mask = ENC_MASK ^ MASK_XORKEY;   // = 0x1F
    uint8_t xorSum = 0;
    for (size_t i = 0; i < 15; ++i)
        xorSum ^= static_cast<uint8_t>(payload[i]);
    xorSum &= mask;

    uint8_t checkByte = static_cast<uint8_t>(payload[15]) & mask;
    return xorSum == checkByte;
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
