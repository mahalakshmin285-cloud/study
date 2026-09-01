package com.example.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_tasks")
data class StudyTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subject: String = "General",
    val dueDate: String, // YYYY-MM-DD
    val dueTime: String = "10:00 AM", // e.g. 10:00 AM, 04:30 PM
    val isCompleted: Boolean = false,
    val priority: String = "Medium", // Low, Medium, High
    val createdAt: Long = System.currentTimeMillis()
)
