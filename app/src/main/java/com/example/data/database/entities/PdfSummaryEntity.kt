package com.example.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_summaries")
data class PdfSummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val summaryText: String = "",
    val extractedText: String = "",
    val pageCount: Int = 1,
    val keyPointsJson: String = "", // JSON list or bullet points
    val flashcardsCount: Int = 0,
    val localFilePath: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

