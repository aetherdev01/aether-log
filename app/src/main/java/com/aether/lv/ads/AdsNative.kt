package com.aether.lv.ads

/**
 * JNI bridge ke libloglog_ads.so (ads_config.cpp).
 *
 * Game ID dan Ad Unit IDs disimpan di native layer (XOR-encoded)
 * untuk menghindari plain-text extraction dari APK resources / BuildConfig.
 *
 * Semua fungsi adalah @JvmStatic karena dipanggil dari companion object
 * maupun Java interop.
 */
object AdsNative {

    init {
        System.loadLibrary("loglog_ads")
    }

    /** Mengembalikan Unity Ads Game ID: "6091240" */
    @JvmStatic external fun getGameId(): String

    /** Mengembalikan Banner Ad Unit ID: "Banner" */
    @JvmStatic external fun getBannerUnitId(): String

    /** Mengembalikan Interstitial Ad Unit ID: "Interstitial" */
    @JvmStatic external fun getInterstitialUnitId(): String
}
