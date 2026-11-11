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

    /** Whether the user has seen or dismissed the battery optimization onboarding banner/dialog. */
    var batteryOptimizationPromptAsked: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_PROMPT_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_BATTERY_PROMPT_ASKED, value).apply()

    // --- LRM (Live Reminder Module) Test Alarm Settings ---
    var lrmTestDurationMinutes: Int
        get() = prefs.getInt(KEY_LRM_DURATION_MINUTES, 2)
        set(value) = prefs.edit().putInt(KEY_LRM_DURATION_MINUTES, value.coerceIn(1, 60)).apply()

    var lrmTestTargetEpochMs: Long
        get() = prefs.getLong(KEY_LRM_TARGET_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_LRM_TARGET_MS, value).apply()

    var isLrmTestActive: Boolean
        get() = prefs.getBoolean(KEY_LRM_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_LRM_ACTIVE, value).apply()

    var lrmLastDeliveredEpochMs: Long
        get() = prefs.getLong(KEY_LRM_LAST_DELIVERED_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_LRM_LAST_DELIVERED_MS, value).apply()

    companion object {
        private const val KEY_LEAD_MINUTES = "global_lead_minutes"
        private const val KEY_STATUS_TONE = "global_sound_tone"
        private const val KEY_ALARM_ENABLED = "global_alarm_enabled"
        private const val KEY_VIBRATE = "global_vibrate"
        private const val KEY_NOTIF_PERMISSION_ASKED = "notification_permission_asked"
        private const val KEY_BATTERY_PROMPT_ASKED = "battery_optimization_prompt_asked"
        private const val KEY_LRM_DURATION_MINUTES = "lrm_duration_minutes"
        private const val KEY_LRM_TARGET_MS = "lrm_target_epoch_ms"
        private const val KEY_LRM_ACTIVE = "lrm_test_active"
        private const val KEY_LRM_LAST_DELIVERED_MS = "lrm_last_delivered_ms"

        @Volatile
        private var instance: ReminderGlobalStore? = null

        fun getInstance(context: Context): ReminderGlobalStore =
            instance ?: synchronized(this) {
                instance ?: ReminderGlobalStore(context.applicationContext).also { instance = it }
            }
    }
}
