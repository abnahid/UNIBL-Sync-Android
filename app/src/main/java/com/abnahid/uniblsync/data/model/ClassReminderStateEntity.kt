package com.abnahid.uniblsync.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent state per class occurrence for two-stage reminders
 * (advance reminder + exact class-start notification).
 *
 * Guarantees idempotency and duplicate prevention across app launches,
 * background syncs, timezone changes, and device reboots.
 */
@Entity(
    tableName = "class_reminder_states",
    indices = [
        Index("classId"),
        Index("classDate"),
        Index("classStartEpochMs")
    ]
)
data class ClassReminderStateEntity(
    @PrimaryKey
    val occurrenceKey: String, // "$classId|$classDate"
    val classId: String,
    val classDate: String,
    val subject: String,
    val sourceTimezone: String,
    val classStartEpochMs: Long,
    val advanceTriggerEpochMs: Long,
    val finalTriggerEpochMs: Long,
    val advanceScheduledStatus: String = STATUS_PENDING,
    val finalScheduledStatus: String = STATUS_PENDING,
    val advanceDelivered: Boolean = false,
    val advanceDeliveredAtEpochMs: Long = 0L,
    val finalDelivered: Boolean = false,
    val finalDeliveredAtEpochMs: Long = 0L,
    val repeatCount: Int = 0, // Kept at 0: no continuous repeat loops per UX policy
    val dismissed: Boolean = false,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SCHEDULED_EXACT = "SCHEDULED_EXACT"
        const val STATUS_SCHEDULED_INEXACT = "SCHEDULED_INEXACT"
        const val STATUS_CATCHUP_TRIGGERED = "CATCHUP_TRIGGERED"
        const val STATUS_DELIVERED = "DELIVERED"
        const val STATUS_SKIPPED_PAST = "SKIPPED_PAST"
        const val STATUS_SKIPPED_STALE = "SKIPPED_STALE"
        const val STATUS_BLOCKED_NO_PERMISSION = "BLOCKED_NO_PERMISSION"
        const val STATUS_DISABLED = "DISABLED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELLED = "CANCELLED"

        fun buildOccurrenceKey(classId: String, classDate: String): String =
            "$classId|$classDate"
    }
}
