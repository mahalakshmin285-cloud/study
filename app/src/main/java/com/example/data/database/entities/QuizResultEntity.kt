package com.example.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quiz_results")
data class QuizResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topic: String,
    val score: Int,
    val totalQuestions: Int,
    val percentage: Int,
    val dateTaken: Long = System.currentTimeMillis()
)
