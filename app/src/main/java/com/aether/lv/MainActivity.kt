package com.aether.lv

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.aether.lv.ads.AdBlockManager
import com.aether.lv.ads.AdsManager
import com.aether.lv.ads.RewardedNoAdsManager
import com.aether.lv.data.preferences.ThemePreferences
import com.aether.lv.license.LicenseRepository
import com.aether.lv.permission.PermissionManager
import com.aether.lv.permission.PermissionRationaleDialog
import com.aether.lv.ui.LogLogApp
import com.aether.lv.ui.theme.LogLogTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private var externalFileUri: Uri? = null

    private var showPermissionDialog     by mutableStateOf(false)
    private var showManageStorageDialog  by mutableStateOf(false)

    private val licenseRepository by lazy { LicenseRepository(applicationContext) }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        externalFileUri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data?.also { uri -> persistUriPermission(uri, intent) }
            else               -> null
        }

        requestStoragePermissionIfNeeded()

        AdBlockManager.startDetection(this, skipForPremium = isPremiumNoAds())

        val themePrefs = ThemePreferences(this)

        setContent {
            val isDark    by themePrefs.isDarkMode.collectAsState(initial = false)
            val isDynamic by themePrefs.isDynamicColor.collectAsState(initial = true)

            LogLogTheme(darkTheme = isDark, dynamicColor = isDynamic) {
                LogLogApp(
                    externalFileUri     = externalFileUri,
                    themePrefs          = themePrefs,
                    onRequestPermission = { requestStoragePermissionIfNeeded(force = true) },
                    onShowInterstitial  = { afterAction ->
                        showInterstitialAd(
                            onComplete = afterAction,
                            onFailed   = { afterAction() }
                        )
                    },
                    onShowRewarded      = { showRewardedAdWithToast() }
                )

                if (showPermissionDialog) {
                    PermissionRationaleDialog(
                        showManageStorage   = false,
                        onRequestPermission = {
                            showPermissionDialog = false
                            requestPermissionLauncher.launch(
                                PermissionManager.requiredPermissions().toTypedArray()
                            )
                        },
                        onOpenSettings = {
                            showPermissionDialog = false
                            PermissionManager.appSettingsIntent(this).also { startActivity(it) }
                        },
                        onDismiss = { showPermissionDialog = false }
                    )
                }

                if (showManageStorageDialog) {
                    PermissionRationaleDialog(
                        showManageStorage   = true,
                        onRequestPermission = {},
                        onOpenSettings = {
                            showManageStorageDialog = false
                            PermissionManager.manageStorageSettingsIntent(this)
                                ?.also { startActivity(it) }
                        },
                        onDismiss = { showManageStorageDialog = false }
                    )
                }
            }
        }
    }

    /**
     * Override onBackPressed untuk mem-block back gesture saat iklan sedang ditampilkan.
     * Ini mencegah iklan interstitial/rewarded tertutup paksa oleh back gesture.
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (AdsManager.isAdShowing.value) {
            // Iklan sedang tampil — abaikan back gesture
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            externalFileUri = intent.data?.also { uri -> persistUriPermission(uri, intent) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (PermissionManager.hasStoragePermission(this) ||
            PermissionManager.canReadArbitraryFiles(this)) {
            showPermissionDialog    = false
            showManageStorageDialog = false
        }
    }

    /**
     * Defense-in-depth: walau gating utama sudah dilakukan di level Composable
     * (HomeScreen/SettingsScreen tidak akan memanggil onShowInterstitial saat premium aktif),
     * cek ulang di sini supaya interstitial tidak pernah tampil untuk user yang sudah
     * mengaktifkan lisensi no_ads, terlepas dari jalur pemanggilan manapun.
     */
    private fun isPremiumNoAds(): Boolean = runBlocking {
        runCatching { licenseRepository.licenseState.first().isNoAds }.getOrDefault(false)
    }

    fun showInterstitialAd(
        onComplete: () -> Unit = {},
        onFailed  : (String) -> Unit = {}
    ) {
        if (isPremiumNoAds()) {
            // Lisensi premium aktif — langsung jalankan aksi tanpa menampilkan iklan.
            onComplete()
            return
        }
        AdsManager.showInterstitial(
            activity   = this,
            onComplete = onComplete,
            onFailed   = onFailed
        )
    }

    fun showRewardedAd(
        onRewarded : () -> Unit = {},
        onComplete : () -> Unit = {},
        onFailed   : (String) -> Unit = {}
    ) {
        AdsManager.showRewarded(
            activity   = this,
            onRewarded = onRewarded,
            onComplete = onComplete,
            onFailed   = onFailed
        )
    }

    /**
     * Tonton rewarded ad dari Settings (tombol "No Ads 30 Menit").
     * Menampilkan Toast untuk setiap kemungkinan hasil:
     * - Iklan belum tersedia / belum siap dimuat → "Iklan Belum Tersedia"
     * - Iklan siap & berhasil ditonton sampai selesai → reward diberikan + Toast sukses
     * - Iklan gagal ditampilkan (network error, dll) → Toast gagal
     */
    private fun showRewardedAdWithToast() {
        if (isPremiumNoAds()) {
            Toast.makeText(this, "Premium aktif — iklan sudah dinonaktifkan", Toast.LENGTH_SHORT).show()
            return
        }

        // Belum siap sama sekali (SDK belum init / belum ada ad yang dimuat)
        if (!AdsManager.rewardedReady.value) {
            Toast.makeText(this, "Iklan Belum Tersedia", Toast.LENGTH_SHORT).show()
            // Coba muat lagi untuk percobaan berikutnya
            AdsManager.loadRewarded(this)
            return
        }

        showRewardedAd(
            onRewarded = {
                val granted = RewardedNoAdsManager.grant30Minutes()
                if (granted) {
                    Toast.makeText(this, "Berhasil! Bebas iklan 30 menit aktif", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Batas harian tercapai", Toast.LENGTH_SHORT).show()
                }
            },
            onFailed = {
                Toast.makeText(this, "Iklan Belum Tersedia", Toast.LENGTH_SHORT).show()
                AdsManager.loadRewarded(this)
            }
        )
    }

    private fun persistUriPermission(uri: Uri, intent: Intent) {
        val flags = intent.flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) { }
    }

    fun requestStoragePermissionIfNeeded(force: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            android.os.Environment.isExternalStorageManager()) return

        val perms = PermissionManager.requiredPermissions()

        if (perms.isEmpty()) {
            if (force) showManageStorageDialog = true
            return
        }

        val allGranted = perms.all { perm ->
            androidx.core.content.ContextCompat.checkSelfPermission(this, perm) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (allGranted && !force) return

        val shouldShowRationale = perms.any { shouldShowRequestPermissionRationale(it) }

        when {
            force && shouldShowRationale       -> showPermissionDialog    = true
            force && !shouldShowRationale
                  && !allGranted               -> showManageStorageDialog = true
            else -> requestPermissionLauncher.launch(perms.toTypedArray())
        }
    }
}
