package com.abnahid.uniblsync.util

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.abnahid.uniblsync.data.model.ClassReminderStateEntity
import com.abnahid.uniblsync.data.model.ScheduledReminderEntity

sealed class ScheduleOutcome(val statusCode: String) {
    data object Exact : ScheduleOutcome(ClassReminderStateEntity.STATUS_SCHEDULED_EXACT)
    data class InexactFallback(val reason: String) :
        ScheduleOutcome(ClassReminderStateEntity.STATUS_SCHEDULED_INEXACT)
    data class Failed(val reason: String) :
        ScheduleOutcome(ClassReminderStateEntity.STATUS_FAILED)
}

/**
 * Arms exact (or explicit inexact fallback) AlarmManager RTC_WAKEUP alarms
 * for two-stage class reminders.
 *
 * PendingIntent identity: the explicit receiver is targeted with a stage-isolated
 * request code AND a unique data URI (`uniblsync://reminder/{stage}/{reminderKey}`),
 * preventing collisions across classes or stages.
 */
object ReminderScheduler {

    private const val TAG = "UniblReminderScheduler"
    private const val ACTION_CLASS_REMINDER = ClassReminderReceiver.ACTION_CLASS_REMINDER
    private const val EXTRA_REMINDER_KEY = ClassReminderReceiver.EXTRA_REMINDER_KEY

