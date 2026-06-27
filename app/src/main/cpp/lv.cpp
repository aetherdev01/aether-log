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
// Game ID: "6091240"  (7 chars)
//  '6'^'L'=0x7A, '0'^'O'=0x7F, '9'^'G'=0x7E, '1'^'X'=0x69,
//  '2'^'A'=0x73, '4'^'D'=0x70, '0'^'L'=0x7C
static const uint8_t ENC_GAME_ID[] = {
    0x7A, 0x7F, 0x7E, 0x69, 0x73, 0x70, 0x7C
};

// Ad Unit: "Interstitial_Android"  (20 chars)
// 'I'^'L'=0x05,'n'^'O'=0x21,'t'^'G'=0x33,'e'^'X'=0x3D,'r'^'A'=0x33,'s'^'D'=0x37,
// 't'^'L'=0x38,'i'^'O'=0x26,'t'^'G'=0x33,'i'^'X'=0x31,'a'^'A'=0x20,'l'^'D'=0x28,
// '_'^'L'=0x13,'A'^'O'=0x0E,'n'^'G'=0x29,'d'^'X'=0x3C,'r'^'A'=0x33,'o'^'D'=0x2B,
// 'i'^'L'=0x25,'d'^'O'=0x2B
static const uint8_t ENC_UNIT_INTERSTITIAL[] = {
    0x05, 0x21, 0x33, 0x3D, 0x33, 0x37,
    0x38, 0x26, 0x33, 0x31, 0x20, 0x28,
    0x13, 0x0E, 0x29, 0x3C, 0x33, 0x2B,
    0x25, 0x2B
};

// Ad Unit: "Rewarded_Android"  (16 chars)
// 'R'^'L'=0x1E,'e'^'O'=0x2A,'w'^'G'=0x30,'a'^'X'=0x39,'r'^'A'=0x33,'d'^'D'=0x20,
// 'e'^'L'=0x29,'d'^'O'=0x2B,'_'^'G'=0x18,'A'^'X'=0x19,'n'^'A'=0x2F,'d'^'D'=0x20,
// 'r'^'L'=0x3E,'o'^'O'=0x20,'i'^'G'=0x2E,'d'^'X'=0x3C
static const uint8_t ENC_UNIT_REWARDED[] = {
    0x1E, 0x2A, 0x30, 0x39, 0x33, 0x20,
    0x29, 0x2B, 0x18, 0x19, 0x2F, 0x20,
    0x3E, 0x20, 0x2E, 0x3C
};

// ─── JNI exports ──────────────────────────────────────────────────────────────
extern "C" {

JNIEXPORT jstring JNICALL
Java_com_aether_lv_ads_AdsNative_getGameId(JNIEnv* env, jclass /*clazz*/) {
    std::string id = xorDecode(ENC_GAME_ID, sizeof(ENC_GAME_ID));
    return env->NewStringUTF(id.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_aether_lv_ads_AdsNative_getInterstitialUnitId(JNIEnv* env, jclass /*clazz*/) {
    std::string id = xorDecode(ENC_UNIT_INTERSTITIAL, sizeof(ENC_UNIT_INTERSTITIAL));
    return env->NewStringUTF(id.c_str()); // "Interstitial_Android"
}

JNIEXPORT jstring JNICALL
Java_com_aether_lv_ads_AdsNative_getRewardedUnitId(JNIEnv* env, jclass /*clazz*/) {
    std::string id = xorDecode(ENC_UNIT_REWARDED, sizeof(ENC_UNIT_REWARDED));
    return env->NewStringUTF(id.c_str()); // "Rewarded_Android"
}

} // extern "C"
