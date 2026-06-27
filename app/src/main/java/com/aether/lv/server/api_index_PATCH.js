// ─── PATCH: api/index.js ─────────────────────────────────────────────────────
//
// Perubahan pada server Vercel (aetherdev01s-projects.vercel.app)
//
// ──────────────────────────────────────────────────────────────────────────────
// LANGKAH 1: Update DEFAULT_PRODUCTS — harga basic_1m jadi 15000
// ──────────────────────────────────────────────────────────────────────────────
//
// TEMUKAN baris ini (sekitar line 59):
const DEFAULT_PRODUCTS = [
  { id: 'basic_1m', name: 'Aether Basic 1 Bulan', price: 15000, ... },
  ...
];
//
// Pastikan basic_1m sudah price: 15000 ✓ (sudah benar di codebase kamu)
// features harus mengandung 'no_ads':
  { id: 'basic_1m', name: 'Aether Basic 1 Bulan', price: 15000, currency: 'IDR', durationDays: 30, maxDevices: 1, active: true, features: ['no_ads', 'advanced_monitor'] },

// ──────────────────────────────────────────────────────────────────────────────
// LANGKAH 2: Tambahkan endpoint baru /app/license-info (opsional, untuk debug)
// ──────────────────────────────────────────────────────────────────────────────
//
// Sisipkan setelah blok `if (route === 'verify')` di router utama:

    if (route === 'app/license-status') {
      const ok = await securityMiddleware(req, res, 'app');
      if (!ok) return;
      return appLicenseStatus(req, res);
    }

// ──────────────────────────────────────────────────────────────────────────────
// LANGKAH 3: Tambahkan fungsi appLicenseStatus (setelah fungsi verify)
// ──────────────────────────────────────────────────────────────────────────────

async function appLicenseStatus(req, res) {
  if (!method(req, res, ['POST'])) return;
  const { key, deviceId } = req.body || {};
  if (!key || !deviceId) return res.status(200).json({ valid: false, error: 'MISSING_PARAMS' });

  const k = normalizeLicenseKeyInput(key);
  const record = await rget(`license:${k}`);
  if (!record) return res.status(200).json({ valid: false, error: 'NOT_FOUND' });

  const status = normalizeLicenseStatus(record);
  if (status !== 'active') return res.status(200).json({ valid: false, status });

  const now = Date.now();
  const exp = licenseExpiryState(record, now);
  if (exp.expired) return res.status(200).json({ valid: false, status: 'expired', expiresAt: record.expiresAt });

  const devices = normalizeDevice(record);
  if (!devices.some(d => d.deviceId === deviceId)) {
    return res.status(200).json({ valid: false, error: 'DEVICE_NOT_ACTIVATED' });
  }

  return res.status(200).json({
    valid: true,
    noAds: (record.features || []).includes('no_ads'),
    ...licensePublicPayload(record, exp)
  });
}

// ──────────────────────────────────────────────────────────────────────────────
// LANGKAH 4: Update Telegram bot (api/bot.js) — harga order jadi 15000
// ──────────────────────────────────────────────────────────────────────────────
//
// Cari dan ganti semua referensi harga di pesan bot yang masih 25000 → 15000
// untuk produk basic_1m.
//
// Cari: PRICE = 25000
// Ganti: PRICE = 15000
//
// Atau kalau harga diambil dari DEFAULT_PRODUCTS, sudah otomatis ikut.

// ──────────────────────────────────────────────────────────────────────────────
// CATATAN PENTING: Cara membuat lisensi manual via Admin Panel
// ──────────────────────────────────────────────────────────────────────────────
//
// 1. Buka: https://aetherdev01s-projects.vercel.app/admin
// 2. Login dengan ADMIN_TOKEN
// 3. Buat lisensi baru:
//    - Product: basic_1m
//    - Features: no_ads, advanced_monitor
//    - Max devices: 1
//    - Duration: 30 hari
// 4. Kode lisensi di-generate otomatis (format: AETHER-XXXX-XXXX)
// 5. Kirim ke pembeli via Telegram bot
//
// Atau via Telegram bot, sudah ada flow order otomatis.