    /**
     * Collision-safe request code separating ADVANCE (bit 0 = 0) from FINAL (bit 0 = 1).
     */
    fun requestCodeFor(
        reminderKey: String,
        stage: String = ScheduledReminderEntity.STAGE_ADVANCE
    ): Int {
        val stageBit = if (stage == ScheduledReminderEntity.STAGE_FINAL) 1 else 0
        return ((reminderKey.hashCode() and 0x3FFFFFFF) shl 1) or stageBit
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    fun alarmIntent(
        context: Context,
        reminderKey: String,
        stage: String = ScheduledReminderEntity.STAGE_ADVANCE
    ): PendingIntent? {
        val intent = Intent(context, ClassReminderReceiver::class.java).apply {
            action = ACTION_CLASS_REMINDER
            data = Uri.parse("uniblsync://reminder/$stage/${Uri.encode(reminderKey)}")
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(reminderKey, stage),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
    }

    private fun freshAlarmIntent(context: Context, reminder: ScheduledReminderEntity): Intent =
        Intent(context, ClassReminderReceiver::class.java).apply {
            action = ACTION_CLASS_REMINDER
            data = Uri.parse("uniblsync://reminder/${reminder.stage}/${Uri.encode(reminder.reminderKey)}")
            putExtra(EXTRA_REMINDER_KEY, reminder.reminderKey)
            putExtra(ClassReminderReceiver.EXTRA_CLASS_ID, reminder.classId)
            putExtra(ClassReminderReceiver.EXTRA_OCCURRENCE_DATE, reminder.occurrenceDate)
            putExtra(ClassReminderReceiver.EXTRA_STAGE, reminder.stage)
            putExtra(ClassReminderReceiver.EXTRA_CLASS_START_EPOCH_MS, reminder.classStartEpochMs)
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
     * Arms the RTC_WAKEUP alarm for [reminder] using setExactAndAllowWhileIdle when permitted,
     * falling back to setAndAllowWhileIdle when SCHEDULE_EXACT_ALARM is denied.
     *
     * Returns an explicit [ScheduleOutcome] so failures or inexact fallbacks are never hidden.
     */
    fun schedule(context: Context, reminder: ScheduledReminderEntity): ScheduleOutcome {
        val alarmManager = alarmManager(context)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeFor(reminder.reminderKey, reminder.stage),
            freshAlarmIntent(context, reminder),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val exactAvailable = canScheduleExactAlarms(context)
        return try {
            if (exactAvailable) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerEpochMs,
                    pendingIntent
                )
                ScheduleOutcome.Exact
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerEpochMs,
                    pendingIntent
                )
                ScheduleOutcome.InexactFallback(
                    "SCHEDULE_EXACT_ALARM denied; scheduled with inexact setAndAllowWhileIdle fallback (exact delivery not guaranteed)"
                )
            }
        } catch (secEx: SecurityException) {
            Log.w(TAG, "Exact alarm SecurityException for ${reminder.reminderKey}: ${secEx.message}")
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerEpochMs,
                    pendingIntent
                )
                ScheduleOutcome.InexactFallback(
                    "Exact alarm permission revoked during scheduling; fell back to setAndAllowWhileIdle"
                )
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Alarm fallback failed for ${reminder.reminderKey}: ${fallbackEx.message}")
                ScheduleOutcome.Failed(fallbackEx.message ?: "AlarmManager rejected alarm")
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Alarm scheduling failed for ${reminder.reminderKey}: ${ex.message}")
            ScheduleOutcome.Failed(ex.message ?: "Unexpected AlarmManager error")
        }
    }

    fun cancel(
        context: Context,
        reminderKey: String,
        stage: String = ScheduledReminderEntity.STAGE_ADVANCE
    ) {
        try {
            alarmIntent(context, reminderKey, stage)?.let {
                alarmManager(context).cancel(it)
                it.cancel()
            }
        } catch (_: Exception) { }
    }

    fun cancelBothStages(context: Context, classId: String, occurrenceDate: String) {
        cancel(
            context,
            ScheduledReminderEntity.buildKey(classId, occurrenceDate, ScheduledReminderEntity.STAGE_ADVANCE),
            ScheduledReminderEntity.STAGE_ADVANCE
        )
        cancel(
            context,
            ScheduledReminderEntity.buildKey(classId, occurrenceDate, ScheduledReminderEntity.STAGE_FINAL),
            ScheduledReminderEntity.STAGE_FINAL
        )
    }

    fun openExactAlarmSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

    const val LRM_REQUEST_CODE = 9991

    fun scheduleLrmTest(context: Context, durationMinutes: Int): ScheduleOutcome {
        val targetEpochMs = System.currentTimeMillis() + durationMinutes * 60 * 1000L
        val store = ReminderGlobalStore.getInstance(context)
        store.lrmTestDurationMinutes = durationMinutes
        store.lrmTestTargetEpochMs = targetEpochMs
        store.isLrmTestActive = true

        val alarmManager = alarmManager(context)
        val intent = Intent(context, ClassReminderReceiver::class.java).apply {
            action = ClassReminderReceiver.ACTION_LRM_TEST_REMINDER
            putExtra(ClassReminderReceiver.EXTRA_LRM_MINUTES, durationMinutes)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            LRM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val exactAvailable = canScheduleExactAlarms(context)
        return try {
            if (exactAvailable) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    targetEpochMs,
                    pendingIntent
                )
                ScheduleOutcome.Exact
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetEpochMs, pendingIntent)
                ScheduleOutcome.InexactFallback("Exact alarm permission denied for LRM test")
            }
        } catch (_: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetEpochMs, pendingIntent)
                ScheduleOutcome.InexactFallback("Exact alarm permission revoked for LRM test")
            } catch (ex: Exception) {
                ScheduleOutcome.Failed(ex.message ?: "LRM test scheduling failed")
            }
        } catch (ex: Exception) {
            ScheduleOutcome.Failed(ex.message ?: "LRM test scheduling failed")
        }
    }

    fun cancelLrmTest(context: Context) {
        val store = ReminderGlobalStore.getInstance(context)
        store.isLrmTestActive = false
        store.lrmTestTargetEpochMs = 0L

        val intent = Intent(context, ClassReminderReceiver::class.java).apply {
            action = ClassReminderReceiver.ACTION_LRM_TEST_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            LRM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            alarmManager(context).cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
}
