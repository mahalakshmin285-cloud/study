package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.database.dao.*
import com.example.data.database.entities.*

@Database(
    entities = [
        NoteEntity::class,
        FlashcardEntity::class,
        QuizResultEntity::class,
        PdfSummaryEntity::class,
        ChatMessageEntity::class,
        StudyTaskEntity::class,
        PomodoroLogEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun quizResultDao(): QuizResultDao
    abstract fun pdfSummaryDao(): PdfSummaryDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun studyTaskDao(): StudyTaskDao
    abstract fun pomodoroLogDao(): PomodoroLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "study_assistant_db"
                )
                    .fallbackToDestructiveMigration()
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
