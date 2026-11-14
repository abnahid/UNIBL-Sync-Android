package com.abnahid.uniblsync.data.schedule

import android.content.Context
import com.abnahid.uniblsync.data.local.AppDatabase
import com.abnahid.uniblsync.data.model.ClassEntity
import com.abnahid.uniblsync.data.model.ClassReminderStateEntity
import com.abnahid.uniblsync.data.model.ReminderPreferenceEntity
import com.abnahid.uniblsync.data.model.ScheduleOverrideEntity
import com.abnahid.uniblsync.data.model.ScheduledReminderEntity
import com.abnahid.uniblsync.util.NotificationHelper
import com.abnahid.uniblsync.util.ReminderDiagnosticsLogger
import com.abnahid.uniblsync.util.ReminderGlobalStore
import com.abnahid.uniblsync.util.ReminderScheduler
import com.abnahid.uniblsync.util.ScheduleOutcome
import com.abnahid.uniblsync.util.TimezoneHelper
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

/**
 * Computes two-stage reminder trigger instants (advance + exact class-start),
 * performs missed-reminder catch-up recovery, prevents duplicates via Room state,
 * and keeps AlarmManager alarms reconciled across launches, syncs, reboots, and
 * timezone changes.
 */
object ReminderReconciler {

    private const val MIN_LEAD_MINUTES = 0
    const val MAX_LEAD_MINUTES = 60 * 24
    private const val RECURRING_HORIZON_DAYS = 21L
    private const val PAST_RETENTION_DAYS = 30L

    private val GENERATED_ID_REGEX = Regex("^rec:(.+):(\\d{4}-\\d{2}-\\d{2})$")
    private const val ONE_TIME_ID_PREFIX = "ovr:"

    /** Guards concurrent reconciliations (e.g. onCreate + refresh + onResume) against duplicate catch-ups. */
    private val reconcileMutex = Mutex()

    fun reminderKeyFor(
        classId: String,
        occurrenceDate: String,
        stage: String = ScheduledReminderEntity.STAGE_ADVANCE
    ): String = ScheduledReminderEntity.buildKey(classId, occurrenceDate, stage)

    fun isGeneratedOccurrenceId(classId: String): Boolean =
        classId.startsWith("rec:") || classId.startsWith(ONE_TIME_ID_PREFIX)

    fun parseGeneratedOccurrenceId(classId: String): Pair<String, String>? {
        val result = GENERATED_ID_REGEX.matchEntire(classId) ?: return null
        return result.groupValues[1] to result.groupValues[2]
    }

    private suspend fun generateOneOff(database: AppDatabase, classId: String): ClassEntity? {
        val (scheduleId, dateStr) = parseGeneratedOccurrenceId(classId) ?: return null
        val schedule = database.recurringScheduleDao().getScheduleById(scheduleId) ?: return null
        val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return null
        if (dateStr < schedule.effectiveFromIsoDate) return null
        if (schedule.effectiveToIsoDate.isNotBlank() && dateStr > schedule.effectiveToIsoDate) return null
        if (!schedule.isActive) return null
        if (schedule.explicitDates.isNotBlank() &&
            ScheduleOccurrenceGenerator.parseExplicitDates(schedule.explicitDates).none { it == dateStr }
        ) return null
        val overrides = database.scheduleOverrideDao().getAllOverridesSnapshot()
        return ScheduleOccurrenceGenerator.generate(listOf(schedule), overrides, date, date).firstOrNull()
    }

