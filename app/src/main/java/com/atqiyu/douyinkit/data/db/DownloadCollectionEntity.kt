package com.atqiyu.douyinkit.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "download_collections")
data class DownloadCollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val sortOrder: Int,
)

@Entity(
    tableName = "download_collection_members",
    indices = [Index("collectionId")],
)
data class DownloadCollectionMemberEntity(
    @PrimaryKey val awemeId: String,
    val collectionId: String,
    val assignedAt: Long,
)
