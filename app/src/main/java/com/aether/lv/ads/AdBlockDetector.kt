package com.aether.lv.ads

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.UnknownHostException
import java.net.URL

private const val TAG = "AdBlockDetector"

/**
 * AdBlock Detection Engine untuk Unity Ads.
 *
 * Fix false positive SDK_INIT_TIMEOUT:
 *  - SDK_INIT_TIMEOUT & SDK_LOAD_TIMEOUT DIHAPUS dari STRONG_SIGNALS
 *    → SDK sinyal tidak pernah bisa berdiri sendiri sebagai verdict
 *  - SDK_WAIT_MS dinaikkan ke 15 s agar cold start Unity Ads bisa selesai
 *  - SDK_INIT_TIMEOUT hanya count jika ada ≥1 sinyal DNS/HTTP juga
 *  - SDK_LOAD_TIMEOUT hanya count jika ada ≥1 sinyal DNS/HTTP juga
 *
 * Logika verdict (dari paling ketat ke paling longgar):
 *  1. DNS_FAST_FAIL / DNS_SINKHOLE / HTTP_UNREACHABLE / HTTP_WRONG_RESPONSE
 *     → sinyal KUAT, langsung BLOCKED (threshold = 1)
 *  2. DNS_NXDOMAIN ≥ 2 domain → BLOCKED
 *  3. SDK_INIT_TIMEOUT + (DNS_NXDOMAIN ≥1 atau HTTP_TIMEOUT) → BLOCKED
 *  4. SDK_LOAD_TIMEOUT + (DNS_NXDOMAIN ≥1 atau HTTP_TIMEOUT) → BLOCKED
 *  5. HTTP_TIMEOUT sendirian / SDK sinyal sendirian → CLEAN (false positive guard)
 */
object AdBlockDetector {

    private val AD_PROBE_DOMAINS = listOf(
        "config.unityads.unity3d.com",
        "auction.unityads.unity3d.com",
        "publisher-config.unityads.unity3d.com",
        "cdp.cloud.unity3d.com",
        "unityads.unity3d.com",
    )

    private val CONTROL_DOMAINS = listOf(
        "github.com",
        "apple.com",
        "microsoft.com",
        "www.wikipedia.org",
    )

    private const val HTTP_PROBE_URL  = "https://config.unityads.unity3d.com/webview/4/config.json"

    private const val DNS_TIMEOUT_MS  = 2_000L
    private const val HTTP_TIMEOUT_MS = 4_000L
    private const val SDK_WAIT_MS     = 15_000L  // naik dari 6s — Unity Ads cold start butuh waktu

    // Sinyal KUAT — DNS/HTTP layer saja, bukan SDK
    // SDK_INIT_TIMEOUT & SDK_LOAD_TIMEOUT sengaja TIDAK masuk sini
    private val STRONG_SIGNALS = setOf(
        SignalSource.DNS_FAST_FAIL,
        SignalSource.DNS_SINKHOLE,
        SignalSource.HTTP_UNREACHABLE,
        SignalSource.HTTP_WRONG_RESPONSE,
    )

    private val HARD_SINKHOLE_IPS = listOf("0.0.0.0", "127.0.0.1", "::1")

    // ── Public API ─────────────────────────────────────────────────────────────

    sealed class DetectionResult {
        object Clean      : DetectionResult()
        data class Blocked(val signals: List<BlockSignal>, val confidence: Int) : DetectionResult()
        object NoInternet : DetectionResult()
    }

    data class BlockSignal(
        val source     : SignalSource,
        val description: String,
        val timingMs   : Long = 0L,
    )

    enum class SignalSource {
        DNS_NXDOMAIN,
        DNS_SINKHOLE,
        DNS_FAST_FAIL,
        HTTP_UNREACHABLE,
        HTTP_TIMEOUT,
        HTTP_WRONG_RESPONSE,
        SDK_INIT_TIMEOUT,
        SDK_LOAD_TIMEOUT,
    }

