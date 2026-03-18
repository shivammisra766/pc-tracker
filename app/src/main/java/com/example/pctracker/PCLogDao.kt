package com.example.pctracker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PCLogDao {

    @Insert
    suspend fun insert(log: PCLogEntity)

    @Query("SELECT * FROM pc_logs ORDER BY timestamp DESC LIMIT 200")
    suspend fun getAll(): List<PCLogEntity>

    @Query("DELETE FROM pc_logs")
    suspend fun clearAll()
}
