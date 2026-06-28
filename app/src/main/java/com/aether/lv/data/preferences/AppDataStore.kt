package com.aether.lv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Satu-satunya deklarasi DataStore untuk seluruh app.
 * ThemePreferences dan PillNavPreferences sama-sama menggunakan instance ini.
 * preferencesDataStore delegate hanya boleh dideklarasikan SEKALI per nama — jika
 * lebih dari sekali akan crash dengan IllegalStateException saat startup.
 */
internal val Context.dataStore: DataStore<Preferences>
    by preferencesDataStore(name = "loglog_prefs")
