package com.atqiyu.douyinkit.download

import android.content.ContentResolver
import android.content.Context
import android.media.MediaScannerConnection
import android.provider.MediaStore
import androidx.core.net.toUri
import com.atqiyu.douyinkit.data.DownloadHistoryRepository
import java.io.File

/** Deletes an app-created media file from MediaStore, falling back to its legacy path. */
fun deleteSavedFile(context: Context, file: SavedFile): Boolean {
    val uri = file.uri
    val isOwnFileProvider = uri.authority == "${context.packageName}.fileprovider"
    if (uri.scheme == ContentResolver.SCHEME_CONTENT && !isOwnFileProvider) {
        val deleted = runCatching {
            context.contentResolver.delete(uri, null, null)
        }.getOrDefault(0)
        if (deleted > 0) return true
        val exists = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID),
                null,
                null,
                null,
            )?.use { it.count > 0 } ?: false
        }.getOrDefault(false)
        if (!exists) return true
    }

    val path = file.path
    if (!path.isNullOrBlank()) {
        val target = File(path)
        if (!target.exists()) return true
        val deleted = runCatching { target.delete() }.getOrDefault(false)
        if (deleted) {
            runCatching {
                MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
            }
        }
        return deleted
    }
    return false
}

fun deleteSavedFileRef(context: Context, ref: SavedFileRef): Boolean {
    val file = ref.toSavedFile() ?: SavedFile(
        uri = ref.uri.toUri(),
        path = ref.path,
        displayName = ref.displayName,
        mimeType = ref.mimeType,
        bytes = ref.bytes,
    )
    return deleteSavedFile(context, file)
}

/**
 * Removes the given file URIs from download history. Tasks that end up with no files are
 * removed entirely. Both the in-memory registry and Room are kept in sync.
 */
suspend fun removeFileUrisFromDownloadRecords(
    repository: DownloadHistoryRepository,
    uris: Set<String>,
): Int {
    if (uris.isEmpty()) return 0
    val tasks = (DownloadRegistry.tasks.value + repository.allTasks()).distinctBy { it.id }
    var removedRecords = 0
    tasks.forEach { task ->
        val remainingFiles = task.files.filterNot { it.uri.toString() in uris }
        val remainingZip = task.zip?.takeUnless { it.uri.toString() in uris }
        if (remainingFiles.size == task.files.size && remainingZip == task.zip) {
            return@forEach
        }
        if (remainingFiles.isEmpty() && remainingZip == null) {
            DownloadRegistry.remove(task.id)
            repository.remove(task.id)
            removedRecords += 1
        } else {
            val updated = task.copy(files = remainingFiles, zip = remainingZip)
            DownloadRegistry.update(task.id) { updated }
            repository.save(updated)
        }
    }
    return removedRecords
}

suspend fun removeDownloadRecord(repository: DownloadHistoryRepository, taskId: String) {
    DownloadRegistry.remove(taskId)
    repository.remove(taskId)
}
