package com.aether.lv.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AdsManager"

object AdsManager {

    // ── State flows ────────────────────────────────────────────────────────────

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _interstitialReady = MutableStateFlow(false)
    val interstitialReady: StateFlow<Boolean> = _interstitialReady.asStateFlow()

    private val _rewardedReady = MutableStateFlow(false)
    val rewardedReady: StateFlow<Boolean> = _rewardedReady.asStateFlow()

    private val _isAdShowing = MutableStateFlow(false)
    val isAdShowing: StateFlow<Boolean> = _isAdShowing.asStateFlow()

    // ── Unit IDs dari native layer (XOR-encoded di liblv.so) ──────────────────

    val GAME_ID: String by lazy { AdsNative.getGameId() }
    val INTERSTITIAL_UNIT_ID: String by lazy { AdsNative.getInterstitialUnitId() }
    val REWARDED_UNIT_ID: String by lazy { AdsNative.getRewardedUnitId() }

    // ── Internal state ─────────────────────────────────────────────────────────

    @Volatile private var appContext: Context? = null

    // Pending load flags — diset saat init belum selesai
    @Volatile private var pendingInterstitialLoad = false
    @Volatile private var pendingRewardedLoad = false

    // ── Init ───────────────────────────────────────────────────────────────────

    /**
     * Inisialisasi Unity Ads SDK.
     * Idempotent — aman dipanggil berkali-kali (dari Application dan MainActivity).
     * Dipanggil dari Application.onCreate() dengan testMode = BuildConfig.DEBUG.
     */
    fun initialize(context: Context, testMode: Boolean = false) {
        appContext = context.applicationContext

        if (_isInitialized.value) {
            return
        }

      
        UnityAds.initialize(
            context.applicationContext,
            GAME_ID,
            testMode,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    _isInitialized.value = true

                    // Eksekusi pending loads
                    if (pendingInterstitialLoad) {
                        pendingInterstitialLoad = false
                        loadInterstitialInternal()
                    }
                    if (pendingRewardedLoad) {
                        pendingRewardedLoad = false
                        loadRewardedInternal()
                    }
                }

                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError?,
                    message: String?
                ) {
                    _isInitialized.value = false
                    pendingInterstitialLoad = false
                    pendingRewardedLoad = false
                }
            }
        )
    }

    // ── Interstitial ───────────────────────────────────────────────────────────

    /**
     * Minta load interstitial.
     * Jika SDK belum init → set pending flag, load otomatis setelah init selesai.
     */
    fun loadInterstitial(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        if (!_isInitialized.value) {
            pendingInterstitialLoad = true
            return
        }
        loadInterstitialInternal()
    }

    fun loadInterstitial(activity: Activity) = loadInterstitial(activity as Context)

    private fun loadInterstitialInternal() {
        if (_interstitialReady.value) {
            return
        }
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
                Log.e(TAG, "Interstitial load failed: $error — $message")
                _interstitialReady.value = false
            }
        })
    }

    /**
     * Tampilkan interstitial.
     * onComplete dipanggil saat iklan selesai/ditutup.
     * onFailed dipanggil jika iklan belum siap atau gagal show.
     * Setelah show selesai/gagal, load berikutnya otomatis dimulai.
     */
    fun showInterstitial(
        activity: Activity,
        onComplete: () -> Unit = {},
        onFailed: (String) -> Unit = {}
    ) {
        if (!_isInitialized.value) {
            Log.w(TAG, "showInterstitial: SDK belum init")
            onFailed("SDK belum siap")
            return
        }
        if (!_interstitialReady.value) {
            Log.w(TAG, "showInterstitial: Ad belum ready")
            onFailed("Ad belum siap")
            return
        }

        _interstitialReady.value = false
        _isAdShowing.value = true
        Log.d(TAG, "Showing interstitial: $INTERSTITIAL_UNIT_ID")

        UnityAds.show(
            activity,
            INTERSTITIAL_UNIT_ID,
            UnityAdsShowOptions(),
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowStart(placementId: String) {
                    _isAdShowing.value = true
                    Log.d(TAG, "Interstitial show start")
                }

                override fun onUnityAdsShowClick(placementId: String) {
                    Log.d(TAG, "Interstitial clicked")
                }

                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState?
                ) {
                    Log.d(TAG, "Interstitial complete: state=$state")
                    _isAdShowing.value = false
                    loadInterstitialInternal()   // preload berikutnya
                    onComplete()
                }

                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError?,
                    message: String?
                ) {
                    Log.e(TAG, "Interstitial show failed: $error — $message")
                    _isAdShowing.value = false
                    loadInterstitialInternal()   // coba reload
                    onFailed(message ?: "Unknown error")
                }
            }
        )
    }

    // ── Rewarded ───────────────────────────────────────────────────────────────

    /**
     * Minta load rewarded ad.
     * Jika SDK belum init → set pending flag.
     */
    fun loadRewarded(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        if (!_isInitialized.value) {
            Log.d(TAG, "Rewarded: SDK belum init — set pending")
            pendingRewardedLoad = true
            return
        }
        loadRewardedInternal()
    }

    fun loadRewarded(activity: Activity) = loadRewarded(activity as Context)

    private fun loadRewardedInternal() {
        if (_rewardedReady.value) {
            Log.d(TAG, "Rewarded already ready — skip")
            return
        }
        Log.d(TAG, "Loading rewarded: $REWARDED_UNIT_ID")
        UnityAds.load(REWARDED_UNIT_ID, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String) {
                Log.d(TAG, "Rewarded loaded: $placementId")
                _rewardedReady.value = true
            }

            override fun onUnityAdsFailedToLoad(
                placementId: String,
                error: UnityAds.UnityAdsLoadError?,
                message: String?
            ) {
                Log.e(TAG, "Rewarded load failed: $error — $message")
                _rewardedReady.value = false
            }
        })
    }

    /**
     * Tampilkan rewarded ad.
     * onRewarded dipanggil jika user menonton sampai selesai (COMPLETED).
     * onComplete dipanggil di semua kasus (selesai/skip/failed).
     * onFailed dipanggil jika belum siap atau show error.
     */
    fun showRewarded(
        activity: Activity,
        onRewarded: () -> Unit = {},
        onComplete: () -> Unit = {},
        onFailed: (String) -> Unit = {}
    ) {
        if (!_isInitialized.value) {
            Log.w(TAG, "showRewarded: SDK belum init")
            onFailed("SDK belum siap")
            return
        }
        if (!_rewardedReady.value) {
            Log.w(TAG, "showRewarded: Ad belum ready")
            onFailed("Ad belum siap")
            return
        }

        _rewardedReady.value = false
        _isAdShowing.value = true
        Log.d(TAG, "Showing rewarded: $REWARDED_UNIT_ID")

        UnityAds.show(
            activity,
            REWARDED_UNIT_ID,
            UnityAdsShowOptions(),
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowStart(placementId: String) {
                    _isAdShowing.value = true
                    Log.d(TAG, "Rewarded show start")
                }

                override fun onUnityAdsShowClick(placementId: String) {
                    Log.d(TAG, "Rewarded clicked")
                }

                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState?
                ) {
                    Log.d(TAG, "Rewarded complete: state=$state")
                    _isAdShowing.value = false
                    loadRewardedInternal()  // preload berikutnya
                    // Beri reward hanya jika user menonton sampai habis
                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                        onRewarded()
                    }
                    onComplete()
                }

                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError?,
                    message: String?
                ) {
                    Log.e(TAG, "Rewarded show failed: $error — $message")
                    _isAdShowing.value = false
                    loadRewardedInternal()  // coba reload
                    onFailed(message ?: "Unknown error")
                }
            }
        )
    }
}
