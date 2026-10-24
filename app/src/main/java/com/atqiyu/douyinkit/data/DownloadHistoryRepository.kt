package com.atqiyu.douyinkit.data

import android.net.Uri
import com.atqiyu.douyinkit.data.db.DownloadHistoryDao
import com.atqiyu.douyinkit.data.db.DownloadHistoryEntity
import com.atqiyu.douyinkit.download.DownloadProgress
import com.atqiyu.douyinkit.download.DownloadRequest
import com.atqiyu.douyinkit.download.DownloadStatus
import com.atqiyu.douyinkit.download.DownloadTask
import com.atqiyu.douyinkit.download.SavedFile
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class DownloadHistoryRepository(
    private val dao: DownloadHistoryDao,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    fun observeAll(): Flow<List<DownloadHistoryEntity>> = dao.observeAll()

    suspend fun save(task: DownloadTask) {
        val files = task.files.map { it.toRecord() }
        dao.upsert(
            DownloadHistoryEntity(
                taskId = task.id,
                awemeId = task.request.aweme.awemeId,
                title = task.request.aweme.displayTitle,
                kind = task.request.kind.name,
                status = task.status.name,
                message = task.message,
                requestJson = json.encodeToString(
                    DownloadRequest.serializer(),
                    task.request,
                ),
                filesJson = json.encodeToString(
                    ListSerializer(SavedFileRecord.serializer()),
                    files,
                ),
                zipJson = task.zip?.let {
                    json.encodeToString(SavedFileRecord.serializer(), it.toRecord())
                },
                createdAt = task.createdAt,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun toTask(entity: DownloadHistoryEntity): DownloadTask? {
        return runCatching {
            val request = json.decodeFromString(
                DownloadRequest.serializer(),
                entity.requestJson,
            )
            val files = json.decodeFromString(
                ListSerializer(SavedFileRecord.serializer()),
                entity.filesJson,
            ).mapNotNull { it.toSavedFile() }
            val zip = entity.zipJson?.let {
                json.decodeFromString(SavedFileRecord.serializer(), it).toSavedFile()
            }
            val status = runCatching {
                DownloadStatus.valueOf(entity.status)
            }.getOrDefault(DownloadStatus.FAILED)
            val bytes = files.sumOf { it.bytes } + (zip?.bytes ?: 0L)
            DownloadTask(
                id = entity.taskId,
                request = request,
                status = status,
                progress = DownloadProgress(
                    bytes = bytes,
                    total = bytes,
                    itemsDone = files.size,
                    itemsTotal = files.size + if (zip != null) 1 else 0,
                ),
                message = entity.message.orEmpty(),
                files = files,
                zip = zip,
                error = if (status == DownloadStatus.FAILED) entity.message else null,
                createdAt = entity.createdAt,
            )
        }.getOrNull()
    }

    suspend fun markInterrupted() {
        dao.markInterrupted()
    }

    suspend fun remove(taskId: String) {
        dao.delete(taskId)
    }

    suspend fun clearFinished() {
        dao.clearFinished()
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    private fun SavedFile.toRecord(): SavedFileRecord {
        return SavedFileRecord(
            uri = uri.toString(),
            path = path,
            displayName = displayName,
            mimeType = mimeType,
            bytes = bytes,
        )
    }

    private fun SavedFileRecord.toSavedFile(): SavedFile? {
        return runCatching {
            SavedFile(
                uri = Uri.parse(uri),
                path = path,
                displayName = displayName,
                mimeType = mimeType,
                bytes = bytes,
            )
        }.getOrNull()
    }

    @Serializable
    private data class SavedFileRecord(
        val uri: String,
        val path: String?,
        val displayName: String,
        val mimeType: String,
        val bytes: Long,
    )
}
