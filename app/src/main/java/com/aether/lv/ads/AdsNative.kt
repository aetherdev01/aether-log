package com.aether.lv.ads

/**
 * JNI bridge ke liblv.so (lv.cpp).
 *
 * Game ID dan Ad Unit IDs disimpan di native layer (XOR-encoded)
 * untuk menghindari plain-text extraction dari APK resources / BuildConfig.
 */
object AdsNative {

    init {
        System.loadLibrary("lv")
    }

    /** Mengembalikan Unity Ads Game ID: "6091240" */
    @JvmStatic external fun getGameId(): String

    /** Mengembalikan Interstitial Ad Unit ID: "Interstitial_Android" */
    @JvmStatic external fun getInterstitialUnitId(): String

    /** Mengembalikan Rewarded Ad Unit ID: "Rewarded_Android" */
    @JvmStatic external fun getRewardedUnitId(): String
}
