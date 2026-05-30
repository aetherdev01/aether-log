package com.aether.lv.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.ads.metadata.MetaData
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.IUnityBannerListener
import com.unity3d.services.banners.UnityBannerPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AdsManager"

object AdsManager {

    private val _isInitialized   = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _interstitialReady = MutableStateFlow(false)
    val interstitialReady: StateFlow<Boolean> = _interstitialReady.asStateFlow()

    private val _bannerReady = MutableStateFlow(false)
    val bannerReady: StateFlow<Boolean> = _bannerReady.asStateFlow()

    val GAME_ID: String              by lazy { AdsNative.getGameId() }
    val BANNER_UNIT_ID: String       by lazy { AdsNative.getBannerUnitId() }
    val INTERSTITIAL_UNIT_ID: String by lazy { AdsNative.getInterstitialUnitId() }

    fun initialize(context: Context, testMode: Boolean = false) {
        if (_isInitialized.value) {
            Log.d(TAG, "Already initialized, skip")
            return
        }

        Log.d(TAG, "Initializing Unity Ads | gameId=$GAME_ID | testMode=$testMode")

        MetaData(context).apply { set("gdpr.consent", true); commit() }
        MetaData(context).apply { set("privacy.consent", true); commit() }

        UnityAds.initialize(
            context,
            GAME_ID,
            testMode,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    Log.d(TAG, "Unity Ads initialized successfully")
                    _isInitialized.value = true
                }

                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError?,
                    message: String?
                ) {
                    Log.e(TAG, "Unity Ads init failed: $error — $message")
                    _isInitialized.value = false
                }
            }
        )
    }

    fun loadInterstitial(activity: Activity) {
        if (!_isInitialized.value) {
            Log.w(TAG, "loadInterstitial: SDK belum initialized")
            return
        }
        _interstitialReady.value = false
        Log.d(TAG, "Loading interstitial: $INTERSTITIAL_UNIT_ID")

        UnityAds.load(INTERSTITIAL_UNIT_ID, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String) {
                Log.d(TAG, "Interstitial loaded: $placementId")
                _interstitialReady.value = true
            }

            override fun onUnityAdsFailedToLoad(
                placementId: String,
                error: UnityAds.UnityAdsLoadError?,
                message: String?
            ) {
                Log.e(TAG, "Interstitial load failed: $placementId | $error | $message")
                _interstitialReady.value = false
            }
        })
    }

    fun showInterstitial(
        activity  : Activity,
        onComplete: () -> Unit = {},
        onFailed  : (String) -> Unit = {}
    ) {
        if (!_interstitialReady.value) {
            Log.w(TAG, "showInterstitial: ad belum ready, skip")
            onFailed("Ad belum siap")
            return
        }

        Log.d(TAG, "Showing interstitial: $INTERSTITIAL_UNIT_ID")
        _interstitialReady.value = false

        UnityAds.show(
            activity,
            INTERSTITIAL_UNIT_ID,
            UnityAdsShowOptions(),
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowStart(placementId: String) {
                    Log.d(TAG, "Interstitial show start: $placementId")
                }

                override fun onUnityAdsShowClick(placementId: String) {
                    Log.d(TAG, "Interstitial clicked: $placementId")
                }

                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState?
                ) {
                    Log.d(TAG, "Interstitial complete: $placementId | state=$state")
                    onComplete()
                }

                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError?,
                    message: String?
                ) {
                    Log.e(TAG, "Interstitial show failed: $error | $message")
                    onFailed(message ?: "Unknown error")
                }
            }
        )
    }

    fun loadBannerIntoView(
        activity  : Activity,
        container : android.view.ViewGroup,
        onLoaded  : () -> Unit = {},
        onFailed  : (String) -> Unit = {}
    ) {
        if (!_isInitialized.value) {
            Log.w(TAG, "loadBanner: SDK belum initialized")
            return
        }

        try {
            val bannerView = BannerView(
                activity,
                BANNER_UNIT_ID,
                UnityBannerPosition.BOTTOM_CENTER
            )

            bannerView.listener = object : IUnityBannerListener {
                override fun onBannerLoaded(bannerAdView: BannerView) {
                    Log.d(TAG, "Banner loaded: $BANNER_UNIT_ID")
                    _bannerReady.value = true
                    onLoaded()
                }

                override fun onBannerFailedToLoad(
                    bannerAdView: BannerView,
                    errorInfo: BannerErrorInfo
                ) {
                    Log.e(TAG, "Banner load failed: ${errorInfo.errorMessage}")
                    _bannerReady.value = false
                    onFailed(errorInfo.errorMessage ?: "Unknown error")
                }

                override fun onBannerClick(bannerAdView: BannerView) {
                    Log.d(TAG, "Banner clicked")
                }

                override fun onBannerLeftApplication(bannerAdView: BannerView) {
                    Log.d(TAG, "Banner left app")
                }

                override fun onBannerShown(bannerAdView: BannerView) {
                    Log.d(TAG, "Banner shown")
                }
            }

            container.removeAllViews()
            container.addView(bannerView)
            bannerView.load()

        } catch (e: Exception) {
            Log.e(TAG, "loadBannerIntoView error: ${e.message}")
            onFailed(e.message ?: "Exception")
        }
    }

    fun destroyBanner(container: android.view.ViewGroup?) {
        try {
            (container?.getChildAt(0) as? BannerView)?.destroy()
            container?.removeAllViews()
        } catch (_: Exception) { }
        _bannerReady.value = false
    }
}
