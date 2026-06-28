package com.aether.lv.data.preferences

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Penyimpanan posisi terakhir pill nav mengambang ([com.aether.lv.ui.component.FloatingPillNav]),
 * disimpan sebagai fraksi (0f..1f) terhadap lebar/tinggi area, supaya tetap
 * valid walau orientasi atau ukuran layar berubah.
 *
 * Memakai DataStore "loglog_prefs" yang sama dengan [ThemePreferences] —
 * instance dataStore tunggal ada di AppDataStore.kt.
 */
class PillNavPreferences(private val context: Context) {
    companion object {
        val PILL_OFFSET_X_KEY = floatPreferencesKey("pill_nav_offset_x")
        val PILL_OFFSET_Y_KEY = floatPreferencesKey("pill_nav_offset_y")
    }

    val offset: Flow<Offset?>
        get() = context.dataStore.data.map { prefs ->
            val x = prefs[PILL_OFFSET_X_KEY]
            val y = prefs[PILL_OFFSET_Y_KEY]
            if (x != null && y != null) Offset(x, y) else null
        }

    suspend fun setOffset(offset: Offset) {
        context.dataStore.edit { prefs ->
            prefs[PILL_OFFSET_X_KEY] = offset.x
            prefs[PILL_OFFSET_Y_KEY] = offset.y
        }
    }
}
