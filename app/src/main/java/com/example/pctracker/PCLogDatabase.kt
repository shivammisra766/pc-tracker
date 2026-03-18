package com.example.pctracker

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PCLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PCLogDatabase : RoomDatabase() {

    abstract fun dao(): PCLogDao

    companion object {
        @Volatile
        private var INSTANCE: PCLogDatabase? = null

        fun getInstance(context: Context): PCLogDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PCLogDatabase::class.java,
                    "pc_logs.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
