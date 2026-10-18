package com.meshi.code.prefs

import android.content.Context
import androidx.compose.runtime.mutableStateOf

enum class ThemeMode { DARK, LIGHT, SYSTEM }

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("meshi_app_prefs", Context.MODE_PRIVATE)

    // Live state so MainActivity can recompose when theme changes
    val themeModeState = mutableStateOf(
        ThemeMode.entries.find { it.name == prefs.getString("theme_mode", null) } ?: ThemeMode.DARK
    )
    var themeMode: ThemeMode
        get() = themeModeState.value
        set(v) { themeModeState.value = v; prefs.edit().putString("theme_mode", v.name).apply() }

    var notifyDms: Boolean
        get() = prefs.getBoolean("notify_dms", true)
        set(v) { prefs.edit().putBoolean("notify_dms", v).apply() }

    var notifyChannel: Boolean
        get() = prefs.getBoolean("notify_channel", true)
        set(v) { prefs.edit().putBoolean("notify_channel", v).apply() }

    var batteryAlertEnabled: Boolean
        get() = prefs.getBoolean("battery_alert_enabled", true)
        set(v) { prefs.edit().putBoolean("battery_alert_enabled", v).apply() }

    var batteryAlertPct: Int
        get() = prefs.getInt("battery_alert_pct", 20)
        set(v) { prefs.edit().putInt("battery_alert_pct", v).apply() }

    var sosActive: Boolean
        get() = prefs.getBoolean("sos_active", false)
        set(v) { prefs.edit().putBoolean("sos_active", v).apply() }

    var stealthMode: Boolean
        get() = prefs.getBoolean("stealth_mode", false)
        set(v) { prefs.edit().putBoolean("stealth_mode", v).apply() }

    var shareLocation: Boolean
        get() = prefs.getBoolean("share_location", false)
        set(v) { prefs.edit().putBoolean("share_location", v).apply() }

    var blockedContacts: Set<String>
        get() = prefs.getStringSet("blocked_contacts", emptySet()) ?: emptySet()
        set(v) { prefs.edit().putStringSet("blocked_contacts", v).apply() }

    var deletedContacts: Set<String>
        get() = prefs.getStringSet("deleted_contacts", emptySet()) ?: emptySet()
        set(v) { prefs.edit().putStringSet("deleted_contacts", v).apply() }

    /** Per-channel/DM draft text. Key: "draft_ch_N" or "draft_dm_<displayId>". */
    fun getDraft(key: String): String = prefs.getString("draft_$key", "") ?: ""
    fun setDraft(key: String, text: String) {
        if (text.isBlank()) prefs.edit().remove("draft_$key").apply()
        else prefs.edit().putString("draft_$key", text).apply()
    }
}
