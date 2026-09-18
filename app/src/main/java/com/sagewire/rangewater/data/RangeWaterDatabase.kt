package com.sagewire.rangewater.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/*
 * 🪨 BLOCK 1 — DURABLE LOCAL DATABASE
 * Purpose: Owns RangeWater's device-local SQLite database.
 * 🪨 Protected: No destructive-migration fallback; saved ranch data must not be erased.
 */
@Database(
    entities = [WaterPointEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RangeWaterDatabase : RoomDatabase() {
    abstract fun waterPointDao(): WaterPointDao

    companion object {
        @Volatile
        private var instance: RangeWaterDatabase? = null

        fun getDatabase(context: Context): RangeWaterDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RangeWaterDatabase::class.java,
                    "rangewater_database"
                ).build().also { instance = it }
            }
    }
}
