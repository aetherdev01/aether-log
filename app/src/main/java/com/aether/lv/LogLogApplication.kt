package com.aether.lv

import android.app.Application
import com.aether.lv.ads.AdsManager
import com.aether.lv.ads.RewardedNoAdsManager
import com.aether.lv.data.db.LogLogDatabase

class LogLogApplication : Application() {

    val database: LogLogDatabase by lazy { LogLogDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()

        // Inisialisasi Unity Ads SDK.
        // testMode = BuildConfig.DEBUG agar dev mode otomatis pakai test ads.
        AdsManager.initialize(
            context  = this,
            testMode = BuildConfig.DEBUG
        )
        RewardedNoAdsManager.initialize(this)

        // Preload interstitial dan rewarded sejak awal.
        // Jika init belum selesai, AdsManager akan set pending flag
        // dan load otomatis setelah onInitializationComplete.
        AdsManager.loadInterstitial(this)
        AdsManager.loadRewarded(this)
    }
}