    /**
     * Full reconciliation across all active published classes (both synced API classes
     * and recurring schedule occurrences merged with overrides).
     *
     * - Schedules Stage 1 (Advance) using per-class preference or global default (20m).
     * - Always schedules Stage 2 (Final) at exact class start, independent of Stage 1.
     * - Performs missed-reminder catch-up recovery when opening shortly before class
     *   or within the configurable 5-minute grace period after start.
     * - Prevents duplicates via [ClassReminderStateEntity] and [reconcileMutex].
     * - Cancels obsolete alarms for cancelled, rescheduled, or removed classes.
     */
    suspend fun reconcileAll(
        context: Context,
        database: AppDatabase,
        overrides: List<ScheduleOverrideEntity> = emptyList(),
        nowEpochMs: Long = System.currentTimeMillis(),
        allowImmediateCatchUp: Boolean = true
    ) = reconcileMutex.withLock {
        val store = ReminderGlobalStore.getInstance(context)
        val preferences = database.reminderDao().getAllPreferencesSnapshot()
        val prefsByClassId = preferences.associateBy { it.classId }

        val syncedClasses = database.classDao().getAllClassesSnapshot()
        val schedules = database.recurringScheduleDao().getAllSchedulesSnapshot()
        val classOverrides = if (overrides.isEmpty()) {
            database.scheduleOverrideDao().getAllOverridesSnapshot()
        } else {
            overrides
        }

        val bosniaZone = ZoneId.of(TimezoneHelper.BOSNIA_TIMEZONE)
        val bosniaToday = Instant.ofEpochMilli(nowEpochMs).atZone(bosniaZone).toLocalDate()
        val generatedOccurrences = ScheduleOccurrenceGenerator.generate(
            schedules = schedules,
            overrides = classOverrides,
            startDate = bosniaToday.minusDays(1),
            endDate = bosniaToday.plusDays(RECURRING_HORIZON_DAYS)
        )

        val mergedClasses = ScheduleMerger.merge(syncedClasses, generatedOccurrences).toMutableList()

        // Ensure any explicit preference on a recurring occurrence beyond the rolling horizon is also included.
        val knownIds = mergedClasses.map { it.id }.toHashSet()
        for (pref in preferences) {
            if (pref.classId !in knownIds && isGeneratedOccurrenceId(pref.classId)) {
                generateOneOff(database, pref.classId)?.let {
                    mergedClasses += it
                    knownIds += it.id
                }
            }
        }

        val gracePeriodMs = store.gracePeriodMinutes * 60_000L
        val activeReminderKeys = HashSet<String>()

        for (classEntity in mergedClasses) {
            val pref = prefsByClassId[classEntity.id]
            val keys = reconcileClassInternal(
                context = context,
                database = database,
                classEntity = classEntity,
                preference = pref,
                store = store,
                nowEpochMs = nowEpochMs,
                gracePeriodMs = gracePeriodMs,
                allowImmediateCatchUp = allowImmediateCatchUp
            )
            activeReminderKeys.addAll(keys)
        }

        // Cancel and prune any stale scheduled_reminders that are no longer active.
        val existingScheduled = database.scheduledReminderDao().getAllScheduledSnapshot()
        for (stale in existingScheduled) {
            if (stale.reminderKey !in activeReminderKeys) {
                ReminderScheduler.cancel(context, stale.reminderKey, stale.stage)
                database.scheduledReminderDao().deleteByReminderKey(stale.reminderKey)
            }
        }

        // Clean up very old reminder state rows (> 30 days).
        val retentionCutoff = nowEpochMs - PAST_RETENTION_DAYS * 24L * 60L * 60_000L
        database.classReminderStateDao().pruneOlderThan(retentionCutoff)
    }

    /**
     * Re-arms persisted scheduled reminders and reconciles catch-ups after boot,
     * app update, timezone change, or exact-alarm permission change.
     */
    suspend fun reconcileArmedAlarms(
        context: Context,
        database: AppDatabase,
        nowEpochMs: Long = System.currentTimeMillis()
    ) {
        reconcileAll(
            context = context,
            database = database,
            nowEpochMs = nowEpochMs,
            allowImmediateCatchUp = true
        )
    }

