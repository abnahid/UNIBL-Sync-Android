package com.abnahid.uniblsync.util

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.abnahid.uniblsync.data.model.ScheduledReminderEntity

/**
 * Arms exact (or best effort inexact) AlarmManager alarms for class reminders.
 *
 * PendingIntent identity: the explicit receiver is targeted with a unique
 * request code plus a data URI carrying the reminderKey, so re-arming the same
 * occurrence updates its alarm instead of creating duplicates.
 */
object ReminderScheduler {

    private const val ACTION_CLASS_REMINDER = "com.abnahid.uniblsync.ACTION_CLASS_REMINDER"
    private const val EXTRA_REMINDER_KEY = "reminderKey"

    /** Stable request code derived from the reminder key (collision masked to positive range). */
    fun requestCodeFor(reminderKey: String): Int = reminderKey.hashCode() and Int.MAX_VALUE

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    fun alarmIntent(context: Context, reminderKey: String): PendingIntent {
        val intent = Intent(context, ClassReminderReceiver::class.java).apply {
            action = ACTION_CLASS_REMINDER
            data = Uri.parse("uniblsync://reminder/${Uri.encode(reminderKey)}")
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(reminderKey),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_NO_CREATE
        )
    }

    private fun freshAlarmIntent(context: Context, reminder: ScheduledReminderEntity): Intent =
        Intent(context, ClassReminderReceiver::class.java).apply {
            action = ACTION_CLASS_REMINDER
            data = Uri.parse("uniblsync://reminder/${Uri.encode(reminder.reminderKey)}")
            putExtra(EXTRA_REMINDER_KEY, reminder.reminderKey)
            putExtra(ClassReminderReceiver.EXTRA_SUBJECT, reminder.subject)
            putExtra(ClassReminderReceiver.EXTRA_CLASS_TYPE, reminder.classType)
            putExtra(ClassReminderReceiver.EXTRA_GROUP, reminder.groupName)
            putExtra(ClassReminderReceiver.EXTRA_MODE, reminder.attendanceMode)
            putExtra(ClassReminderReceiver.EXTRA_START_TIME, reminder.sourceStartTime)
            putExtra(ClassReminderReceiver.EXTRA_END_TIME, reminder.sourceEndTime)
            putExtra(ClassReminderReceiver.EXTRA_LOCATION_OR_URL, reminder.locationOrUrl)
            putExtra(ClassReminderReceiver.EXTRA_LEAD_MINUTES, reminder.leadMinutes)
            putExtra(ClassReminderReceiver.EXTRA_ALARM_ENABLED, reminder.isAlarmEnabled)
            putExtra(ClassReminderReceiver.EXTRA_VIBRATE, reminder.vibrate)
        }

    /**
     * Arms the alarm for [reminder]. Assumes the trigger time has already been
     * validated as still in the future by [ReminderReconciler].
     *
     * Exact alarms are used when Android grants them; otherwise the system
     * inexact alarm is used as a fallback (which may be delayed by the OS, never
     * fired earlier than requested).
     */
    fun schedule(context: Context, reminder: ScheduledReminderEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeFor(reminder.reminderKey),
            freshAlarmIntent(context, reminder),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val exactAvailable = canScheduleExactAlarms(context)
        try {
            if (exactAvailable) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerEpochMs,
                    pendingIntent
                )
            } else {
                // Fallback: inexact alarm. Delivery may be deferred by the OS;
                // Android never fires it earlier than requested.
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerEpochMs,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            // Exact alarm permission was revoked between the check and the call.
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, reminder.triggerEpochMs, pendingIntent)
            } catch (_: Exception) {
                // Out of options: the reminder stays in the Room table and is re-armed on next app open / reboot.
            }
        }
    }

    fun cancel(context: Context, reminderKey: String) {
        try {
            alarmIntent(context, reminderKey)?.let { alarmManager(context).cancel(it) }
        } catch (_: Exception) { }
    }

    fun openExactAlarmSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:${context.packageName}"))
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun hasNotificationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
}
