package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.LouvorDao
import com.example.data.model.Culto
import com.example.data.model.Louvor

@Database(entities = [Culto::class, Louvor::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun louvorDao(): LouvorDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "louvorink_database"
                )
                .fallbackToDestructiveMigration() // Useful during development if schema changes
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