    suspend fun detect(context: Context): DetectionResult = withContext(Dispatchers.IO) {
        if (!hasInternetConnection(context)) {
            Log.d(TAG, "No internet — skip detection")
            return@withContext DetectionResult.NoInternet
        }

        if (!verifyControlDomains()) {
            Log.d(TAG, "Control domain failed — network issue, skip")
            return@withContext DetectionResult.NoInternet
        }

        val signals = mutableListOf<BlockSignal>()

        coroutineScope {
            val dnsJob  = async { runDnsProbes() }
            val httpJob = async { runHttpProbe() }
            val sdkJob  = async { runSdkStateCheck() }

            signals.addAll(dnsJob.await())
            signals.addAll(httpJob.await())
            signals.addAll(sdkJob.await())
        }

        if (signals.isEmpty()) {
            Log.d(TAG, "No signals — CLEAN")
            return@withContext DetectionResult.Clean
        }

        val effectiveSignals = filterEffectiveSignals(signals)

        if (effectiveSignals.isEmpty()) {
            Log.d(TAG, "Signals present but not effective — CLEAN (false positive guard)")
            return@withContext DetectionResult.Clean
        }

        val confidence = calculateConfidence(effectiveSignals)
        Log.w(TAG, "AdBlock DETECTED — signals=${effectiveSignals.size}, confidence=$confidence%")
        effectiveSignals.forEach { Log.w(TAG, "  ${it.source} | ${it.description} | ${it.timingMs}ms") }

        DetectionResult.Blocked(signals = effectiveSignals, confidence = confidence)
    }

    /**
     * Filter sinyal mana yang efektif untuk verdict BLOCKED.
     *
     * Rule (urutan prioritas):
     * 1. Ada sinyal KUAT (DNS/HTTP layer) → semua sinyal count
     * 2. DNS_NXDOMAIN ≥ 2 → count (CDN maintenance bisa 1 domain, tapi jarang 2+)
     * 3. SDK_INIT_TIMEOUT + ada sinyal DNS atau HTTP apapun → count
     *    (SDK gagal init dikuatkan oleh DNS/HTTP yang juga bermasalah)
     * 4. SDK_LOAD_TIMEOUT + ada DNS_NXDOMAIN atau HTTP_TIMEOUT → count
     * 5. Semua kasus lain → emptyList() → CLEAN
     */
    private fun filterEffectiveSignals(signals: List<BlockSignal>): List<BlockSignal> {
        // Rule 1: ada sinyal DNS/HTTP kuat
        if (signals.any { it.source in STRONG_SIGNALS }) {
            return signals
        }

        val nxCount      = signals.count { it.source == SignalSource.DNS_NXDOMAIN }
        val hasHttpIssue = signals.any { it.source == SignalSource.HTTP_TIMEOUT ||
                                         it.source == SignalSource.HTTP_UNREACHABLE }
        val hasDnsIssue  = nxCount >= 1
        val hasSdkInit   = signals.any { it.source == SignalSource.SDK_INIT_TIMEOUT }
        val hasSdkLoad   = signals.any { it.source == SignalSource.SDK_LOAD_TIMEOUT }

        // Rule 2: banyak NXDOMAIN
        if (nxCount >= 2) {
            return signals
        }

        // Rule 3: SDK init gagal + ada DNS atau HTTP problem
        if (hasSdkInit && (hasDnsIssue || hasHttpIssue)) {
            return signals
        }

        // Rule 4: SDK load gagal + ada DNS atau HTTP timeout
        if (hasSdkLoad && (hasDnsIssue || hasHttpIssue)) {
            return signals
        }

        // Rule 5: sinyal lemah saja (HTTP_TIMEOUT sendirian, atau SDK sendirian)
        return emptyList()
    }

    // ── Control domain check ──────────────────────────────────────────────────

    private fun verifyControlDomains(): Boolean {
        for (domain in CONTROL_DOMAINS) {
            try {
                val addr = withTimeoutOrNullSync(DNS_TIMEOUT_MS) { InetAddress.getByName(domain) }
                if (addr != null) {
                    Log.d(TAG, "Control OK: $domain → ${addr.hostAddress}")
                    return true
                }
            } catch (_: Exception) {
                Log.d(TAG, "Control failed: $domain")
            }
        }
        return false
    }

