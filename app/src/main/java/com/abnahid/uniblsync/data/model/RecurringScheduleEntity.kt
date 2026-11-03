package com.abnahid.uniblsync.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A recurring schedule definition.
 *
 * Weekly occurrences are generated on demand for a bounded date range instead of
 * persisting one database row per class date. [explicitDates] can list concrete
 * dates (comma separated, yyyy-MM-dd) for classes that only happen on selected
 * weekdays (e.g. fortnight alternating practicals); when empty the schedule
 * simply repeats weekly on [weekdayIso].
 *
 * All times are announced in the university timezone (Europe/Sarajevo).
 */
@Entity(
    tableName = "recurring_schedule",
    indices = [Index("weekdayIso"), Index("subject")]
)
data class RecurringScheduleEntity(
    @PrimaryKey
    val id: String,
    val subject: String,
    val classType: String, // Lecture, Practical
    val weekdayIso: Int, // java.time.DayOfWeek value: 1 = Monday .. 7 = Sunday
    val sourceStartTime: String, // "HH:mm" in university timezone
    val sourceEndTime: String, // "HH:mm" in university timezone
    val sourceTimezone: String = "Europe/Sarajevo",
    val groupName: String = "All",
    val attendanceMode: String = "In-Person",
    val platform: String = "Microsoft Teams",
    val locationOrUrl: String = "",
    val instructor: String = "",
    val notes: String = "",
    val effectiveFromIsoDate: String, // yyyy-MM-dd
    val effectiveToIsoDate: String = "", // "" = open ended
    val explicitDates: String = "", // comma separated yyyy-MM-dd; only these dates occur when non-empty
    val isActive: Boolean = true,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_LECTURE = "Lecture"
        const val TYPE_PRACTICAL = "Practical"

        const val ACTION_CANCEL = "CANCEL"
        const val ACTION_UPDATE = "UPDATE"
        const val ACTION_ADD = "ADD"
    }
}