    /**
     * Reconciles both stages for a single class after saving an individual preference.
     */
    suspend fun scheduleForPreference(
        context: Context,
        database: AppDatabase,
        preference: ReminderPreferenceEntity,
        overrides: List<ScheduleOverrideEntity> = emptyList(),
        nowEpochMs: Long = System.currentTimeMillis(),
        allowImmediateCatchUp: Boolean = true
    ) = reconcileMutex.withLock {
        val store = ReminderGlobalStore.getInstance(context)
        val classEntity = if (isGeneratedOccurrenceId(preference.classId)) {
            generateOneOff(database, preference.classId)
        } else {
            database.classDao().getClassByIdSnapshot(preference.classId)
        }

        if (classEntity == null) {
            val existing = database.scheduledReminderDao().getScheduledForClass(preference.classId)
            existing.forEach { ReminderScheduler.cancel(context, it.reminderKey, it.stage) }
            if (existing.isNotEmpty()) database.scheduledReminderDao().deleteByClassId(preference.classId)
            return@withLock
        }

        val activeKeys = reconcileClassInternal(
            context = context,
            database = database,
            classEntity = classEntity,
            preference = preference,
            store = store,
            nowEpochMs = nowEpochMs,
            gracePeriodMs = store.gracePeriodMinutes * 60_000L,
            allowImmediateCatchUp = allowImmediateCatchUp
        )

        val existingForClass = database.scheduledReminderDao().getScheduledForClass(classEntity.id)
        for (row in existingForClass) {
            if (row.reminderKey !in activeKeys) {
                ReminderScheduler.cancel(context, row.reminderKey, row.stage)
                database.scheduledReminderDao().deleteByReminderKey(row.reminderKey)
            }
        }
    }

