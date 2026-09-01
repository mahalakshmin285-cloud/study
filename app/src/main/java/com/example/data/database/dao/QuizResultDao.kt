package com.example.data.database.dao

import androidx.room.*
import com.example.data.database.entities.QuizResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuizResultDao {
    @Query("SELECT * FROM quiz_results ORDER BY dateTaken DESC")
    fun getAllQuizResults(): Flow<List<QuizResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizResult(quizResult: QuizResultEntity): Long

    @Query("DELETE FROM quiz_results WHERE id = :id")
    suspend fun deleteQuizResultById(id: Long)
}
