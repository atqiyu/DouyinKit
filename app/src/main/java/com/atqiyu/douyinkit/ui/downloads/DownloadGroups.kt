package com.atqiyu.douyinkit.ui.downloads

import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.core.model.ImageInfo
import com.atqiyu.douyinkit.core.model.LivePhoto
import com.atqiyu.douyinkit.core.model.MusicInfo
import com.atqiyu.douyinkit.core.model.VideoCodec
import com.atqiyu.douyinkit.core.model.VideoGear
import com.atqiyu.douyinkit.core.util.formatBytes
import com.atqiyu.douyinkit.core.util.formatDuration
import com.atqiyu.douyinkit.download.DownloadKind
import com.atqiyu.douyinkit.download.DownloadStatus
import com.atqiyu.douyinkit.download.DownloadTask
import com.atqiyu.douyinkit.download.SavedFile

enum class GroupMediaKind { VIDEO, IMAGE, LIVE, AUDIO, ZIP }

data class GroupMediaItem(
    val task: DownloadTask,
    val file: SavedFile,
    val kind: GroupMediaKind,
    val title: String,
    val gear: VideoGear? = null,
    val imageInfo: ImageInfo? = null,
    val livePhoto: LivePhoto? = null,
    val music: MusicInfo? = null,
    val durationMs: Long = 0L,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Int? = null,
    val codec: VideoCodec? = null,
    val bitrateBps: Long? = null,
    val format: String = "",
) {
    val isPlayableVideo: Boolean
        get() = kind == GroupMediaKind.VIDEO || kind == GroupMediaKind.LIVE

    val formatLabel: String
        get() = format.ifBlank {
            file.displayName.substringAfterLast('.', "")
        }.uppercase()

    val sizeLabel: String get() = formatBytes(file.bytes)

    val dimensionLabel: String?
        get() {
            val w = width ?: return null
            val h = height ?: return null
            if (w <= 0 || h <= 0) return null
            return "$w×$h"
        }

    val detailSummary: String
        get() = when (kind) {
            GroupMediaKind.VIDEO, GroupMediaKind.LIVE -> buildList {
                dimensionLabel?.let(::add)
                fps?.takeIf { it > 0 }?.let { add("${it}fps") }
                codec?.let { add(codecLabel(it)) }
                if (durationMs > 0) add(formatDuration(durationMs))
                add(sizeLabel)
                bitrateBps?.takeIf { it > 0 }?.let { add("${it / 1000} kbps") }
                if (formatLabel.isNotBlank()) add(formatLabel)
            }.joinToString(" · ")

            GroupMediaKind.IMAGE -> buildList {
                dimensionLabel?.let(::add)
                if (formatLabel.isNotBlank()) add(formatLabel)
                add(sizeLabel)
                if (imageInfo?.isLivePhoto == true) add("含动图")
            }.joinToString(" · ")

            GroupMediaKind.AUDIO -> buildList {
                if (durationMs > 0) add(formatDuration(durationMs))
                if (formatLabel.isNotBlank()) add(formatLabel)
                add(sizeLabel)
            }.joinToString(" · ")

            GroupMediaKind.ZIP -> listOfNotNull(
                formatLabel.takeIf { it.isNotBlank() },
                sizeLabel,
            ).joinToString(" · ")
        }

    private fun codecLabel(codec: VideoCodec): String = when (codec) {
        VideoCodec.H264 -> "H.264"
        VideoCodec.H265 -> "H.265"
        VideoCodec.UNKNOWN -> "未知编码"
    }
}

data class GroupImagePage(
    val image: GroupMediaItem,
    val live: GroupMediaItem?,
)

