package com.atqiyu.douyinkit.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "download_history",
    indices = [Index("createdAt")],
)
data class DownloadHistoryEntity(
    @PrimaryKey val taskId: String,
    val awemeId: String,
    val title: String,
    val kind: String,
    val status: String,
    val message: String?,
    val requestJson: String,
    val filesJson: String,
    val zipJson: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
