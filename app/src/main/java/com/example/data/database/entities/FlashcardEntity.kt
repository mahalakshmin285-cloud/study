package com.example.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "flashcards")
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckName: String,
    val question: String,
    val answer: String,
    val isBookmarked: Boolean = false,
    val isLearned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
