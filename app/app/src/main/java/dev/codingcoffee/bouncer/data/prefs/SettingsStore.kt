package dev.codingcoffee.bouncer.data.prefs

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { System, Light, Dark }

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null)!!) }.getOrDefault(ThemeMode.System)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
        _themeMode.value = mode
    }

    var initialSyncDone: Boolean
        get() = prefs.getBoolean(KEY_SYNCED, false)
        set(value) = prefs.edit { putBoolean(KEY_SYNCED, value) }

    var lastSmsId: Long
        get() = prefs.getLong(KEY_LAST_SMS, 0)
        set(value) = prefs.edit { putLong(KEY_LAST_SMS, value) }

    var lastMmsId: Long
        get() = prefs.getLong(KEY_LAST_MMS, 0)
        set(value) = prefs.edit { putLong(KEY_LAST_MMS, value) }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_SYNCED = "initial_sync_done"
        const val KEY_LAST_SMS = "last_sms_id"
        const val KEY_LAST_MMS = "last_mms_id"
    }
}