    private suspend fun reconcileClassInternal(
        context: Context,
        database: AppDatabase,
        classEntity: ClassEntity,
        preference: ReminderPreferenceEntity?,
        store: ReminderGlobalStore,
        nowEpochMs: Long,
        gracePeriodMs: Long,
        allowImmediateCatchUp: Boolean
    ): Set<String> {
        val classId = classEntity.id
        val scheduledDao = database.scheduledReminderDao()
        val stateDao = database.classReminderStateDao()

        val authoritativeStartMs = resolveAuthoritativeStartMs(classEntity)
        val occurrenceKey = ClassReminderStateEntity.buildOccurrenceKey(classId, classEntity.sourceDate)
        val studentTz = store.selectedTimezone
        val localClassTimeDisplay =
            "${TimezoneHelper.formatToLocalTime(authoritativeStartMs, studentTz)} (${TimezoneHelper.getTimezoneShortLabel(studentTz)})"

        // 1. Handle cancelled classes immediately.
        if (classEntity.status == ClassEntity.STATUS_CANCELLED) {
            val existingAlarms = scheduledDao.getScheduledForClass(classId)
            existingAlarms.forEach { ReminderScheduler.cancel(context, it.reminderKey, it.stage) }
            ReminderScheduler.cancelBothStages(context, classId, classEntity.sourceDate)
            if (existingAlarms.isNotEmpty()) scheduledDao.deleteByClassId(classId)
            if (preference != null) database.reminderDao().deletePreferenceForClass(classId)

            val prevState = stateDao.getByOccurrenceKey(occurrenceKey)
            val cancelledState = (prevState ?: ClassReminderStateEntity(
                occurrenceKey = occurrenceKey,
                classId = classId,
                classDate = classEntity.sourceDate,
                subject = classEntity.subject,
                sourceTimezone = classEntity.sourceTimezone,
                classStartEpochMs = authoritativeStartMs,
                advanceTriggerEpochMs = authoritativeStartMs,
                finalTriggerEpochMs = authoritativeStartMs
            )).copy(
                advanceScheduledStatus = ClassReminderStateEntity.STATUS_CANCELLED,
                finalScheduledStatus = ClassReminderStateEntity.STATUS_CANCELLED,
                updatedAtEpochMs = nowEpochMs
            )
            stateDao.upsert(cancelledState)
            if (existingAlarms.isNotEmpty() || prevState != null) {
                ReminderDiagnosticsLogger.log(
                    context = context,
                    database = database,
                    eventType = "CANCEL",
                    classId = classId,
                    subject = classEntity.subject,
                    stage = "BOTH",
                    sourceTimezone = classEntity.sourceTimezone,
                    localClassTime = localClassTimeDisplay,
                    advanceTriggerMs = cancelledState.advanceTriggerEpochMs,
                    finalTriggerMs = authoritativeStartMs,
                    resultStatus = ClassReminderStateEntity.STATUS_CANCELLED,
                    message = "Cancelled alarms for cancelled class"
                )
            }
            return emptySet()
        }

        val leadMinutes = (preference?.leadMinutes ?: store.leadMinutes)
            .coerceIn(MIN_LEAD_MINUTES, MAX_LEAD_MINUTES)
        val soundEnabled = preference?.isAlarmEnabled ?: store.isAlarmEnabled
        val soundTone = preference?.soundTone ?: store.soundTone
        val vibrateEnabled = preference?.vibrate ?: store.isVibrateEnabled

        val advanceTriggerMs = authoritativeStartMs - leadMinutes * 60_000L
        val finalTriggerMs = authoritativeStartMs

        // Ignore classes far in the past unless they already have a state/alarm to clean up.
        val isFarPast = nowEpochMs > finalTriggerMs + gracePeriodMs
        val existingState = stateDao.getByOccurrenceKey(occurrenceKey)
        val existingScheduled = scheduledDao.getScheduledForClass(classId)

        if (isFarPast && existingState == null && existingScheduled.isEmpty()) {
            return emptySet()
        }

        // Detect rescheduling: if class start time changed, cancel old alarms and reset delivery flags.
        val wasRescheduled = existingState != null && existingState.classStartEpochMs != authoritativeStartMs
        if (wasRescheduled) {
            existingScheduled.forEach { ReminderScheduler.cancel(context, it.reminderKey, it.stage) }
            ReminderScheduler.cancelBothStages(context, classId, classEntity.sourceDate)
            scheduledDao.deleteByClassId(classId)
            ReminderDiagnosticsLogger.log(
                context = context,
                database = database,
                eventType = "RESCHEDULE",
                classId = classId,
                subject = classEntity.subject,
                stage = "BOTH",
                sourceTimezone = classEntity.sourceTimezone,
                localClassTime = localClassTimeDisplay,
                advanceTriggerMs = advanceTriggerMs,
                finalTriggerMs = finalTriggerMs,
                resultStatus = "RESET_FOR_NEW_TIME",
                message = "Class time changed from ${existingState!!.classStartEpochMs} to $authoritativeStartMs"
            )
        }

        var advanceDelivered = if (wasRescheduled) false else (existingState?.advanceDelivered ?: false)
        var advanceDeliveredAt = if (wasRescheduled) 0L else (existingState?.advanceDeliveredAtEpochMs ?: 0L)
        var finalDelivered = if (wasRescheduled) false else (existingState?.finalDelivered ?: false)
        var finalDeliveredAt = if (wasRescheduled) 0L else (existingState?.finalDeliveredAtEpochMs ?: 0L)
        val dismissed = if (wasRescheduled) false else (existingState?.dismissed ?: false)

        var advanceStatus = existingState?.advanceScheduledStatus ?: ClassReminderStateEntity.STATUS_PENDING
        var finalStatus = existingState?.finalScheduledStatus ?: ClassReminderStateEntity.STATUS_PENDING

        val activeKeys = HashSet<String>()
        val advanceKey = reminderKeyFor(classId, classEntity.sourceDate, ScheduledReminderEntity.STAGE_ADVANCE)
        val finalKey = reminderKeyFor(classId, classEntity.sourceDate, ScheduledReminderEntity.STAGE_FINAL)
        val scheduleId = parseGeneratedOccurrenceId(classId)?.first ?: ""

        // ------------------ STAGE 1: ADVANCE REMINDER ------------------
        val advancePreferenceActive = preference == null || preference.isAlarmEnabled || preference.vibrate
        if (!advancePreferenceActive) {
            ReminderScheduler.cancel(context, advanceKey, ScheduledReminderEntity.STAGE_ADVANCE)
            scheduledDao.deleteByReminderKey(advanceKey)
            advanceStatus = ClassReminderStateEntity.STATUS_DISABLED
        } else if (advanceDelivered) {
            advanceStatus = ClassReminderStateEntity.STATUS_DELIVERED
        } else if (nowEpochMs < advanceTriggerMs) {
            val advanceEntity = ScheduledReminderEntity(
                reminderKey = advanceKey,
                classId = classId,
                occurrenceDate = classEntity.sourceDate,
                scheduleId = scheduleId,
                triggerEpochMs = advanceTriggerMs,
                leadMinutes = leadMinutes,
                isAlarmEnabled = soundEnabled,
                soundTone = soundTone,
                vibrate = vibrateEnabled,
                subject = classEntity.subject,
                classType = classEntity.classType,
                groupName = classEntity.groupName,
                attendanceMode = classEntity.attendanceMode,
                sourceStartTime = classEntity.sourceStartTime,
                sourceEndTime = classEntity.sourceEndTime,
                locationOrUrl = classEntity.locationOrUrl,
                stage = ScheduledReminderEntity.STAGE_ADVANCE,
                classStartEpochMs = authoritativeStartMs,
                scheduleStatus = ClassReminderStateEntity.STATUS_PENDING
            )
            val outcome = ReminderScheduler.schedule(context, advanceEntity)
            advanceStatus = outcome.statusCode
            if (outcome !is ScheduleOutcome.Failed) {
                scheduledDao.upsert(advanceEntity.copy(scheduleStatus = advanceStatus))
                activeKeys += advanceKey
            }
            if (existingState?.advanceTriggerEpochMs != advanceTriggerMs ||
                existingState.advanceScheduledStatus != advanceStatus
            ) {
                ReminderDiagnosticsLogger.log(
                    context = context,
                    database = database,
                    eventType = "SCHEDULE",
                    classId = classId,
                    subject = classEntity.subject,
                    stage = ScheduledReminderEntity.STAGE_ADVANCE,
                    sourceTimezone = classEntity.sourceTimezone,
                    localClassTime = localClassTimeDisplay,
                    advanceTriggerMs = advanceTriggerMs,
                    finalTriggerMs = finalTriggerMs,
                    resultStatus = advanceStatus,
                    message = when (outcome) {
                        ScheduleOutcome.Exact -> "Exact advance alarm armed (${leadMinutes}m before)"
                        is ScheduleOutcome.InexactFallback -> outcome.reason
                        is ScheduleOutcome.Failed -> outcome.reason
                    }
                )
            }
        } else if (nowEpochMs < finalTriggerMs) {
            // Missed advance window while before class start -> trigger ONE catch-up notification.
            ReminderScheduler.cancel(context, advanceKey, ScheduledReminderEntity.STAGE_ADVANCE)
            scheduledDao.deleteByReminderKey(advanceKey)
            if (allowImmediateCatchUp) {
                val minsLeft = ceil((finalTriggerMs - nowEpochMs) / 60_000.0).toInt().coerceAtLeast(1)
                val delivered = NotificationHelper.sendAdvanceReminderNotification(
                    context = context,
                    notificationId = NotificationHelper.notificationIdFor(
                        occurrenceKey,
                        ScheduledReminderEntity.STAGE_ADVANCE
                    ),
                    subject = classEntity.subject,
                    minutesUntilStart = minsLeft,
                    localTimeDisplay = localClassTimeDisplay,
                    locationOrUrl = classEntity.locationOrUrl,
                    isCatchUp = true,
                    soundEnabled = soundEnabled,
                    vibrate = vibrateEnabled
                )
                if (delivered) {
                    advanceDelivered = true
                    advanceDeliveredAt = nowEpochMs
                    advanceStatus = ClassReminderStateEntity.STATUS_CATCHUP_TRIGGERED
                } else {
                    advanceStatus = ClassReminderStateEntity.STATUS_BLOCKED_NO_PERMISSION
                }
                ReminderDiagnosticsLogger.log(
                    context = context,
                    database = database,
                    eventType = "CATCHUP",
                    classId = classId,
                    subject = classEntity.subject,
                    stage = ScheduledReminderEntity.STAGE_ADVANCE,
                    sourceTimezone = classEntity.sourceTimezone,
                    localClassTime = localClassTimeDisplay,
                    advanceTriggerMs = advanceTriggerMs,
                    finalTriggerMs = finalTriggerMs,
                    resultStatus = advanceStatus,
                    message = if (delivered) {
                        "Triggered immediate catch-up advance notification ($minsLeft min remaining)"
                    } else {
                        "Catch-up advance skipped: POST_NOTIFICATIONS permission denied"
                    }
                )
            }
        } else {
            // Class already started: skip stale advance reminder.
            ReminderScheduler.cancel(context, advanceKey, ScheduledReminderEntity.STAGE_ADVANCE)
            scheduledDao.deleteByReminderKey(advanceKey)
            if (!advanceDelivered) {
                advanceStatus = ClassReminderStateEntity.STATUS_SKIPPED_PAST
            }
        }

        // ------------------ STAGE 2: FINAL CLASS-START REMINDER ------------------
        // Always evaluated independently of Stage 1.
        if (finalDelivered) {
            finalStatus = ClassReminderStateEntity.STATUS_DELIVERED
        } else if (nowEpochMs < finalTriggerMs) {
            val finalEntity = ScheduledReminderEntity(
                reminderKey = finalKey,
                classId = classId,
                occurrenceDate = classEntity.sourceDate,
                scheduleId = scheduleId,
                triggerEpochMs = finalTriggerMs,
                leadMinutes = 0,
                isAlarmEnabled = soundEnabled,
                soundTone = soundTone,
                vibrate = vibrateEnabled,
                subject = classEntity.subject,
                classType = classEntity.classType,
                groupName = classEntity.groupName,
                attendanceMode = classEntity.attendanceMode,
                sourceStartTime = classEntity.sourceStartTime,
                sourceEndTime = classEntity.sourceEndTime,
                locationOrUrl = classEntity.locationOrUrl,
                stage = ScheduledReminderEntity.STAGE_FINAL,
                classStartEpochMs = authoritativeStartMs,
                scheduleStatus = ClassReminderStateEntity.STATUS_PENDING
            )
            val outcome = ReminderScheduler.schedule(context, finalEntity)
            finalStatus = outcome.statusCode
            if (outcome !is ScheduleOutcome.Failed) {
                scheduledDao.upsert(finalEntity.copy(scheduleStatus = finalStatus))
                activeKeys += finalKey
            }
            if (existingState?.finalTriggerEpochMs != finalTriggerMs ||
                existingState.finalScheduledStatus != finalStatus
            ) {
                ReminderDiagnosticsLogger.log(
                    context = context,
                    database = database,
                    eventType = "SCHEDULE",
                    classId = classId,
                    subject = classEntity.subject,
                    stage = ScheduledReminderEntity.STAGE_FINAL,
                    sourceTimezone = classEntity.sourceTimezone,
                    localClassTime = localClassTimeDisplay,
                    advanceTriggerMs = advanceTriggerMs,
                    finalTriggerMs = finalTriggerMs,
                    resultStatus = finalStatus,
                    message = when (outcome) {
                        ScheduleOutcome.Exact -> "Exact class-start alarm armed"
                        is ScheduleOutcome.InexactFallback -> outcome.reason
                        is ScheduleOutcome.Failed -> outcome.reason
                    }
                )
            }
        } else if (nowEpochMs <= finalTriggerMs + gracePeriodMs) {
            // At class start or within 5-minute grace period -> trigger ONE final notification immediately.
            ReminderScheduler.cancel(context, finalKey, ScheduledReminderEntity.STAGE_FINAL)
            scheduledDao.deleteByReminderKey(finalKey)
            if (allowImmediateCatchUp) {
                val delivered = NotificationHelper.sendClassStartNotification(
                    context = context,
                    notificationId = NotificationHelper.notificationIdFor(
                        occurrenceKey,
                        ScheduledReminderEntity.STAGE_FINAL
                    ),
                    subject = classEntity.subject,
                    localTimeDisplay = localClassTimeDisplay,
                    locationOrUrl = classEntity.locationOrUrl,
                    soundEnabled = soundEnabled,
                    vibrate = vibrateEnabled
                )
                if (delivered) {
                    finalDelivered = true
                    finalDeliveredAt = nowEpochMs
                    finalStatus = ClassReminderStateEntity.STATUS_CATCHUP_TRIGGERED
                } else {
                    finalStatus = ClassReminderStateEntity.STATUS_BLOCKED_NO_PERMISSION
                }
                ReminderDiagnosticsLogger.log(
                    context = context,
                    database = database,
                    eventType = "CATCHUP",
                    classId = classId,
                    subject = classEntity.subject,
                    stage = ScheduledReminderEntity.STAGE_FINAL,
                    sourceTimezone = classEntity.sourceTimezone,
                    localClassTime = localClassTimeDisplay,
                    advanceTriggerMs = advanceTriggerMs,
                    finalTriggerMs = finalTriggerMs,
                    resultStatus = finalStatus,
                    message = if (delivered) {
                        "Triggered immediate class-start catch-up notification within grace period"
                    } else {
                        "Class-start catch-up skipped: POST_NOTIFICATIONS permission denied"
                    }
                )
            }
        } else {
            // Class has been running longer than grace period -> do NOT send stale notifications.
            ReminderScheduler.cancel(context, finalKey, ScheduledReminderEntity.STAGE_FINAL)
            scheduledDao.deleteByReminderKey(finalKey)
            if (!finalDelivered) {
                finalStatus = ClassReminderStateEntity.STATUS_SKIPPED_STALE
            }
        }

        // Persist consolidated two-stage state in Room.
        stateDao.upsert(
            ClassReminderStateEntity(
                occurrenceKey = occurrenceKey,
                classId = classId,
                classDate = classEntity.sourceDate,
                subject = classEntity.subject,
                sourceTimezone = classEntity.sourceTimezone,
                classStartEpochMs = authoritativeStartMs,
                advanceTriggerEpochMs = advanceTriggerMs,
                finalTriggerEpochMs = finalTriggerMs,
                advanceScheduledStatus = advanceStatus,
                finalScheduledStatus = finalStatus,
                advanceDelivered = advanceDelivered,
                advanceDeliveredAtEpochMs = advanceDeliveredAt,
                finalDelivered = finalDelivered,
                finalDeliveredAtEpochMs = finalDeliveredAt,
                repeatCount = 0,
                dismissed = dismissed,
                updatedAtEpochMs = nowEpochMs
            )
        )

        return activeKeys
    }

