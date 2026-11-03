package com.abnahid.uniblsync.data.schedule

import com.abnahid.uniblsync.data.model.ClassEntity
import com.abnahid.uniblsync.data.model.RecurringScheduleEntity
import com.abnahid.uniblsync.data.model.ScheduleOverrideEntity
import com.abnahid.uniblsync.util.TimezoneHelper
import java.time.LocalDate

/**
 * Generates class occurrences for a bounded date range from recurring schedule
 * definitions and date specific overrides. Occurrences are computed on demand
 * (Room stays the source of definitions) so no database row is created per date.
 *
 * Occurrence ids are deterministic: `rec:{scheduleId}:{date}`. One time classes
 * added by an override get `ovr:{overrideId}`. Deterministic ids keep filtering,
 * reminder preferences and detail sheets stable across regenerations.
 */
object ScheduleOccurrenceGenerator {

    fun generate(
        schedules: List<RecurringScheduleEntity>,
        overrides: List<ScheduleOverrideEntity>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ClassEntity> {
        require(!endDate.isBefore(startDate)) { "endDate must not precede startDate" }

        val overridesByScheduleDate = overrides
            .filter { it.scheduleId.isNotBlank() }
            .groupBy { it.scheduleId to it.occurrenceDate }
        val addOverrides = overrides.filter { it.scheduleId.isBlank() && it.changeType == RecurringScheduleEntity.ACTION_ADD }

        val occurrences = ArrayList<ClassEntity>()

        var date = startDate
        while (!date.isAfter(endDate)) {
            val dateStr = date.toString()
            for (schedule in schedules) {
                if (!schedule.isActive) continue
                if (!appliesTo(schedule, date)) continue

                val override = overridesByScheduleDate[schedule.id to dateStr]?.firstOrNull()
                if (override?.changeType == RecurringScheduleEntity.ACTION_CANCEL) {
                    // Cancelled: keep the occurrence visible with CANCELLED status.
                    // Any field deltas (e.g. a cancellation note) still apply.
                    occurrences += toClassEntity(schedule, date, status = ClassEntity.STATUS_CANCELLED, override = override)
                    continue
                }
                occurrences += toClassEntity(schedule, date, status = ClassEntity.STATUS_SCHEDULED, override = override)
            }
            date = date.plusDays(1)
        }

        // One time classes added by overrides.
        for (add in addOverrides) {
            val parsed = runCatching { LocalDate.parse(add.occurrenceDate) }.getOrNull() ?: continue
            if (parsed.isBefore(startDate) || parsed.isAfter(endDate)) continue
            occurrences += addOverrideToClassEntity(add)
        }

        return occurrences
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.utcStartEpochMs }, { it.subject }))
    }

    private fun appliesTo(schedule: RecurringScheduleEntity, date: LocalDate): Boolean {
        if (parseExplicitDates(schedule.explicitDates).contains(date.toString())) return true
        if (schedule.explicitDates.isNotBlank()) return false // explicit-date schedules only occur on listed dates
        if (date.dayOfWeek.value != schedule.weekdayIso) return false
        if (date.toString() < schedule.effectiveFromIsoDate) return false
        if (schedule.effectiveToIsoDate.isNotBlank() && date.toString() > schedule.effectiveToIsoDate) return false
        return true
    }

    fun parseExplicitDates(raw: String): List<String> =
        raw.split(',').mapNotNull { it.trim().takeIf { s -> s.length == 10 } }

    private fun toClassEntity(
        schedule: RecurringScheduleEntity,
        date: LocalDate,
        status: String,
        override: ScheduleOverrideEntity?
    ): ClassEntity {
        val classType = override?.classType ?: schedule.classType
        val startTime = override?.sourceStartTime ?: schedule.sourceStartTime
        val endTime = override?.sourceEndTime ?: schedule.sourceEndTime
        val groupName = override?.groupName ?: schedule.groupName
        val attendanceMode = override?.attendanceMode ?: schedule.attendanceMode
        val location = override?.locationOrUrl ?: schedule.locationOrUrl
        val instructor = override?.instructor ?: schedule.instructor
        val platform = override?.platform ?: schedule.platform
        val notes = listOfNotNull(schedule.notes.trim().takeIf { it.isNotBlank() }, override?.notes?.trim()?.takeIf { it.isNotBlank() })
            .joinToString(" • ")

        return ClassEntity(
            id = "rec:${schedule.id}:$date",
            subject = schedule.subject,
            classType = classType,
            instructor = instructor,
            sourceDate = date.toString(),
            sourceStartTime = startTime,
            sourceEndTime = endTime,
            sourceTimezone = schedule.sourceTimezone,
            utcStartEpochMs = TimezoneHelper.toUtcEpochMs(date.toString(), startTime, schedule.sourceTimezone),
            utcEndEpochMs = TimezoneHelper.toUtcEpochMs(date.toString(), endTime, schedule.sourceTimezone),
            groupName = groupName,
            attendanceMode = attendanceMode,
            platform = platform,
            teamsChannel = "",
            locationOrUrl = location,
            status = status,
            notes = notes
        )
    }

    private fun addOverrideToClassEntity(override: ScheduleOverrideEntity): ClassEntity {
        val type = override.classType ?: RecurringScheduleEntity.TYPE_LECTURE
        val start = override.sourceStartTime ?: "17:00"
        val end = override.sourceEndTime ?: start

        return ClassEntity(
            id = "ovr:${override.id}",
            subject = override.subject?.trim().orEmpty().ifBlank { "Additional class" },
            classType = type,
            instructor = override.instructor ?: "",
            sourceDate = override.occurrenceDate,
            sourceStartTime = start,
            sourceEndTime = end,
            sourceTimezone = TimezoneHelper.BOSNIA_TIMEZONE,
            utcStartEpochMs = TimezoneHelper.toUtcEpochMs(override.occurrenceDate, start, TimezoneHelper.BOSNIA_TIMEZONE),
            utcEndEpochMs = TimezoneHelper.toUtcEpochMs(override.occurrenceDate, end, TimezoneHelper.BOSNIA_TIMEZONE),
            groupName = override.groupName ?: "All",
            attendanceMode = override.attendanceMode ?: ClassEntity.MODE_IN_PERSON,
            platform = override.platform ?: ClassEntity.PLATFORM_TEAMS,
            teamsChannel = "",
            locationOrUrl = override.locationOrUrl ?: "",
            status = ClassEntity.STATUS_SCHEDULED,
            notes = ""
        )
    }
}
