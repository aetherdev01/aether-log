package com.aether.lv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "loglog_prefs")

class ThemePreferences(private val context: Context) {
    companion object {
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
        val WRAP_LINES_KEY = booleanPreferencesKey("wrap_lines")
        val SHOW_LINE_NUMBERS_KEY = booleanPreferencesKey("show_line_numbers")
        val SHOW_LOG_COLORS_KEY = booleanPreferencesKey("show_log_colors")
    }

    val isDarkMode: Flow<Boolean>
        get() = context.dataStore.data.map { it[DARK_MODE_KEY] ?: false }

    val isDynamicColor: Flow<Boolean>
        get() = context.dataStore.data.map { it[DYNAMIC_COLOR_KEY] ?: true }

    val isWrapLines: Flow<Boolean>
        get() = context.dataStore.data.map { it[WRAP_LINES_KEY] ?: false }

    val showLineNumbers: Flow<Boolean>
        get() = context.dataStore.data.map { it[SHOW_LINE_NUMBERS_KEY] ?: true }

    val showLogColors: Flow<Boolean>
        get() = context.dataStore.data.map { it[SHOW_LOG_COLORS_KEY] ?: true }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[DARK_MODE_KEY] = enabled }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[DYNAMIC_COLOR_KEY] = enabled }
    }

    suspend fun setWrapLines(enabled: Boolean) {
        context.dataStore.edit { it[WRAP_LINES_KEY] = enabled }
    }

    suspend fun setShowLineNumbers(enabled: Boolean) {
        context.dataStore.edit { it[SHOW_LINE_NUMBERS_KEY] = enabled }
    }

    suspend fun setShowLogColors(enabled: Boolean) {
        context.dataStore.edit { it[SHOW_LOG_COLORS_KEY] = enabled }
    }
}