class DownloadGroup(
    val aweme: Aweme,
    val tasks: List<DownloadTask>,
) {
    val awemeId: String get() = aweme.awemeId
    val title: String get() = aweme.displayTitle
    val latestAt: Long = tasks.maxOfOrNull { it.createdAt } ?: 0L

    val activeTasks: List<DownloadTask> = tasks.filter { it.isActiveDownload() }
    val failedTasks: List<DownloadTask> = tasks.filter {
        it.status == DownloadStatus.FAILED || it.status == DownloadStatus.NEEDS_REFRESH
    }
    val completedTasks: List<DownloadTask> = tasks.filter {
        it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.PARTIAL
    }

    val allFiles: List<SavedFile> by lazy {
        tasks.flatMap { task -> task.files + listOfNotNull(task.zip) }
            .distinctBy { it.uri.toString() }
    }

    val mediaItems: List<GroupMediaItem> by lazy {
        buildMediaItems(aweme, tasks).sortedWith(
            compareBy<GroupMediaItem> { it.kind.ordinal }
                .thenByDescending { it.task.createdAt }
                .thenBy { it.file.displayName },
        )
    }

    val videoItems: List<GroupMediaItem> by lazy {
        mediaItems.filter { it.kind == GroupMediaKind.VIDEO }
    }
    val imageItems: List<GroupMediaItem> by lazy {
        mediaItems.filter { it.kind == GroupMediaKind.IMAGE }
    }
    val liveItems: List<GroupMediaItem> by lazy {
        mediaItems.filter { it.kind == GroupMediaKind.LIVE }
    }
    val audioItems: List<GroupMediaItem> by lazy {
        mediaItems.filter { it.kind == GroupMediaKind.AUDIO }
    }
    val zipItems: List<GroupMediaItem> by lazy {
        mediaItems.filter { it.kind == GroupMediaKind.ZIP }
    }

    val imagePages: List<GroupImagePage> by lazy {
        imageItems.map { image ->
            val imageIndex = leadingMediaIndex(image.file.displayName)
            val live = liveItems.firstOrNull { candidate ->
                candidate.task.id == image.task.id &&
                    leadingMediaIndex(candidate.file.displayName) == imageIndex
            }
            GroupImagePage(image = image, live = live)
        }
    }

    val coverModel: Any? by lazy {
        imageItems.firstOrNull()?.file?.uri
            ?: videoItems.firstOrNull()?.file?.uri
            ?: aweme.primaryCoverUrl
    }

    val coverIsVideo: Boolean by lazy {
        imageItems.isEmpty() && videoItems.isNotEmpty()
    }

    val featuredMedia: GroupMediaItem? by lazy {
        when (aweme.kind) {
            AwemeKind.VIDEO -> videoItems.firstOrNull()
                ?: imageItems.firstOrNull()
                ?: audioItems.firstOrNull()
                ?: mediaItems.firstOrNull()

            AwemeKind.IMAGES -> imageItems.firstOrNull()
                ?: videoItems.firstOrNull()
                ?: audioItems.firstOrNull()
                ?: mediaItems.firstOrNull()
        }
    }

    val hasFiles: Boolean get() = allFiles.isNotEmpty()
    val isActive: Boolean get() = activeTasks.isNotEmpty()
    val fileCount: Int get() = allFiles.size
    val totalBytes: Long = allFiles.sumOf { it.bytes }

    val progressFraction: Float?
        get() {
            val knownTotals = activeTasks.mapNotNull { task ->
                val total = task.progress.total
                if (total != null && total > 0) task.progress.bytes to total else null
            }
            if (knownTotals.isEmpty()) return null
            val (bytes, total) = knownTotals.fold(0L to 0L) { acc, pair ->
                (acc.first + pair.first) to (acc.second + pair.second)
            }
            if (total <= 0) return null
            return (bytes.toFloat() / total).coerceIn(0f, 1f)
        }

    val speedBps: Long
        get() = activeTasks.sumOf { it.progress.speedBps }

    val summaryLine: String
        get() = buildList {
            add("${fileCount} 个文件")
            if (totalBytes > 0) add(formatBytes(totalBytes))
            if (failedTasks.isNotEmpty()) add("${failedTasks.size} 个任务失败")
        }.joinToString(" · ")

    val statusLine: String
        get() = when {
            activeTasks.isNotEmpty() -> {
                val fraction = progressFraction
                if (fraction != null) {
                    "下载中 ${(fraction * 100).toInt()}%"
                } else {
                    "正在下载 ${activeTasks.size} 个任务"
                }
            }

            failedTasks.isNotEmpty() && completedTasks.isEmpty() -> "下载失败"
            completedTasks.isNotEmpty() && failedTasks.isNotEmpty() -> "部分完成"
            completedTasks.isNotEmpty() -> "已保存到相册"
            tasks.any { it.status == DownloadStatus.CANCELLED } -> "已取消"
            else -> "等待下载"
        }
}

fun DownloadTask.isActiveDownload(): Boolean {
    return status == DownloadStatus.RUNNING || status == DownloadStatus.QUEUED
}

fun DownloadTask.typeLabel(): String = when (request.kind) {
    DownloadKind.VIDEO -> request.gear?.let { "${it.resolutionLabel} 视频" } ?: "视频"
    DownloadKind.IMAGES -> if (request.aweme.kind == AwemeKind.IMAGES) "图集" else "图片"
    DownloadKind.MUSIC -> "BGM"
    DownloadKind.COVER -> "封面"
    DownloadKind.ZIP -> "ZIP"
}

fun buildDownloadGroups(tasks: List<DownloadTask>): List<DownloadGroup> {
    return tasks
        .groupBy { it.request.aweme.awemeId }
        .map { (_, groupTasks) ->
            val ordered = groupTasks.sortedByDescending { it.createdAt }
            val aweme = ordered.firstOrNull { it.files.isNotEmpty() }?.request?.aweme
                ?: ordered.first().request.aweme
            DownloadGroup(aweme = aweme, tasks = ordered)
        }
        .sortedByDescending { it.latestAt }
}

