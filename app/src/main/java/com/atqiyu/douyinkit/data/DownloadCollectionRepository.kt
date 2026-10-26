package com.atqiyu.douyinkit.data

import com.atqiyu.douyinkit.data.db.DownloadCollectionDao
import com.atqiyu.douyinkit.data.db.DownloadCollectionEntity
import com.atqiyu.douyinkit.data.db.DownloadCollectionMemberEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class DownloadCollectionRepository(private val dao: DownloadCollectionDao) {
    val collections: Flow<List<DownloadCollectionEntity>> = dao.observeCollections()
    val memberships: Flow<List<DownloadCollectionMemberEntity>> = dao.observeMembers()

    suspend fun create(name: String): DownloadCollectionEntity {
        val cleanName = name.trim().take(MAX_NAME_LENGTH)
        require(cleanName.isNotBlank()) { "合集名称不能为空" }
        val entity = DownloadCollectionEntity(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            createdAt = System.currentTimeMillis(),
            sortOrder = dao.maxSortOrder() + 1,
        )
        dao.upsertCollection(entity)
        return entity
    }

    suspend fun rename(id: String, name: String) {
        val cleanName = name.trim().take(MAX_NAME_LENGTH)
        require(cleanName.isNotBlank()) { "合集名称不能为空" }
        dao.renameCollection(id, cleanName)
    }

    suspend fun delete(id: String) {
        dao.clearMembers(id)
        dao.deleteCollection(id)
    }

    /** A work belongs to at most one collection; null moves it back to "未分类". */
    suspend fun move(awemeId: String, collectionId: String?) {
        dao.removeMember(awemeId)
        if (collectionId != null) {
            dao.upsertMember(
                DownloadCollectionMemberEntity(
                    awemeId = awemeId,
                    collectionId = collectionId,
                    assignedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 20
    }
}
