package com.sagewire.rangewater.ui.help

import android.content.Context

data class HelpPreferences(
    val fieldTipsEnabled: Boolean = true,
    val contextualHintsEnabled: Boolean = true,
    val welcomeSeen: Boolean = false,
    val dismissedHintIds: Set<String> = emptySet(),
    val nextTipIndex: Int = 0
)

class HelpPreferencesRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun get(): HelpPreferences = HelpPreferences(
        fieldTipsEnabled = preferences.getBoolean(KEY_FIELD_TIPS, true),
        contextualHintsEnabled = preferences.getBoolean(KEY_CONTEXTUAL_HINTS, true),
        welcomeSeen = preferences.getBoolean(KEY_WELCOME_SEEN, false),
        dismissedHintIds = preferences.getStringSet(KEY_DISMISSED_HINTS, emptySet()).orEmpty().toSet(),
        nextTipIndex = preferences.getInt(KEY_NEXT_TIP, 0).coerceAtLeast(0)
    )

    fun setFieldTipsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_FIELD_TIPS, enabled).apply()
    }

    fun setContextualHintsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_CONTEXTUAL_HINTS, enabled).apply()
    }

    fun markWelcomeSeen() {
        preferences.edit().putBoolean(KEY_WELCOME_SEEN, true).apply()
    }

    fun replayWelcome() {
        preferences.edit().putBoolean(KEY_WELCOME_SEEN, false).apply()
    }

    fun dismissHint(id: String) {
        require(id.isNotBlank())
        val updated = get().dismissedHintIds + id
        preferences.edit().putStringSet(KEY_DISMISSED_HINTS, updated).apply()
    }

    fun resetHints() {
        preferences.edit().remove(KEY_DISMISSED_HINTS).apply()
    }

    fun advanceTip(totalTips: Int): Int {
        require(totalTips > 0)
        val current = get().nextTipIndex % totalTips
        preferences.edit().putInt(KEY_NEXT_TIP, (current + 1) % totalTips).apply()
        return current
    }

    companion object {
        const val PREFERENCES_NAME = "rangewater_help_prefs"
        const val KEY_FIELD_TIPS = "field_tips_enabled"
        const val KEY_CONTEXTUAL_HINTS = "contextual_hints_enabled"
        const val KEY_WELCOME_SEEN = "welcome_seen"
        const val KEY_DISMISSED_HINTS = "dismissed_hint_ids"
        const val KEY_NEXT_TIP = "next_tip_index"
    }
}
