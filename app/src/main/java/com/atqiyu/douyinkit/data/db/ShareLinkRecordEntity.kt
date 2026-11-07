package com.atqiyu.douyinkit.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One Douyin share link captured by the floating assistant's 连播 mode.
 * The normalized URL is the primary key so repeated captures update the same row.
 */
@Entity(
    tableName = "share_link_records",
    indices = [
        Index("status"),
        Index("lastCapturedAt"),
    ],
)
data class ShareLinkRecordEntity(
    @PrimaryKey val url: String,
    val awemeId: String? = null,
    val title: String? = null,
    val status: String = "RECORDED",
    val message: String? = null,
    val firstCapturedAt: Long,
    val lastCapturedAt: Long,
    val captureCount: Int = 1,
    val updatedAt: Long,
    val completedAt: Long? = null,
)