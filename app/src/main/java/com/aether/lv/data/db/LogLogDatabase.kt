package com.aether.lv.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aether.lv.data.model.RecentFile

@Database(
    entities  = [RecentFile::class],
    version   = 1,
    exportSchema = false
)
abstract class LogLogDatabase : RoomDatabase() {
    abstract fun recentFileDao(): RecentFileDao

    companion object {
        @Volatile private var INSTANCE: LogLogDatabase? = null

        fun getInstance(context: Context): LogLogDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    LogLogDatabase::class.java,
                    "loglog.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
