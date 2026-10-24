package com.atqiyu.douyinkit.download

import android.net.Uri
import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.VideoGear
import java.io.File
import kotlinx.serialization.Serializable

@Serializable
enum class DownloadKind { VIDEO, IMAGES, MUSIC, COVER, ZIP }

enum class DownloadStatus { QUEUED, RUNNING, COMPLETED, PARTIAL, FAILED, NEEDS_REFRESH, CANCELLED }

enum class DownloadFailure {
    NETWORK,
    HTTP,
    EXPIRED,
    NOT_MEDIA,
    TOO_LARGE,
    EMPTY,
    SIZE_MISMATCH,
    NO_SOURCE,
    CANCELLED,
    STORAGE,
    PERMISSION,
    UNKNOWN,
}

class DownloadException(
    val failure: DownloadFailure,
    override val message: String,
    val attempts: List<String> = emptyList(),
    cause: Throwable? = null,
) : Exception(message, cause)

enum class MediaKind { VIDEO, IMAGE, AUDIO }

data class DownloadedMedia(
    val file: File,
    val extension: String,
    val mimeType: String,
    val bytes: Long,
)

data class SavedFile(
    val uri: Uri,
    val path: String?,
    val displayName: String,
    val mimeType: String,
    val bytes: Long,
)

data class DownloadProgress(
    val bytes: Long = 0,
    val total: Long? = null,
    val itemsDone: Int = 0,
    val itemsTotal: Int = 0,
    val speedBps: Long = 0,
)

@Serializable
data class DownloadRequest(
    val aweme: Aweme,
    val kind: DownloadKind,
    val gear: VideoGear? = null,
    val includeLivePhotos: Boolean = true,
    val includeMusic: Boolean = true,
    val createZip: Boolean = false,
    val allowWatermarkedFallback: Boolean = false,
)

data class DownloadTask(
    val id: String,
    val request: DownloadRequest,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: DownloadProgress = DownloadProgress(),
    val message: String = "",
    val files: List<SavedFile> = emptyList(),
    val zip: SavedFile? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
