package com.example.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pomodoro_logs")
data class PomodoroLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val minutesSpent: Int,
    val subject: String = "Study Session",
    val timestamp: Long = System.currentTimeMillis()
)
