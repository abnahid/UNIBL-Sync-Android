package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey
    val id: String,
    val action: String, // CREATED, UPDATED, RESCHEDULED, CANCELLED, IMPORTED
    val subject: String,
    val details: String,
    val timestampEpochMs: Long = System.currentTimeMillis()
)
