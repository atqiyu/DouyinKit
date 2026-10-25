package com.atqiyu.douyinkit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadHistoryDao {
    @Query("SELECT * FROM download_history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadHistoryEntity>>

    @Query("SELECT * FROM download_history ORDER BY createdAt DESC")
    suspend fun getAll(): List<DownloadHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadHistoryEntity)

    @Query("DELETE FROM download_history WHERE taskId = :taskId")
    suspend fun delete(taskId: String)

    @Query("DELETE FROM download_history WHERE status NOT IN ('QUEUED', 'RUNNING')")
    suspend fun clearFinished()

    @Query("DELETE FROM download_history")
    suspend fun clearAll()

    @Query(
        "UPDATE download_history SET status = 'FAILED', message = '上次下载被中断，可重试' " +
            "WHERE status IN ('QUEUED', 'RUNNING')",
    )
    suspend fun markInterrupted()
}