private fun buildMediaItems(aweme: Aweme, tasks: List<DownloadTask>): List<GroupMediaItem> {
    val result = mutableListOf<GroupMediaItem>()
    tasks.forEach { task ->
        val request = task.request
        when (request.kind) {
            DownloadKind.VIDEO -> {
                task.files
                    .filter { it.mimeType.startsWith("video/") && !it.displayName.contains("_live") }
                    .forEach { file ->
                        val gear = request.gear
                        result += GroupMediaItem(
                            task = task,
                            file = file,
                            kind = GroupMediaKind.VIDEO,
                            title = gear?.let { "${it.resolutionLabel} · ${it.codecLabel}" } ?: "视频",
                            gear = gear,
                            durationMs = request.aweme.video?.durationMs ?: 0L,
                            width = gear?.width,
                            height = gear?.height,
                            fps = gear?.fps,
                            codec = gear?.codec,
                            bitrateBps = gear?.bitrateBps,
                            format = gear?.format.orEmpty(),
                        )
                    }
            }

            DownloadKind.MUSIC -> {
                task.files
                    .filter { it.mimeType.startsWith("audio/") }
                    .forEach { file ->
                        val music = request.aweme.music
                        result += GroupMediaItem(
                            task = task,
                            file = file,
                            kind = GroupMediaKind.AUDIO,
                            title = music?.displayTitle?.takeIf { it.isNotBlank() } ?: "BGM",
                            music = music,
                            durationMs = music?.durationMs ?: 0L,
                            format = file.displayName.substringAfterLast('.', "").uppercase(),
                        )
                    }
            }

            DownloadKind.COVER, DownloadKind.IMAGES, DownloadKind.ZIP -> {
                task.files.forEach { file ->
                    when {
                        file.mimeType.startsWith("image/") -> {
                            val imageIndex = leadingMediaIndex(file.displayName)
                            val info = imageInfoAt(aweme, imageIndex)
                            result += GroupMediaItem(
                                task = task,
                                file = file,
                                kind = GroupMediaKind.IMAGE,
                                title = if (request.kind == DownloadKind.COVER) {
                                    "封面"
                                } else if (imageIndex != null) {
                                    "第 ${imageIndex + 1} 张"
                                } else {
                                    file.displayName
                                },
                                imageInfo = info,
                                width = info?.width,
                                height = info?.height,
                                format = info?.format ?: file.displayName.substringAfterLast('.', ""),
                            )
                        }

                        file.mimeType.startsWith("video/") &&
                            file.displayName.contains("_live") -> {
                            val imageIndex = leadingMediaIndex(file.displayName)
                            val info = imageInfoAt(aweme, imageIndex)
                            val live = info?.livePhoto
                            result += GroupMediaItem(
                                task = task,
                                file = file,
                                kind = GroupMediaKind.LIVE,
                                title = if (imageIndex != null) {
                                    "第 ${imageIndex + 1} 张动图"
                                } else {
                                    "LivePhoto"
                                },
                                imageInfo = info,
                                livePhoto = live,
                                durationMs = live?.durationMs ?: 0L,
                                width = live?.width,
                                height = live?.height,
                                fps = live?.fps,
                                codec = live?.codec,
                                format = "MP4",
                            )
                        }

                        file.mimeType.startsWith("audio/") -> {
                            val music = request.aweme.music
                            result += GroupMediaItem(
                                task = task,
                                file = file,
                                kind = GroupMediaKind.AUDIO,
                                title = music?.displayTitle ?: "作品 BGM",
                                music = music,
                                durationMs = music?.durationMs ?: 0L,
                                format = file.displayName.substringAfterLast('.', "").uppercase(),
                            )
                        }
                    }
                }
            }
        }

        task.zip?.let { zip ->
            result += GroupMediaItem(
                task = task,
                file = zip,
                kind = GroupMediaKind.ZIP,
                title = "ZIP 打包",
                format = "ZIP",
            )
        }
    }
    return result.distinctBy { it.file.uri.toString() }
}

private fun imageInfoAt(aweme: Aweme, index: Int?): ImageInfo? {
    if (index == null) return null
    return aweme.images.firstOrNull { it.index == index } ?: aweme.images.getOrNull(index)
}

private fun leadingMediaIndex(name: String): Int? {
    val value = leadingMediaIndexRegex.find(name)?.groupValues?.getOrNull(1) ?: return null
    return value.toIntOrNull()?.minus(1)
}

private val leadingMediaIndexRegex = Regex("""^(\d+)""")
