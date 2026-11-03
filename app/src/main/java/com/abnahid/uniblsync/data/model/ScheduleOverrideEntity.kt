package com.abnahid.uniblsync.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A date specific change to one class occurrence.
 *
 * - [ACTION_CANCEL] cancels one date of a recurring schedule (or one stored class).
 * - [ACTION_UPDATE] changes fields for one date only; the recurring definition is untouched.
 * - [ACTION_ADD] creates a one time class (stored class data lives in the override itself).
 *
 * Overrides never modify the recurring definition.
 */
@Entity(
    tableName = "schedule_overrides",
    indices = [Index("scheduleId"), Index("occurrenceDate")]
)
data class ScheduleOverrideEntity(
    @PrimaryKey
    val id: String,
    val scheduleId: String = "", // "" for one time additions
    val occurrenceDate: String, // yyyy-MM-dd in the university timezone
    val changeType: String, // RecurringScheduleEntity.ACTION_* constant
    val subject: String? = null, // required for ACTION_ADD one time classes
    val classType: String? = null,
    val sourceStartTime: String? = null,
    val sourceEndTime: String? = null,
    val groupName: String? = null,
    val attendanceMode: String? = null,
    val locationOrUrl: String? = null,
    val instructor: String? = null,
    val platform: String? = null,
    val notes: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
) {
    fun hasFieldChanges(): Boolean = listOf(
        classType, sourceStartTime, sourceEndTime, groupName,
        attendanceMode, locationOrUrl, instructor, platform, notes
    ).any { !it.isNullOrBlank() }}
