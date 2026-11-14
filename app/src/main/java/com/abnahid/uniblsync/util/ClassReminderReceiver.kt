package com.abnahid.uniblsync.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.abnahid.uniblsync.data.local.AppDatabase
import com.abnahid.uniblsync.data.model.ClassEntity
import com.abnahid.uniblsync.data.model.ClassReminderStateEntity
import com.abnahid.uniblsync.data.model.ScheduledReminderEntity
import com.abnahid.uniblsync.data.schedule.ReminderReconciler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * Fired by AlarmManager for two-stage class reminders (Advance + Final Class-Start)
 * and LRM live verification tests.
 *
 * Uses Room [ClassReminderStateEntity] to prevent duplicate deliveries and skips
 * cancelled or stale classes even if an alarm fires late.
 */
class ClassReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_LRM_TEST_REMINDER) {
            val testMinutes = intent.getIntExtra(EXTRA_LRM_MINUTES, 2)
            val store = ReminderGlobalStore.getInstance(context)
            store.isLrmTestActive = false
            store.lrmTestTargetEpochMs = 0L
            store.lrmLastDeliveredEpochMs = System.currentTimeMillis()

            NotificationHelper.sendClassReminderNotification(
                context = context,
                notificationId = 8888,
                title = "LRM Test Alarm • Verified",
                message = "Success! Test alarm fired after $testMinutes min. Your device woke up and delivered this offline alert on time.",
                vibrate = true
            )
            return
        }

        if (intent.action != ACTION_CLASS_REMINDER) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleClassReminder(appContext, intent)
            } catch (_: Exception) {
                // Never crash on broadcast delivery
            } finally {
                pendingResult.finish()
            }
        }
    }

    internal suspend fun handleClassReminder(
        context: Context,
        intent: Intent,
        databaseOverride: AppDatabase? = null,
        nowEpochMs: Long = System.currentTimeMillis()
    ) {
        val database = databaseOverride ?: AppDatabase.getDatabase(context)
        val store = ReminderGlobalStore.getInstance(context)

        val reminderKey = intent.getStringExtra(EXTRA_REMINDER_KEY)
            ?: intent.data?.lastPathSegment.orEmpty()
        val stage = intent.getStringExtra(EXTRA_STAGE) ?: ScheduledReminderEntity.STAGE_ADVANCE
        val storedRow = reminderKey.takeIf { it.isNotBlank() }
            ?.let { database.scheduledReminderDao().getByReminderKey(it) }

        val classId = intent.getStringExtra(EXTRA_CLASS_ID)
            ?: storedRow?.classId.orEmpty()
        val occurrenceDate = intent.getStringExtra(EXTRA_OCCURRENCE_DATE)
            ?: storedRow?.occurrenceDate.orEmpty()
        val subject = intent.getStringExtra(EXTRA_SUBJECT)
            ?: storedRow?.subject.orEmpty()
        val locationOrUrl = intent.getStringExtra(EXTRA_LOCATION_OR_URL)
            ?: storedRow?.locationOrUrl.orEmpty()
        val leadMinutes = if (intent.hasExtra(EXTRA_LEAD_MINUTES)) {
            intent.getIntExtra(EXTRA_LEAD_MINUTES, store.leadMinutes)
        } else {
            storedRow?.leadMinutes ?: store.leadMinutes
        }
        val soundEnabled = if (intent.hasExtra(EXTRA_ALARM_ENABLED)) {
            intent.getBooleanExtra(EXTRA_ALARM_ENABLED, store.isAlarmEnabled)
        } else {
            storedRow?.isAlarmEnabled ?: store.isAlarmEnabled
        }
        val vibrate = if (intent.hasExtra(EXTRA_VIBRATE)) {
            intent.getBooleanExtra(EXTRA_VIBRATE, store.isVibrateEnabled)
        } else {
            storedRow?.vibrate ?: store.isVibrateEnabled
        }

        val liveClass = classId.takeIf { it.isNotBlank() }
            ?.let { database.classDao().getClassByIdSnapshot(it) }
        val classStartEpochMs = when {
            liveClass != null -> ReminderReconciler.resolveAuthoritativeStartMs(liveClass)
            intent.getLongExtra(EXTRA_CLASS_START_EPOCH_MS, 0L) > 0L ->
                intent.getLongExtra(EXTRA_CLASS_START_EPOCH_MS, 0L)
            storedRow != null && storedRow.classStartEpochMs > 0L -> storedRow.classStartEpochMs
            else -> nowEpochMs
        }

        val sourceTz = liveClass?.sourceTimezone ?: TimezoneHelper.BOSNIA_TIMEZONE
        val studentTz = store.selectedTimezone
        val localClassTimeDisplay =
            "${TimezoneHelper.formatToLocalTime(classStartEpochMs, studentTz)} (${TimezoneHelper.getTimezoneShortLabel(studentTz)})"

        // Cancel/skip if the live class was cancelled in Room.
        if (liveClass?.status == ClassEntity.STATUS_CANCELLED) {
            if (reminderKey.isNotBlank()) database.scheduledReminderDao().deleteByReminderKey(reminderKey)
            ReminderDiagnosticsLogger.log(
                context = context,
                database = database,
                eventType = "RECEIVER",
                classId = classId,
                subject = subject,
                stage = stage,
                sourceTimezone = sourceTz,
                localClassTime = localClassTimeDisplay,
                advanceTriggerMs = 0L,
                finalTriggerMs = classStartEpochMs,
                resultStatus = "SKIPPED_CANCELLED",
                message = "Receiver skipped cancelled class"
            )
            return
        }

        val occurrenceKey = ClassReminderStateEntity.buildOccurrenceKey(classId, occurrenceDate)
        val stateDao = database.classReminderStateDao()
        val existingState = stateDao.getByOccurrenceKey(occurrenceKey)

        // Duplicate prevention: verify stage has not already been delivered.
        if (stage == ScheduledReminderEntity.STAGE_ADVANCE && existingState?.advanceDelivered == true) {
            if (reminderKey.isNotBlank()) database.scheduledReminderDao().deleteByReminderKey(reminderKey)
            ReminderDiagnosticsLogger.log(
                context = context,
                database = database,
                eventType = "RECEIVER",
                classId = classId,
                subject = subject,
                stage = stage,
                sourceTimezone = sourceTz,
                localClassTime = localClassTimeDisplay,
                advanceTriggerMs = existingState.advanceTriggerEpochMs,
                finalTriggerMs = classStartEpochMs,
                resultStatus = "DUPLICATE_PREVENTED",
                message = "Advance notification already delivered; skipping duplicate"
            )
            return
        }
        if (stage == ScheduledReminderEntity.STAGE_FINAL && existingState?.finalDelivered == true) {
            if (reminderKey.isNotBlank()) database.scheduledReminderDao().deleteByReminderKey(reminderKey)
            ReminderDiagnosticsLogger.log(
                context = context,
                database = database,
                eventType = "RECEIVER",
                classId = classId,
                subject = subject,
                stage = stage,
                sourceTimezone = sourceTz,
                localClassTime = localClassTimeDisplay,
                advanceTriggerMs = existingState.advanceTriggerEpochMs,
                finalTriggerMs = classStartEpochMs,
                resultStatus = "DUPLICATE_PREVENTED",
                message = "Final class-start notification already delivered; skipping duplicate"
            )
            return
        }

        // Stale alarm guard (never deliver stale notifications well past grace period).
        val graceMs = store.gracePeriodMinutes * 60_000L
        if (nowEpochMs > classStartEpochMs + graceMs) {
            if (reminderKey.isNotBlank()) database.scheduledReminderDao().deleteByReminderKey(reminderKey)
            ReminderDiagnosticsLogger.log(
                context = context,
                database = database,
                eventType = "RECEIVER",
                classId = classId,
                subject = subject,
                stage = stage,
                sourceTimezone = sourceTz,
                localClassTime = localClassTimeDisplay,
                advanceTriggerMs = existingState?.advanceTriggerEpochMs ?: 0L,
                finalTriggerMs = classStartEpochMs,
                resultStatus = ClassReminderStateEntity.STATUS_SKIPPED_STALE,
                message = "Alarm fired past grace period; skipped stale notification"
            )
            return
        }

        val notificationId = NotificationHelper.notificationIdFor(occurrenceKey, stage)
        val delivered = if (stage == ScheduledReminderEntity.STAGE_FINAL) {
            NotificationHelper.sendClassStartNotification(
                context = context,
                notificationId = notificationId,
                subject = subject.ifBlank { "Class" },
                localTimeDisplay = localClassTimeDisplay,
                locationOrUrl = locationOrUrl,
                soundEnabled = soundEnabled,
                vibrate = vibrate
            )
        } else {
            val minsLeft = if (classStartEpochMs > nowEpochMs) {
                ceil((classStartEpochMs - nowEpochMs) / 60_000.0).toInt().coerceAtLeast(1)
            } else {
                leadMinutes.coerceAtLeast(1)
            }
            NotificationHelper.sendAdvanceReminderNotification(
                context = context,
                notificationId = notificationId,
                subject = subject.ifBlank { "Class" },
                minutesUntilStart = minsLeft,
                localTimeDisplay = localClassTimeDisplay,
                locationOrUrl = locationOrUrl,
                isCatchUp = false,
                soundEnabled = soundEnabled,
                vibrate = vibrate
            )
        }

        // Update persisted state and clean up fired alarm row.
        val baseState = existingState ?: ClassReminderStateEntity(
            occurrenceKey = occurrenceKey,
            classId = classId,
            classDate = occurrenceDate,
            subject = subject,
            sourceTimezone = sourceTz,
            classStartEpochMs = classStartEpochMs,
            advanceTriggerEpochMs = classStartEpochMs - leadMinutes * 60_000L,
            finalTriggerEpochMs = classStartEpochMs
        )

        if (delivered) {
            if (stage == ScheduledReminderEntity.STAGE_FINAL) {
                stateDao.upsert(
                    baseState.copy(
                        finalDelivered = true,
                        finalDeliveredAtEpochMs = nowEpochMs,
                        finalScheduledStatus = ClassReminderStateEntity.STATUS_DELIVERED,
                        repeatCount = 0,
                        updatedAtEpochMs = nowEpochMs
                    )
                )
            } else {
                stateDao.upsert(
                    baseState.copy(
                        advanceDelivered = true,
                        advanceDeliveredAtEpochMs = nowEpochMs,
                        advanceScheduledStatus = ClassReminderStateEntity.STATUS_DELIVERED,
                        repeatCount = 0,
                        updatedAtEpochMs = nowEpochMs
                    )
                )
            }
        } else {
            stateDao.upsert(
                baseState.copy(
                    advanceScheduledStatus = if (stage == ScheduledReminderEntity.STAGE_ADVANCE) {
                        ClassReminderStateEntity.STATUS_BLOCKED_NO_PERMISSION
                    } else {
                        baseState.advanceScheduledStatus
                    },
                    finalScheduledStatus = if (stage == ScheduledReminderEntity.STAGE_FINAL) {
                        ClassReminderStateEntity.STATUS_BLOCKED_NO_PERMISSION
                    } else {
                        baseState.finalScheduledStatus
                    },
                    updatedAtEpochMs = nowEpochMs
                )
            )
        }

        if (reminderKey.isNotBlank()) {
            database.scheduledReminderDao().deleteByReminderKey(reminderKey)
        }

        ReminderDiagnosticsLogger.log(
            context = context,
            database = database,
            eventType = "DELIVERY",
            classId = classId,
            subject = subject,
            stage = stage,
            sourceTimezone = sourceTz,
            localClassTime = localClassTimeDisplay,
            advanceTriggerMs = baseState.advanceTriggerEpochMs,
            finalTriggerMs = classStartEpochMs,
            resultStatus = if (delivered) "DELIVERED" else "BLOCKED_NO_PERMISSION",
            message = if (delivered) {
                "$stage notification delivered once (no repeat)"
            } else {
                "$stage notification blocked: POST_NOTIFICATIONS denied"
            }
        )
    }

    companion object {
        const val ACTION_CLASS_REMINDER = "com.abnahid.uniblsync.ACTION_CLASS_REMINDER"
        const val ACTION_LRM_TEST_REMINDER = "com.abnahid.uniblsync.ACTION_LRM_TEST_REMINDER"
        const val EXTRA_LRM_MINUTES = "lrmMinutes"

        const val EXTRA_REMINDER_KEY = "reminderKey"
        const val EXTRA_CLASS_ID = "classId"
        const val EXTRA_OCCURRENCE_DATE = "occurrenceDate"
        const val EXTRA_STAGE = "stage"
        const val EXTRA_CLASS_START_EPOCH_MS = "classStartEpochMs"
        const val EXTRA_SUBJECT = "subject"
        const val EXTRA_CLASS_TYPE = "classType"
        const val EXTRA_GROUP = "groupName"
        const val EXTRA_START_TIME = "sourceStartTime"
        const val EXTRA_END_TIME = "sourceEndTime"
        const val EXTRA_MODE = "attendanceMode"
        const val EXTRA_LOCATION_OR_URL = "locationOrUrl"
        const val EXTRA_LEAD_MINUTES = "leadMinutes"
        const val EXTRA_ALARM_ENABLED = "alarmEnabled"
        const val EXTRA_VIBRATE = "vibrate"
    }
}
