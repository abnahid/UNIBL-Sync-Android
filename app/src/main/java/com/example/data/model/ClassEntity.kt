package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "classes")
data class ClassEntity(
    @PrimaryKey
    val id: String,
    val subject: String,
    val classType: String, // "Lecture", "Practical", "Make up Lecture", "Make up practical", "Seminar"
    val instructor: String = "",
    val sourceDate: String, // YYYY-MM-DD
    val sourceStartTime: String, // e.g., "05:00 PM" or "17:00"
    val sourceEndTime: String, // e.g., "06:30 PM" or "18:30"
    val sourceTimezone: String = "Europe/Sarajevo",
    val utcStartEpochMs: Long,
    val utcEndEpochMs: Long,
    val groupName: String = "All", // "All", "Group 1", "Group 2", "Group 3", "Group 4"
    val attendanceMode: String = "In person + Online", // "In-Person", "Online", "In person + Online"
    val platform: String = PLATFORM_TEAMS, // Microsoft Teams, Google Meet, Zoom, Classroom
    val teamsChannel: String = "General",
    val locationOrUrl: String = "",
    val status: String = STATUS_SCHEDULED, // SCHEDULED, RESCHEDULED, CANCELLED
    val notes: String = "",
    val updatedAtEpochMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_SCHEDULED = "SCHEDULED"
        const val STATUS_RESCHEDULED = "RESCHEDULED"
        const val STATUS_CANCELLED = "CANCELLED"

        const val MODE_ONLINE = "Online"
        const val MODE_IN_PERSON = "In-Person"
        const val MODE_HYBRID = "In person + Online"

        const val PLATFORM_TEAMS = "Microsoft Teams"
        const val PLATFORM_MEET = "Google Meet"
        const val PLATFORM_ZOOM = "Zoom"
        const val PLATFORM_CLASSROOM = "Classroom"
    }
}
