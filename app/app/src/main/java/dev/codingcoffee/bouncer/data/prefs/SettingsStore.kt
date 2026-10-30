package dev.codingcoffee.bouncer.data.prefs

import android.content.Context
import androidx.core.content.edit
import dev.codingcoffee.bouncer.data.db.Label
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

    private val _mutedLabels = MutableStateFlow(
        prefs.getStringSet(KEY_MUTED_LABELS, emptySet()).orEmpty().mapNotNull { name -> Label.entries.find { it.name == name } }.toSet()
    )
    val mutedLabels: StateFlow<Set<Label>> = _mutedLabels.asStateFlow()

    fun setLabelMuted(label: Label, muted: Boolean) {
        val updated = if (muted) _mutedLabels.value + label else _mutedLabels.value - label
        prefs.edit { putStringSet(KEY_MUTED_LABELS, updated.map { it.name }.toSet()) }
        _mutedLabels.value = updated
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

    var labelModel: String?
        get() = prefs.getString(KEY_LABEL_MODEL, null)
        set(value) = prefs.edit { putString(KEY_LABEL_MODEL, value) }

    /** Ids of messages whose notification is showing. */
    val notifiedMessages: Set<Long>
        get() = prefs.getStringSet(KEY_NOTIFIED_MESSAGES, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    @Synchronized
    fun setMessageNotified(messageId: Long, notified: Boolean) {
        val current = notifiedMessages
        val updated = if (notified) current + messageId else current - messageId
        if (updated != current) prefs.edit { putStringSet(KEY_NOTIFIED_MESSAGES, updated.map { it.toString() }.toSet()) }
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_SYNCED = "initial_sync_done"
        const val KEY_LAST_SMS = "last_sms_id"
        const val KEY_LAST_MMS = "last_mms_id"
        const val KEY_LABEL_MODEL = "label_model"
        const val KEY_MUTED_LABELS = "muted_labels"
        const val KEY_NOTIFIED_MESSAGES = "notified_messages"
    }
}
