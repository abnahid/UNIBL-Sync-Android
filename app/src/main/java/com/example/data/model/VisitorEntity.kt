package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "visitors")
data class VisitorEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val email: String,
    val purpose: String,
    val targetGroup: String = "All",
    val registeredAtEpochMs: Long = System.currentTimeMillis()
)
