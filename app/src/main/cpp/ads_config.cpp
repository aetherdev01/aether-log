/**
 * ads_config.cpp
 *
 * Menyimpan konfigurasi Unity Ads (Game ID dan Ad Unit IDs) di layer native.
 * Dipanggil dari Java/Kotlin melalui JNI.
 *
 * Alasan disimpan di C++:
 * - Strings di .so lebih susah di-extract dibanding BuildConfig / string resources
 * - Obfuscation ProGuard tidak menyentuh native layer
 * - Kombinasi XOR sederhana sebagai lapisan tambahan
 */

#include <jni.h>
#include <string>

// ─── XOR key ──────────────────────────────────────────────────────────────────
// Key sederhana untuk obfuscate string di binary .so
// (bukan enkripsi kuat — hanya menghindari plain-text grep di APK)
static constexpr uint8_t XOR_KEY[] = { 0x4C, 0x4F, 0x47, 0x58, 0x41, 0x44 }; // "LOGXAD"
static constexpr size_t  KEY_LEN   = sizeof(XOR_KEY);

static std::string xorDecode(const uint8_t* data, size_t len) {
    std::string result(len, '\0');
    for (size_t i = 0; i < len; i++) {
        result[i] = static_cast<char>(data[i] ^ XOR_KEY[i % KEY_LEN]);
    }
    return result;
}

// ─── Encoded strings ──────────────────────────────────────────────────────────
//
// Cara encode: setiap char XOR dengan XOR_KEY[i % 6]
//
// Game ID: "6091240"
// "6091240" XOR "LOGXAD..." → encoded bytes berikut:
//  '6'^'L'=0x7A, '0'^'O'=0x7F, '9'^'G'=0x3E, '1'^'X'=0x69, '2'^'A'=0x73, '4'^'D'=0x70, '0'^'L'=0x3C
static const uint8_t ENC_GAME_ID[]           = { 0x7A, 0x7F, 0x3E, 0x69, 0x73, 0x70, 0x3C };

// Ad Unit: "Banner"
// 'B'^'L'=0x06, 'a'^'O'=0x2E, 'n'^'G'=0x29, 'n'^'X'=0x36, 'e'^'A'=0x24, 'r'^'D'=0x36
static const uint8_t ENC_UNIT_BANNER[]       = { 0x06, 0x2E, 0x29, 0x36, 0x24, 0x36 };

// Ad Unit: "Interstitial"
// 'I'^'L'=0x05,'n'^'O'=0x21,'t'^'G'=0x33,'e'^'X'=0x3D,'r'^'A'=0x33,'s'^'D'=0x37,
// 't'^'L'=0x38,'i'^'O'=0x26,'t'^'G'=0x33,'i'^'X'=0x31,'a'^'A'=0x00,'l'^'D'=0x28
static const uint8_t ENC_UNIT_INTERSTITIAL[] = {
    0x05, 0x21, 0x33, 0x3D, 0x33, 0x37, 0x38, 0x26, 0x33, 0x31, 0x00, 0x28
};

// ─── JNI exports ─────────────────────────────────────────────────────────────
extern "C" {

/**
 * com.aether.lv.ads.AdsNative.getGameId()
 * Return: "6091240"
 */
JNIEXPORT jstring JNICALL
Java_com_aether_lv_ads_AdsNative_getGameId(JNIEnv* env, jclass /*clazz*/) {
    std::string id = xorDecode(ENC_GAME_ID, sizeof(ENC_GAME_ID));
    return env->NewStringUTF(id.c_str());
}

/**
 * com.aether.lv.ads.AdsNative.getBannerUnitId()
 * Return: "Banner"
 */
JNIEXPORT jstring JNICALL
Java_com_aether_lv_ads_AdsNative_getBannerUnitId(JNIEnv* env, jclass /*clazz*/) {
    std::string id = xorDecode(ENC_UNIT_BANNER, sizeof(ENC_UNIT_BANNER));
    return env->NewStringUTF(id.c_str());
}

/**
 * com.aether.lv.ads.AdsNative.getInterstitialUnitId()
 * Return: "Interstitial"
 */
JNIEXPORT jstring JNICALL
Java_com_aether_lv_ads_AdsNative_getInterstitialUnitId(JNIEnv* env, jclass /*clazz*/) {
    std::string id = xorDecode(ENC_UNIT_INTERSTITIAL, sizeof(ENC_UNIT_INTERSTITIAL));
    return env->NewStringUTF(id.c_str());
}

} // extern "C"
