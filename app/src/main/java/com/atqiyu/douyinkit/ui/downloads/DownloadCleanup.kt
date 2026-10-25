package com.atqiyu.douyinkit.ui.downloads

import android.content.Context
import com.atqiyu.douyinkit.core.util.formatBytes
import com.atqiyu.douyinkit.data.DownloadHistoryRepository
import com.atqiyu.douyinkit.download.DownloadStatus
import com.atqiyu.douyinkit.download.DownloadTask
import com.atqiyu.douyinkit.download.SavedFile
import com.atqiyu.douyinkit.download.deleteSavedFile
import com.atqiyu.douyinkit.download.removeDownloadRecord
import com.atqiyu.douyinkit.download.removeFileUrisFromDownloadRecords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DownloadCleanupUnit(
    val key: String,
    val group: DownloadGroup,
    val task: DownloadTask,
    val file: SavedFile?,
    val kind: GroupMediaKind?,
    val label: String,
    val detail: String,
)

data class DownloadCleanupResult(
    val removedRecords: Int,
    val deletedFiles: Int,
    val failedFiles: List<String>,
)

fun DownloadGroup.cleanupUnits(): List<DownloadCleanupUnit> {
    val units = mutableListOf<DownloadCleanupUnit>()
    val knownUris = mutableSetOf<String>()

    mediaItems.forEach { item ->
        val uri = item.file.uri.toString()
        if (!knownUris.add(uri)) return@forEach
        units += DownloadCleanupUnit(
            key = "file:$uri",
            group = this,
            task = item.task,
            file = item.file,
            kind = item.kind,
            label = item.title,
            detail = "${item.file.displayName} · ${item.detailSummary}",
        )
    }

    allFiles.forEach { file ->
        val uri = file.uri.toString()
        if (!knownUris.add(uri)) return@forEach
        val owner = tasks.firstOrNull { task ->
            task.files.any { it.uri.toString() == uri } ||
                task.zip?.uri?.toString() == uri
        } ?: return@forEach
        units += DownloadCleanupUnit(
            key = "file:$uri",
            group = this,
            task = owner,
            file = file,
            kind = null,
            label = file.displayName,
            detail = formatBytes(file.bytes),
        )
    }

    tasks
        .filter { it.files.isEmpty() && it.zip == null && !it.isActiveDownload() }
        .forEach { task ->
            units += DownloadCleanupUnit(
                key = "task:${task.id}",
                group = this,
                task = task,
                file = null,
                kind = null,
                label = task.typeLabel(),
                detail = "${task.status.statusLabel()} · ${
                    task.message.ifBlank { "没有本地文件" }
                }",
            )
        }

    return units
}

suspend fun performDownloadCleanup(
    context: Context,
    repository: DownloadHistoryRepository,
    units: List<DownloadCleanupUnit>,
    deleteLocalFiles: Boolean,
): DownloadCleanupResult = withContext(Dispatchers.IO) {
    val fileUnits = units.mapNotNull { it.file }.distinctBy { it.uri.toString() }
    val selectedFileUris = units.filter { it.file != null }
        .mapNotNull { it.file?.uri?.toString() }
        .toSet()

    val deletedUris = mutableSetOf<String>()
    val failures = mutableListOf<String>()
    var deletedFiles = 0

    if (deleteLocalFiles) {
        fileUnits.forEach { file ->
            if (deleteSavedFile(context, file)) {
                deletedUris += file.uri.toString()
                deletedFiles += 1
            } else {
                failures += file.displayName
            }
        }
    } else {
        deletedUris += selectedFileUris
    }

    var removedRecords = removeFileUrisFromDownloadRecords(repository, deletedUris)
    units.asSequence()
        .filter { it.file == null }
        .map { it.task.id }
        .distinct()
        .forEach { taskId ->
            removeDownloadRecord(repository, taskId)
            removedRecords += 1
        }

    DownloadCleanupResult(
        removedRecords = removedRecords,
        deletedFiles = deletedFiles,
        failedFiles = failures,
    )
}

private fun DownloadStatus.statusLabel(): String = when (this) {
    DownloadStatus.QUEUED -> "排队中"
    DownloadStatus.RUNNING -> "下载中"
    DownloadStatus.COMPLETED -> "已完成"
    DownloadStatus.PARTIAL -> "部分完成"
    DownloadStatus.FAILED -> "失败"
    DownloadStatus.NEEDS_REFRESH -> "需要刷新"
    DownloadStatus.CANCELLED -> "已取消"
}
