package com.example.data.database.dao

import androidx.room.*
import com.example.data.database.entities.PomodoroLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PomodoroLogDao {
    @Query("SELECT * FROM pomodoro_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<PomodoroLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: PomodoroLogEntity): Long

    @Query("SELECT SUM(minutesSpent) FROM pomodoro_logs")
    fun getTotalFocusMinutes(): Flow<Int?>
}
