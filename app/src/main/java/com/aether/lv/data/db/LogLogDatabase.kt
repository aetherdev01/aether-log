package com.aether.lv.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aether.lv.data.model.RecentFile

@Database(
    entities  = [RecentFile::class],
    version   = 2,
    exportSchema = false
)
abstract class LogLogDatabase : RoomDatabase() {
    abstract fun recentFileDao(): RecentFileDao

    companion object {
        @Volatile private var INSTANCE: LogLogDatabase? = null

        // Migration v1 → v2: tambah kolom isPersisted (default 1 = true untuk entry lama)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE recent_files ADD COLUMN isPersisted INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        fun getInstance(context: Context): LogLogDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    LogLogDatabase::class.java,
                    "loglog.db"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
