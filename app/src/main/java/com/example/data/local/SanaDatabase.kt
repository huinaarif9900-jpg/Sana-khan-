package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SanaMemory::class, SanaMessage::class], version = 1, exportSchema = false)
abstract class SanaDatabase : RoomDatabase() {

    abstract fun sanaDao(): SanaDao

    companion object {
        @Volatile
        private var INSTANCE: SanaDatabase? = null

        fun getInstance(context: Context): SanaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SanaDatabase::class.java,
                    "sana_ai_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
