package com.aether.lv.ads

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

private val Context.rewardedNoAdsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "rewarded_no_ads"
)

private const val TAG = "RewardedNoAds"

private fun startOfTodayMillis(timeMillis: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

data class RewardedNoAdsState(
    val noAdsUntil: Long = 0L,
    val rewardClaimsToday: Int = 0,
    val claimDayStartMillis: Long = 0L
) {
    val isActive: Boolean
        get() = System.currentTimeMillis() < noAdsUntil

    val remainingMillis: Long
        get() = (noAdsUntil - System.currentTimeMillis()).coerceAtLeast(0L)

    val canWatchRewarded: Boolean
        get() {
            val today = startOfTodayMillis()
            return claimDayStartMillis != today || rewardClaimsToday < RewardedNoAdsManager.DAILY_LIMIT
        }

    val remainingClaims: Int
        get() {
            val today = startOfTodayMillis()
            return if (claimDayStartMillis != today) RewardedNoAdsManager.DAILY_LIMIT
            else (RewardedNoAdsManager.DAILY_LIMIT - rewardClaimsToday).coerceAtLeast(0)
        }
}

object RewardedNoAdsManager {

    const val DAILY_LIMIT = 2
    private const val NO_ADS_DURATION_MS = 30L * 60L * 1000L

    private object PrefKeys {
        val NO_ADS_UNTIL = longPreferencesKey("no_ads_until")
        val CLAIM_COUNT  = longPreferencesKey("reward_claim_count")
        val CLAIM_DAY    = longPreferencesKey("reward_claim_day")
    }

    @Volatile private var appContext: Context? = null
    @Volatile private var initialized = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(RewardedNoAdsState())
    val state: StateFlow<RewardedNoAdsState> = _state.asStateFlow()

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (initialized) return
        initialized = true

        scope.launch {
            context.rewardedNoAdsDataStore.data
                .catch { e ->
                    Log.e(TAG, "DataStore read failed: ${e.message}")
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                }
                .collectLatest { prefs ->
                    _state.value = prefs.toState()
                }
        }
    }

    fun canWatchRewarded(): Boolean = _state.value.canWatchRewarded

    fun isNoAdsActive(): Boolean = _state.value.isActive

    fun remainingMillis(): Long = _state.value.remainingMillis

    fun remainingClaimsToday(): Int = _state.value.remainingClaims

    /**
     * Dipanggil saat rewarded ad berhasil ditonton sampai selesai.
     * Mengembalikan false jika limit harian sudah habis.
     */
    fun grant30Minutes(): Boolean {
        val current = _state.value
        if (!current.canWatchRewarded) {
            Log.d(TAG, "Grant skipped — daily limit reached")
            return false
        }

        val now         = System.currentTimeMillis()
        val startToday   = startOfTodayMillis(now)
        val baseUntil    = maxOf(current.noAdsUntil, now)
        val newUntil     = baseUntil + NO_ADS_DURATION_MS
        val newClaimCount = if (current.claimDayStartMillis == startToday) {
            current.rewardClaimsToday + 1
        } else {
            1
        }

        val updated = RewardedNoAdsState(
            noAdsUntil          = newUntil,
            rewardClaimsToday   = newClaimCount,
            claimDayStartMillis = startToday
        )
        _state.value = updated

        val context = appContext
        if (context == null) {
            Log.w(TAG, "App context not ready — in-memory grant only")
            return true
        }

        scope.launch {
            context.rewardedNoAdsDataStore.edit { prefs ->
                prefs[PrefKeys.NO_ADS_UNTIL] = newUntil
                prefs[PrefKeys.CLAIM_COUNT]  = newClaimCount.toLong()
                prefs[PrefKeys.CLAIM_DAY]    = startToday
            }
        }

        return true
    }

    private fun Preferences.toState(): RewardedNoAdsState {
        val now       = System.currentTimeMillis()
        val today     = startOfTodayMillis(now)
        val storedDay = this[PrefKeys.CLAIM_DAY] ?: 0L
        val count     = if (storedDay == today) {
            (this[PrefKeys.CLAIM_COUNT] ?: 0L).toInt()
        } else {
            0
        }

        return RewardedNoAdsState(
            noAdsUntil          = this[PrefKeys.NO_ADS_UNTIL] ?: 0L,
            rewardClaimsToday   = count,
            claimDayStartMillis = if (storedDay == today) storedDay else today
        )
    }

}