    // ── DNS Probes ─────────────────────────────────────────────────────────────

    private suspend fun runDnsProbes(): List<BlockSignal> = withContext(Dispatchers.IO) {
        val result  = mutableListOf<BlockSignal>()
        var nxCount = 0

        for (domain in AD_PROBE_DOMAINS) {
            val signal = probeDns(domain)
            when {
                signal == null -> { /* clean */ }
                signal.source == SignalSource.DNS_NXDOMAIN -> nxCount++
                else -> {
                    result.add(signal)
                    if (signal.source in STRONG_SIGNALS) break  // stop early on strong signal
                }
            }
        }

        if (nxCount >= 2) {
            result.add(BlockSignal(
                source      = SignalSource.DNS_NXDOMAIN,
                description = "$nxCount/${AD_PROBE_DOMAINS.size} ad domains NXDOMAIN",
                timingMs    = 0L,
            ))
        } else if (nxCount == 1) {
            Log.d(TAG, "Only 1 NXDOMAIN — possible CDN issue, not counted")
        }

        result
    }

    private fun probeDns(domain: String): BlockSignal? {
        val startMs = System.currentTimeMillis()
        return try {
            val address = withTimeoutOrNullSync(DNS_TIMEOUT_MS) {
                InetAddress.getByName(domain)
            } ?: return null

            val elapsed = System.currentTimeMillis() - startMs
            val ip      = address.hostAddress ?: ""
            Log.d(TAG, "DNS $domain → $ip (${elapsed}ms)")

            if (HARD_SINKHOLE_IPS.any { ip == it || ip.startsWith(it) }) {
                val source = if (elapsed < 15L) SignalSource.DNS_FAST_FAIL else SignalSource.DNS_SINKHOLE
                return BlockSignal(source, "$domain → sinkhole $ip", elapsed)
            }

            val isPrivate = ip.startsWith("10.") || ip.startsWith("192.168.") ||
                            ip.startsWith("172.") || ip == "::1" ||
                            ip.startsWith("fd")   || ip.startsWith("fe80")
            if (isPrivate && elapsed < 50L) {
                return BlockSignal(SignalSource.DNS_SINKHOLE,
                    "$domain → private IP $ip (${elapsed}ms) — hosts file", elapsed)
            }

            null
        } catch (e: UnknownHostException) {
            val elapsed = System.currentTimeMillis() - startMs
            BlockSignal(SignalSource.DNS_NXDOMAIN, "NXDOMAIN: $domain", elapsed)
        } catch (_: Exception) {
            null
        }
    }

    // ── HTTP Probe ─────────────────────────────────────────────────────────────

    private suspend fun runHttpProbe(): List<BlockSignal> = withContext(Dispatchers.IO) {
        val result  = mutableListOf<BlockSignal>()
        val startMs = System.currentTimeMillis()

        try {
            val responseCode = withTimeoutOrNull(HTTP_TIMEOUT_MS) {
                val conn = URL(HTTP_PROBE_URL).openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod           = "GET"
                    connectTimeout          = HTTP_TIMEOUT_MS.toInt()
                    readTimeout             = HTTP_TIMEOUT_MS.toInt()
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "UnityAds/4.0")
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    conn.connect()
                    val code = conn.responseCode
                    conn.disconnect()
                    code
                } catch (e: java.net.ConnectException) { conn.disconnect(); -1 }
                  catch (_: Exception)                  { conn.disconnect(); -2 }
            }

