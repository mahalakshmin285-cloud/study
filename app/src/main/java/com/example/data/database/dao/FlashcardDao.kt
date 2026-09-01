package com.example.data.database.dao

import androidx.room.*
import com.example.data.database.entities.FlashcardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards ORDER BY createdAt DESC")
    fun getAllFlashcards(): Flow<List<FlashcardEntity>>

    @Query("SELECT DISTINCT deckName FROM flashcards")
    fun getAllDeckNames(): Flow<List<String>>

    @Query("SELECT * FROM flashcards WHERE deckName = :deckName")
    fun getFlashcardsByDeck(deckName: String): Flow<List<FlashcardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcard(flashcard: FlashcardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcards(flashcards: List<FlashcardEntity>)

    @Update
    suspend fun updateFlashcard(flashcard: FlashcardEntity)

    @Delete
    suspend fun deleteFlashcard(flashcard: FlashcardEntity)

    @Query("DELETE FROM flashcards WHERE deckName = :deckName")
    suspend fun deleteDeck(deckName: String)
}
