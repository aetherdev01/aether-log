package com.aether.lv.data.db

import androidx.room.*
import com.aether.lv.data.model.RecentFile
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {
    @Query("SELECT * FROM recent_files ORDER BY lastOpenedAt DESC LIMIT 50")
    fun getAllFlow(): Flow<List<RecentFile>>

    @Query("SELECT * FROM recent_files ORDER BY lastOpenedAt DESC LIMIT 50")
    suspend fun getAll(): List<RecentFile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(file: RecentFile)

    @Query("DELETE FROM recent_files WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM recent_files")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM recent_files")
    suspend fun count(): Int
}
