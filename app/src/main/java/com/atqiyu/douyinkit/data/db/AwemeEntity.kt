package com.atqiyu.douyinkit.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "awemes",
    indices = [
        Index("parsedAt"),
        Index("isFavorite"),
    ],
)
data class AwemeEntity(
    @PrimaryKey val awemeId: String,
    val title: String,
    val authorName: String,
    val desc: String,
    val kind: String,
    val coverUrl: String?,
    val sourceUrl: String?,
    val snapshotJson: String,
    val isFavorite: Boolean = false,
    val tags: String = "",
    val parsedAt: Long,
    val createTimeSeconds: Long?,
)
