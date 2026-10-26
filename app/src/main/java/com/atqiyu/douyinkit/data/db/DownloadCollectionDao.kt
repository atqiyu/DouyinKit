package com.atqiyu.douyinkit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadCollectionDao {
    @Query("SELECT * FROM download_collections ORDER BY sortOrder ASC, createdAt ASC")
    fun observeCollections(): Flow<List<DownloadCollectionEntity>>

    @Query("SELECT * FROM download_collection_members")
    fun observeMembers(): Flow<List<DownloadCollectionMemberEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM download_collections")
    suspend fun maxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollection(collection: DownloadCollectionEntity)

    @Query("UPDATE download_collections SET name = :name WHERE id = :id")
    suspend fun renameCollection(id: String, name: String)

    @Query("DELETE FROM download_collections WHERE id = :id")
    suspend fun deleteCollection(id: String)

    @Query("DELETE FROM download_collection_members WHERE collectionId = :collectionId")
    suspend fun clearMembers(collectionId: String)

    @Query("DELETE FROM download_collection_members WHERE awemeId = :awemeId")
    suspend fun removeMember(awemeId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: DownloadCollectionMemberEntity)
}
