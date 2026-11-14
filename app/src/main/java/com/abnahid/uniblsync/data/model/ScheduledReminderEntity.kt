package com.abnahid.uniblsync.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One scheduled reminder. Persisted so reminders survive process death and
 * device reboots: the boot receiver re-arms AlarmManager alarms from this table
 * without needing the class list or the UI.
 *
 * [reminderKey] is unique per class occurrence so overlapping recurrences never
 * collide on one PendingIntent request code.
 */
@Entity(
    tableName = "scheduled_reminders",
    indices = [Index("classId"), Index("triggerEpochMs")]
)
data class ScheduledReminderEntity(
    @PrimaryKey
    val reminderKey: String, // "reminder:{stage}:{classId}|{occurrenceDate}"
    val classId: String,
    val occurrenceDate: String, // yyyy-MM-dd in the university timezone
    val scheduleId: String, // "" for classes synced from the API
    val triggerEpochMs: Long,
    val leadMinutes: Int,
    val isAlarmEnabled: Boolean,
    val soundTone: String,
    val vibrate: Boolean,
    // Display snapshot for the notification, taken when the alarm is armed.
    val subject: String,
    val classType: String,
    val groupName: String,
    val attendanceMode: String,
    val sourceStartTime: String,
    val sourceEndTime: String,
    val locationOrUrl: String,
    val stage: String = STAGE_ADVANCE,
    val classStartEpochMs: Long = 0L,
    val scheduleStatus: String = "PENDING"
) {
    companion object {
        const val STAGE_ADVANCE = "ADVANCE"
        const val STAGE_FINAL = "FINAL"

        fun buildKey(
            classId: String,
            occurrenceDate: String,
            stage: String = STAGE_ADVANCE
        ): String = "reminder:$stage:$classId|$occurrenceDate"
    }
}