    /**
     * Resolves canonical UTC epoch millis from the authoritative source date, time, and timezone
     * (Europe/Sarajevo), avoiding double conversion or fixed UTC offsets.
     */
    fun resolveAuthoritativeStartMs(classEntity: ClassEntity): Long {
        if (classEntity.sourceDate.isNotBlank() && classEntity.sourceStartTime.isNotBlank()) {
            val tz = classEntity.sourceTimezone.ifBlank { TimezoneHelper.BOSNIA_TIMEZONE }
            return TimezoneHelper.toUtcEpochMs(
                classEntity.sourceDate,
                classEntity.sourceStartTime,
                tz
            )
        }
        return classEntity.utcStartEpochMs
    }

    /**
     * Cancels all alarms and clears scheduled rows for a specific class.
     */
    suspend fun cancelForClass(context: Context, database: AppDatabase, classId: String) {
        val existing = database.scheduledReminderDao().getScheduledForClass(classId)
        existing.forEach { ReminderScheduler.cancel(context, it.reminderKey, it.stage) }
        database.scheduledReminderDao().deleteByClassId(classId)
        database.classReminderStateDao().getByClassId(classId).forEach { state ->
            ReminderScheduler.cancelBothStages(context, state.classId, state.classDate)
            database.classReminderStateDao().upsert(
                state.copy(
                    advanceScheduledStatus = ClassReminderStateEntity.STATUS_CANCELLED,
                    finalScheduledStatus = ClassReminderStateEntity.STATUS_CANCELLED,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    /** True when the occurrence date lands inside [zoneId] timezone's daylight saving "repeated hour". */
    fun isAmbiguousLocalTime(date: LocalDate, zoneId: ZoneId, hour: Int, minute: Int): Boolean {
        val transition = zoneId.rules.getTransition(java.time.LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)))
        return transition != null && !transition.isGap
    }
}