            val elapsed = System.currentTimeMillis() - startMs
            when {
                responseCode == null -> {
                    Log.d(TAG, "HTTP timeout after ${elapsed}ms")
                    result.add(BlockSignal(SignalSource.HTTP_TIMEOUT,
                        "HTTP timeout after ${elapsed}ms — possible network-layer block", elapsed))
                }
                responseCode == -1 -> {
                    Log.d(TAG, "HTTP REFUSED (${elapsed}ms)")
                    result.add(BlockSignal(SignalSource.HTTP_UNREACHABLE,
                        "Connection refused to ad endpoint — VPN/iptables block", elapsed))
                }
                responseCode == 200 && elapsed < 30L -> {
                    Log.d(TAG, "Suspiciously fast 200 in ${elapsed}ms")
                    result.add(BlockSignal(SignalSource.HTTP_WRONG_RESPONSE,
                        "Suspiciously fast HTTP 200 (${elapsed}ms) — transparent sinkhole", elapsed))
                }
                responseCode in 400..499 && responseCode != 404 && responseCode != 403 -> {
                    Log.d(TAG, "Unexpected $responseCode (${elapsed}ms)")
                    result.add(BlockSignal(SignalSource.HTTP_WRONG_RESPONSE,
                        "Unexpected HTTP $responseCode from ad endpoint", elapsed))
                }
                else -> Log.d(TAG, "HTTP OK: $responseCode (${elapsed}ms)")
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startMs
            if (elapsed < 1_000L) {
                result.add(BlockSignal(SignalSource.HTTP_UNREACHABLE,
                    "HTTP fast-fail (${elapsed}ms): ${e.message}", elapsed))
            }
        }

        result
    }

    // ── SDK State Check ────────────────────────────────────────────────────────

    /**
     * Cek apakah Unity Ads SDK berhasil init dan load ad unit.
     *
     * SDK_WAIT_MS dinaikkan ke 15s karena:
     *  - Unity Ads cold start bisa 8-12 s di jaringan normal
     *  - Sinyal ini TIDAK berdiri sendiri — filterEffectiveSignals() yang
     *    memutuskan apakah SDK signal perlu dikombinasi sinyal lain
     */
    private suspend fun runSdkStateCheck(): List<BlockSignal> = withContext(Dispatchers.IO) {
        val result = mutableListOf<BlockSignal>()

        val initOk = waitForCondition(SDK_WAIT_MS, 200L) { AdsManager.isInitialized.value }
        if (!initOk) {
            Log.d(TAG, "SDK did not init within ${SDK_WAIT_MS}ms")
            result.add(BlockSignal(SignalSource.SDK_INIT_TIMEOUT,
                "Unity Ads SDK failed to initialize within ${SDK_WAIT_MS / 1000}s", SDK_WAIT_MS))
            return@withContext result
        }

        Log.d(TAG, "SDK initialized OK — checking ad load")

        val loadOk = waitForCondition(8_000L, 200L) { AdsManager.interstitialReady.value }
        if (!loadOk) {
            Log.d(TAG, "Interstitial did not load within 8s")
            result.add(BlockSignal(SignalSource.SDK_LOAD_TIMEOUT,
                "Ad unit failed to load after SDK init — CDN/auction endpoint possibly blocked", 8_000L))
        } else {
            Log.d(TAG, "Ad unit loaded OK — no SDK signal")
        }

        result
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private fun hasInternetConnection(context: Context): Boolean {
        val cm   = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private suspend fun waitForCondition(timeoutMs: Long, intervalMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            delay(intervalMs)
        }
        return condition()
    }

    private fun <T> withTimeoutOrNullSync(timeoutMs: Long, block: () -> T): T? {
        var result: T? = null
        val thread = Thread { try { result = block() } catch (_: Exception) {} }
        thread.start()
        thread.join(timeoutMs)
        return result
    }

    private fun calculateConfidence(signals: List<BlockSignal>): Int {
        val weights = mapOf(
            SignalSource.DNS_FAST_FAIL       to 45,
            SignalSource.DNS_SINKHOLE        to 40,
            SignalSource.DNS_NXDOMAIN        to 30,
            SignalSource.SDK_INIT_TIMEOUT    to 25,  // diturunkan karena tidak bisa berdiri sendiri
            SignalSource.SDK_LOAD_TIMEOUT    to 20,
            SignalSource.HTTP_UNREACHABLE    to 35,
            SignalSource.HTTP_TIMEOUT        to 15,
            SignalSource.HTTP_WRONG_RESPONSE to 35,
        )
        return signals.sumOf { weights[it.source] ?: 0 }.coerceIn(0, 100)
    }
}
