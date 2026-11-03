package com.abnahid.uniblsync.data.schedule

import android.content.Context
import com.abnahid.uniblsync.data.local.AppDatabase
import com.abnahid.uniblsync.data.model.ClassEntity
import com.abnahid.uniblsync.data.model.RecurringScheduleEntity
import com.abnahid.uniblsync.data.model.ReminderPreferenceEntity
import com.abnahid.uniblsync.data.model.ScheduleOverrideEntity
import com.abnahid.uniblsync.data.model.ScheduledReminderEntity
import com.abnahid.uniblsync.util.ReminderScheduler
import java.time.LocalDate
import java.time.ZoneId

/**
 * Computes reminder trigger instants and keeps AlarmManager alarms and the
 * Room [ScheduledReminderEntity] table in a consistent, deduplicated state.
 *
 * Every reminder is identified by `reminder:{classId}|{occurrenceDate}` so two
 * occurrences of the same recurring schedule never share a PendingIntent.
 */
object ReminderReconciler {

    /** Offset used when a class definition provides no usable start instant. */
    private const val MIN_LEAD_MINUTES = 0
    const val MAX_LEAD_MINUTES = 60 * 24

    /**
     * Also resolves a schedule id + occurrence date from a generated occurrence
     * id such as `rec:{scheduleId}:{date}`.
     */
    private val GENERATED_ID_REGEX = Regex("^rec:(.+):(\\d{4}-\\d{2}-\\d{2})$")
    private const val ONE_TIME_ID_PREFIX = "ovr:"

    fun reminderKeyFor(classId: String, occurrenceDate: String): String =
        ScheduledReminderEntity.buildKey(classId, occurrenceDate)

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
        return ScheduleOccurrenceGenerator.generate(listOf(schedule), emptyList(), date, date).firstOrNull()
    }

    /**
     * Full reconciliation: recomputes every reminder from the current reminder
     * preferences and schedule data.
     *
     * - Expired or already-started classes are skipped and cleaned up.
     * - Cancelled classes or cancelled occurrences are skipped.
     * - Existing alarms are replaced, never duplicated.
     */
    suspend fun reconcileAll(
        context: Context,
        database: AppDatabase,
        overrides: List<ScheduleOverrideEntity> = emptyList()
    ) {
        val now = System.currentTimeMillis()
        val preferences = database.reminderDao().getAllPreferencesSnapshot()
        val classOverrides = if (overrides.isEmpty()) {
            database.scheduleOverrideDao().getAllOverridesSnapshot()
        } else overrides

        for (pref in preferences) {
            scheduleForPreference(context, database, pref, classOverrides, now)
        }

        // Drop scheduled rows whose preference disappeared or which expired long ago.
        val staleReminders = database.scheduledReminderDao().getAllScheduledSnapshot()
            .filter { reminder -> preferences.none { it.classId == reminder.classId } }
        for (stale in staleReminders) {
            ReminderScheduler.cancel(context, stale.reminderKey)
            database.scheduledReminderDao().deleteByReminderKey(stale.reminderKey)
        }
    }

    /**
     * Re-arms only the alarms that are already persisted. Used by the boot
     * receiver: no preference re-computation needed, just time validation.
     */
    suspend fun reconcileArmedAlarms(context: Context, database: AppDatabase) {
        val now = System.currentTimeMillis()
        val pending = database.scheduledReminderDao().getFutureScheduled(now)
        val all = database.scheduledReminderDao().getAllScheduledSnapshot()
        for (reminder in all) {
            if (reminder.triggerEpochMs <= now) {
                database.scheduledReminderDao().deleteByReminderKey(reminder.reminderKey)
                continue
            }
            if (reminder !in pending) continue
            ReminderScheduler.schedule(context, reminder)
        }
    }

    /** Schedules (or cancels) the reminder for one class based on its preference row. */
    suspend fun scheduleForPreference(
        context: Context,
        database: AppDatabase,
        preference: ReminderPreferenceEntity,
        overrides: List<ScheduleOverrideEntity> = emptyList(),
        nowEpochMs: Long = System.currentTimeMillis()
    ) {
        val classId = preference.classId
        val scheduledDao = database.scheduledReminderDao()

        // Always cancel whatever is armed for this class first, then recompute.
        val existing = scheduledDao.getScheduledForClass(classId)
        existing.forEach { ReminderScheduler.cancel(context, it.reminderKey) }
        if (existing.isNotEmpty()) scheduledDao.deleteByClassId(classId)

        // No lead time configured or reminders disabled: nothing to arm.
        if (preference.leadMinutes < MIN_LEAD_MINUTES || preference.leadMinutes > MAX_LEAD_MINUTES) return
        if (!preference.isAlarmEnabled && !preference.vibrate) return

        val target = if (isGeneratedOccurrenceId(classId)) {
            generateOneOff(database, classId)
        } else {
            database.classDao().getClassByIdSnapshot(classId)
        } ?: return // class no longer exists; preference cleaned elsewhere

        if (target.status == ClassEntity.STATUS_CANCELLED) {
            database.reminderDao().deletePreferenceForClass(classId)
            return
        }

        val triggerMs = target.utcStartEpochMs - preference.leadMinutes * 60_000L
        if (triggerMs <= nowEpochMs) return // already expired or the class already started

        val reminder = ScheduledReminderEntity(
            reminderKey = reminderKeyFor(classId, target.sourceDate),
            classId = classId,
            occurrenceDate = target.sourceDate,
            scheduleId = parseGeneratedOccurrenceId(classId)?.first ?: "",
            triggerEpochMs = triggerMs,
            leadMinutes = preference.leadMinutes,
            isAlarmEnabled = preference.isAlarmEnabled,
            soundTone = preference.soundTone,
            vibrate = preference.vibrate,
            subject = target.subject,
            classType = target.classType,
            groupName = target.groupName,
            attendanceMode = target.attendanceMode,
            sourceStartTime = target.sourceStartTime,
            sourceEndTime = target.sourceEndTime,
            locationOrUrl = target.locationOrUrl
        )
        scheduledDao.upsert(reminder)
        ReminderScheduler.schedule(context, reminder)
    }

    /**
     * Cancels reminders for cancelled occurrences and re-arms the rest. Called
     * after a sync or an override change so local alarms reflect the new truth.
     */
    suspend fun rescheduleKnownClasses(context: Context, database: AppDatabase) {
        reconcileAll(context, database)
    }

    /** True when the occurrence date lands inside [context] timezone's daylight saving "repeated hour". */
    fun isAmbiguousLocalTime(date: LocalDate, zoneId: ZoneId, hour: Int, minute: Int): Boolean {
        val before = zoneId.rules.getTransition(java.time.LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)))
        return before != null && !before.isGap
    }
}
