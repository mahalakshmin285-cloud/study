package com.example.data.database.dao

import androidx.room.*
import com.example.data.database.entities.PdfSummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfSummaryDao {
    @Query("SELECT * FROM pdf_summaries ORDER BY createdAt DESC")
    fun getAllPdfSummaries(): Flow<List<PdfSummaryEntity>>

    @Query("SELECT * FROM pdf_summaries WHERE fileName = :fileName LIMIT 1")
    suspend fun getSummaryByFileName(fileName: String): PdfSummaryEntity?

    @Query("SELECT * FROM pdf_summaries WHERE id = :id LIMIT 1")
    suspend fun getSummaryById(id: Long): PdfSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPdfSummary(summary: PdfSummaryEntity): Long

    @Update
    suspend fun updatePdfSummary(summary: PdfSummaryEntity)

    @Query("DELETE FROM pdf_summaries WHERE id = :id")
    suspend fun deletePdfSummaryById(id: Long)
}

