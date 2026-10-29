package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminder_preferences")
data class ReminderPreferenceEntity(
    @PrimaryKey
    val classId: String,
    val leadMinutes: Int = 20, // 5, 10, 15, 20, 30, 60
    val isAlarmEnabled: Boolean = true,
    val soundTone: String = "Gentle Chime", // Gentle Chime, Digital Beep, Soft Bell, Classic Alarm
    val vibrate: Boolean = true
)
