package com.abnahid.uniblsync.util

import android.content.Context

/**
 * Global reminder defaults persisted outside Compose state. Per-class overrides
 * live in Room (ReminderPreferenceEntity); everything here is safe to read
 * synchronously and is the fallback for classes without a saved preference.
 */
class ReminderGlobalStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("unibl_reminder_prefs", Context.MODE_PRIVATE)

    var leadMinutes: Int
        get() = prefs.getInt(KEY_LEAD_MINUTES, 20)
        set(value) = prefs.edit().putInt(KEY_LEAD_MINUTES, value.coerceIn(1, 24 * 60)).apply()

    var soundTone: String
        get() = prefs.getString(KEY_STATUS_TONE, "Gentle Chime") ?: "Gentle Chime"
        set(value) = prefs.edit().putString(KEY_STATUS_TONE, value).apply()

    var isAlarmEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALARM_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ALARM_ENABLED, value).apply()

    var isVibrateEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE, value).apply()

    /** Whether the OS notification permission prompt was already surfaced once. */
    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_PERMISSION_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIF_PERMISSION_ASKED, value).apply()

    companion object {
        private const val KEY_LEAD_MINUTES = "global_lead_minutes"
        private const val KEY_STATUS_TONE = "global_sound_tone"
        private const val KEY_ALARM_ENABLED = "global_alarm_enabled"
        private const val KEY_VIBRATE = "global_vibrate"
        private const val KEY_NOTIF_PERMISSION_ASKED = "notification_permission_asked"

        @Volatile
        private var instance: ReminderGlobalStore? = null

        fun getInstance(context: Context): ReminderGlobalStore =
            instance ?: synchronized(this) {
                instance ?: ReminderGlobalStore(context.applicationContext).also { instance = it }
            }
    }
}
