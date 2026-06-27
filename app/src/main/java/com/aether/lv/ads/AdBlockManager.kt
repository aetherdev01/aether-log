package com.aether.lv.ads

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "AdBlockManager"

/**
 * Orchestrator untuk AdBlock detection & state management.
 *
 * Siklus deteksi:
 * 1. App dibuka → tunggu grace period (12 detik) agar SDK ada cukup waktu init
 * 2. Jalankan AdBlockDetector.detect()
 * 3. Jika BLOCKED → tampilkan dialog (via state flow)
 * 4. User klik "Sudah Dinonaktifkan" → re-check setelah 3 detik
 * 5. Jika masih blocked → tampilkan dialog lagi
 * 6. Jika clean → lanjutkan normal
 *
 * FIX: Guard `startDetection` sekarang hanya cek `isDetecting` —
 * tidak lagi skip jika `isBlocked`. Hal ini penting agar re-check
 * dari onUserClaimedDisabled() berjalan benar.
 *
 * FIX: `detectionStarted` flag mencegah double-fire di onCreate,
 * tapi reset saat `dismiss()` agar bisa di-retrigger jika perlu.
 */
object AdBlockManager {

    data class AdBlockState(
        val isDetecting  : Boolean = false,
        val isBlocked    : Boolean = false,
        val signals      : List<AdBlockDetector.BlockSignal> = emptyList(),
        val confidence   : Int = 0,
        val recheckCount : Int = 0,
    )

    private val _state = MutableStateFlow(AdBlockState())
    val state: StateFlow<AdBlockState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Flag: deteksi pertama sudah dijadwalkan (cegah double-fire)
    @Volatile private var detectionStarted = false

    private const val INITIAL_GRACE_MS = 5_000L
    private const val RECHECK_DELAY_MS = 3_000L
    private const val MAX_RECHECKS     = 5

    /**
     * Mulai deteksi AdBlock.
     * Panggil dari MainActivity.onCreate().
     * Idempotent — tidak akan double-detect berkat [detectionStarted] flag.
     *
     * @param skipForPremium jika true (user sudah punya lisensi no_ads aktif),
     *        deteksi tidak dijalankan sama sekali — dialog AdBlock hanya relevan
     *        untuk user yang seharusnya melihat iklan tapi memblokirnya secara diam-diam.
     */
    fun startDetection(context: Context, skipForPremium: Boolean = false) {
        if (skipForPremium) {
            Log.d(TAG, "Skip AdBlock detection — premium license active")
            return
        }
        if (detectionStarted) {
            Log.d(TAG, "Detection already scheduled, skip")
            return
        }
        if (_state.value.isDetecting) {
            Log.d(TAG, "Detection coroutine running, skip")
            return
        }

        detectionStarted = true
        Log.d(TAG, "Scheduling AdBlock detection after ${INITIAL_GRACE_MS}ms grace period")
        _state.value = AdBlockState(isDetecting = false)

        scope.launch(Dispatchers.IO) {
            delay(INITIAL_GRACE_MS)
            runDetection(context, recheckCount = 0)
        }
    }

    /**
     * Dipanggil saat user klaim sudah menonaktifkan AdBlock.
     * Re-check setelah delay singkat.
     */
    fun onUserClaimedDisabled(context: Context) {
        val currentCount = _state.value.recheckCount
        if (currentCount >= MAX_RECHECKS) {
            Log.w(TAG, "Max rechecks ($MAX_RECHECKS) reached — clearing block state")
            _state.value = AdBlockState()
            return
        }

        Log.d(TAG, "User claimed AdBlock disabled — re-checking in ${RECHECK_DELAY_MS}ms")
        _state.value = _state.value.copy(isDetecting = true, isBlocked = false)

        scope.launch(Dispatchers.IO) {
            delay(RECHECK_DELAY_MS)
            runDetection(context, recheckCount = currentCount + 1)
        }
    }

    /**
     * Dismiss dialog tanpa re-check.
     * Reset state sepenuhnya, termasuk [detectionStarted]
     * agar bisa di-restart jika dibutuhkan.
     */
    fun dismiss() {
        _state.value      = AdBlockState()
        detectionStarted  = false
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private suspend fun runDetection(context: Context, recheckCount: Int) {
        _state.value = _state.value.copy(isDetecting = true)
        Log.d(TAG, "Running detection (attempt ${recheckCount + 1}/${MAX_RECHECKS + 1})")

        val result = try {
            AdBlockDetector.detect(context)
        } catch (e: Exception) {
            Log.e(TAG, "Detection exception: ${e.message}")
            AdBlockDetector.DetectionResult.Clean // Fail safe
        }

        when (result) {
            is AdBlockDetector.DetectionResult.Clean -> {
                Log.d(TAG, "Detection result: CLEAN")
                _state.value = AdBlockState()
            }
            is AdBlockDetector.DetectionResult.NoInternet -> {
                Log.d(TAG, "Detection result: NO INTERNET — skip")
                _state.value = AdBlockState()
            }
            is AdBlockDetector.DetectionResult.Blocked -> {
                Log.w(TAG, "Detection result: BLOCKED — confidence=${result.confidence}%")
                _state.value = AdBlockState(
                    isDetecting  = false,
                    isBlocked    = true,
                    signals      = result.signals,
                    confidence   = result.confidence,
                    recheckCount = recheckCount,
                )
            }
        }
    }
}
