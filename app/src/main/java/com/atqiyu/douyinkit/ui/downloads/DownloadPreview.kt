package com.atqiyu.douyinkit.ui.downloads

import com.atqiyu.douyinkit.core.model.ImageInfo
import com.atqiyu.douyinkit.core.model.LivePhoto
import com.atqiyu.douyinkit.download.DownloadKind
import com.atqiyu.douyinkit.download.DownloadTask

sealed interface DownloadPreview {
    data class Video(val url: String, val title: String) : DownloadPreview

    data class Images(val images: List<ImageInfo>) : DownloadPreview

    data class Audio(val uri: String, val title: String) : DownloadPreview
}

fun buildDownloadPreview(task: DownloadTask): DownloadPreview? {
    val files = task.files
    if (files.isEmpty()) return null

    val images = files
        .filter { it.mimeType.startsWith("image/") }
        .sortedBy { it.displayName }
    val liveByIndex = files
        .filter { it.mimeType.startsWith("video/") && it.displayName.contains("_live") }
        .associateBy { leadingIndex(it.displayName) }

    return when (task.request.kind) {
        DownloadKind.VIDEO -> {
            files
                .firstOrNull { it.mimeType.startsWith("video/") && !it.displayName.contains("_live") }
                ?.let { DownloadPreview.Video(it.uri.toString(), task.request.aweme.displayTitle) }
        }

        DownloadKind.IMAGES, DownloadKind.COVER -> {
            if (images.isEmpty()) {
                null
            } else {
                DownloadPreview.Images(
                    images.mapIndexed { index, file ->
                        val live = liveByIndex[leadingIndex(file.displayName)]
                        ImageInfo(
                            index = index,
                            urls = listOf(file.uri.toString()),
                            isLivePhoto = live != null,
                            livePhoto = live?.let {
                                LivePhoto(
                                    urls = listOf(it.uri.toString()),
                                    sizeBytes = it.bytes,
                                )
                            },
                        )
                    },
                )
            }
        }

        DownloadKind.MUSIC -> {
            files.firstOrNull { it.mimeType.startsWith("audio/") }
                ?.let { DownloadPreview.Audio(it.uri.toString(), it.displayName) }
        }

        DownloadKind.ZIP -> null
    }
}

private val leadingIndexRegex = Regex("""^(\d+)""")

private fun leadingIndex(name: String): String? {
    return leadingIndexRegex.find(name)?.groupValues?.getOrNull(1)
}
