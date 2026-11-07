package com.atqiyu.douyinkit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShareLinkDao {
    @Query("SELECT * FROM share_link_records ORDER BY lastCapturedAt DESC")
    fun observeAll(): Flow<List<ShareLinkRecordEntity>>

    @Query(
        "SELECT COUNT(*) FROM share_link_records " +
            "WHERE status IN ('RECORDED', 'FAILED')",
    )
    fun observePendingCount(): Flow<Int>

    @Query(
        "SELECT * FROM share_link_records " +
            "WHERE status IN ('RECORDED', 'FAILED') ORDER BY lastCapturedAt ASC",
    )
    suspend fun getPending(): List<ShareLinkRecordEntity>

    @Query("SELECT * FROM share_link_records WHERE url = :url LIMIT 1")
    suspend fun get(url: String): ShareLinkRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ShareLinkRecordEntity)

    @Query(
        "UPDATE share_link_records SET status = 'PROCESSING', message = '正在处理', " +
            "updatedAt = :now WHERE url = :url AND status != 'COMPLETED'",
    )
    suspend fun markProcessing(url: String, now: Long)

    @Query(
        "UPDATE share_link_records SET status = 'COMPLETED', " +
            "awemeId = COALESCE(:awemeId, awemeId), title = COALESCE(:title, title), " +
            "message = :message, completedAt = :now, updatedAt = :now WHERE url = :url",
    )
    suspend fun markCompleted(
        url: String,
        awemeId: String?,
        title: String?,
        message: String?,
        now: Long,
    )

    @Query(
        "UPDATE share_link_records SET status = 'FAILED', message = :message, " +
            "updatedAt = :now WHERE url = :url",
    )
    suspend fun markFailed(url: String, message: String?, now: Long)

    @Query(
        "UPDATE share_link_records SET status = 'FAILED', " +
            "message = '上次处理被中断，可重新批量处理', updatedAt = :now " +
            "WHERE status = 'PROCESSING'",
    )
    suspend fun markInterrupted(now: Long)

    @Query("DELETE FROM share_link_records WHERE url = :url")
    suspend fun delete(url: String)

    @Query("DELETE FROM share_link_records WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()

    @Query("DELETE FROM share_link_records")
    suspend fun clearAll()
}