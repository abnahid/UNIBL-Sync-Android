package com.abnahid.uniblsync.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Structured diagnostic log entry for reminder scheduling, permission checks,
 * catch-up recovery, receiver execution, and notification delivery.
 * Contains no personally identifiable student data.
 */
@Entity(
    tableName = "reminder_diagnostic_logs",
    indices = [Index("timestampEpochMs")]
)
data class ReminderDiagnosticLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val eventType: String, // SCHEDULE, CATCHUP, RECEIVER, DELIVERY, CANCEL, PERMISSION, ERROR
    val classId: String,
    val subject: String,
    val stage: String, // ADVANCE, FINAL, SYSTEM
    val sourceTimezone: String,
    val localClassTime: String,
    val advanceTriggerMs: Long = 0L,
    val finalTriggerMs: Long = 0L,
    val resultStatus: String,
    val exactAlarmGranted: Boolean,
    val notificationPermissionGranted: Boolean,
    val repeatCount: Int = 0,
    val dismissed: Boolean = false,
    val message: String
)
