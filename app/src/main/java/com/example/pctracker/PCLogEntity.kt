package com.example.pctracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pc_logs")
data class PCLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val app: String,
    val title: String,
    val timestamp: Long
)
