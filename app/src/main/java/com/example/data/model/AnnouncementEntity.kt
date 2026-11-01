package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "announcements")
data class AnnouncementEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val message: String,
    val priority: String = "NORMAL", // NORMAL, URGENT, INFO
    val targetGroup: String = "All",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
