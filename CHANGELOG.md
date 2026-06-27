# Changelog

Semua perubahan penting pada LogLog Viewer dicatat di sini.

Format mengikuti gaya sederhana berbasis tanggal dan versi.

---

## [1.1 Beta] - 2026-06-26

### Added
- Fitur **No Ads 30 menit** setelah menonton rewarded ad.
- **Limit 2 kali per hari** untuk penukaran rewarded no-ads.
- Dukungan tampilan tombol / akses rewarded ad di aplikasi.
- Integrasi state No Ads agar interstitial tidak tampil saat masa aktif masih berjalan.

### Fixed
- **Back gesture saat iklan tampil tidak lagi menutup iklan**.
- Perbaikan handling coroutine pada `MainActivity`.
- Import coroutine yang hilang untuk mendukung `lifecycleScope.launch`.

### Changed
- Penanganan iklan dibuat lebih aman terhadap kondisi lifecycle activity.
- Alur iklan dipisah antara interstitial, rewarded, dan no-ads state.

---

## [1.0.0] - 2026-06-26

### Added
- Viewer log utama.
- Editor log.
- Recent files.
- Theme preferences.
- Update checker.
- Dukungan iklan banner/interstitial/rewarded.
- Dukungan file eksternal melalui intent.

---

## Catatan Rilis

- Versi beta bisa mengandung bug kecil.
- Jika menemukan masalah pada viewer, editor, atau rewarded ad, laporkan ke maintainer.

---
**Maintainer:** Aether Dev
