package com.atqiyu.douyinkit.download

import com.atqiyu.douyinkit.core.model.VideoGear

object DownloadMediaKeys {
    const val MUSIC = "music"
    const val ZIP = "zip"
    const val COVER = "cover"

    fun image(index: Int): String = "image:$index"
    fun live(index: Int): String = "live:$index"

    fun video(gear: VideoGear?): String {
        if (gear == null) return "video:unknown"
        val dimensions = if ((gear.width ?: 0) > 0 && (gear.height ?: 0) > 0) {
            "${gear.width}x${gear.height}"
        } else {
            gear.name
        }
        return "video:$dimensions:${gear.codec}:${gear.fps ?: 0}"
    }
}

data class ExistingDuplicateContent(
    val key: String,
    val label: String,
    val files: List<SavedFile>,
)

data class DuplicateMatch(
    val request: DownloadRequest,
    val contents: List<ExistingDuplicateContent>,
) {
    val keys: Set<String> get() = contents.map { it.key }.toSet()
}

data class DuplicateCheck(
    val matches: List<DuplicateMatch>,
) {
    val hasDuplicates: Boolean get() = matches.isNotEmpty()
    val labels: List<String>
        get() = matches.flatMap { it.contents }.map { it.label }.distinct()

    val allExistingFiles: List<SavedFile>
        get() = matches.flatMap { it.contents }.flatMap { it.files }
            .distinctBy { it.uri.toString() }
}

fun DownloadRequest.requestedMediaKeys(): Set<String> = when (kind) {
    DownloadKind.VIDEO -> setOf(DownloadMediaKeys.video(gear))
    DownloadKind.MUSIC -> setOf(DownloadMediaKeys.MUSIC)
    DownloadKind.COVER -> setOf(DownloadMediaKeys.COVER)
    DownloadKind.IMAGES, DownloadKind.ZIP -> buildSet {
        aweme.images.forEachIndexed { index, image ->
            add(DownloadMediaKeys.image(index))
            if (includeLivePhotos && image.isLivePhoto && image.livePhoto != null) {
                add(DownloadMediaKeys.live(index))
            }
        }
        if (includeMusic && aweme.music != null) add(DownloadMediaKeys.MUSIC)
        if (createZip || kind == DownloadKind.ZIP) add(DownloadMediaKeys.ZIP)
    }
}

fun DownloadRequest.hasContentAfterSkip(skipKeys: Set<String>): Boolean = when (kind) {
    DownloadKind.VIDEO -> DownloadMediaKeys.video(gear) !in skipKeys
    DownloadKind.MUSIC -> DownloadMediaKeys.MUSIC !in skipKeys
    DownloadKind.COVER -> DownloadMediaKeys.COVER !in skipKeys
    DownloadKind.IMAGES, DownloadKind.ZIP -> {
        val imageRemaining = aweme.images.indices.any { index ->
            DownloadMediaKeys.image(index) !in skipKeys ||
                (
                    includeLivePhotos &&
                        aweme.images[index].isLivePhoto &&
                        aweme.images[index].livePhoto != null &&
                        DownloadMediaKeys.live(index) !in skipKeys
                    )
        }
        val musicRemaining = includeMusic &&
            aweme.music != null &&
            DownloadMediaKeys.MUSIC !in skipKeys
        imageRemaining || musicRemaining
    }
}

fun collectExistingDownloadContent(
    awemeId: String,
    tasks: List<DownloadTask>,
): Map<String, ExistingDuplicateContent> {
    val result = linkedMapOf<String, ExistingDuplicateContent>()

    fun add(key: String, label: String, files: List<SavedFile>) {
        if (files.isEmpty()) return
        val previous = result[key]
        result[key] = ExistingDuplicateContent(
            key = key,
            label = previous?.label ?: label,
            files = (previous?.files.orEmpty() + files).distinctBy { it.uri.toString() },
        )
    }

    tasks.filter { it.request.aweme.awemeId == awemeId }.forEach { task ->
        val request = task.request
        if (request.kind == DownloadKind.VIDEO) {
            val files = task.files.filter {
                it.mimeType.startsWith("video/") && !it.displayName.contains("_live")
            }
            if (files.isNotEmpty()) {
                add(
                    key = DownloadMediaKeys.video(request.gear),
                    label = videoLabel(request.gear),
                    files = files,
                )
            }
        }

        task.files.forEach { file ->
            when {
                file.mimeType.startsWith("image/") && request.kind != DownloadKind.COVER -> {
                    val index = leadingIndex(file.displayName) ?: return@forEach
                    add(
                        key = DownloadMediaKeys.image(index),
                        label = "第 ${index + 1} 张图片",
                        files = listOf(file),
                    )
                }

                file.mimeType.startsWith("video/") &&
                    file.displayName.contains("_live") -> {
                    val index = leadingIndex(file.displayName) ?: return@forEach
                    add(
                        key = DownloadMediaKeys.live(index),
                        label = "第 ${index + 1} 张动图",
                        files = listOf(file),
                    )
                }

                file.mimeType.startsWith("audio/") -> {
                    add(
                        key = DownloadMediaKeys.MUSIC,
                        label = "BGM",
                        files = listOf(file),
                    )
                }

                request.kind == DownloadKind.COVER &&
                    file.mimeType.startsWith("image/") -> {
                    add(
                        key = DownloadMediaKeys.COVER,
                        label = "作品封面",
                        files = listOf(file),
                    )
                }
            }
        }

        task.zip?.let { zip ->
            add(
                key = DownloadMediaKeys.ZIP,
                label = "ZIP 打包",
                files = listOf(zip),
            )
        }
    }

    return result
}

fun findDuplicateDownloads(
    requests: List<DownloadRequest>,
    existing: Map<String, ExistingDuplicateContent>,
): DuplicateCheck {
    val matches = requests.mapNotNull { request ->
        val contents = request.requestedMediaKeys().mapNotNull { existing[it] }
        if (contents.isEmpty()) null else DuplicateMatch(request, contents)
    }
    return DuplicateCheck(matches)
}

private fun videoLabel(gear: VideoGear?): String {
    if (gear == null) return "视频"
    return buildString {
        append(gear.resolutionLabel)
        append(" · ")
        append(gear.codecLabel)
        gear.fps?.takeIf { it > 0 }?.let { append(" · ${it}fps") }
    }
}

private fun leadingIndex(name: String): Int? {
    val value = leadingIndexRegex.find(name)?.groupValues?.getOrNull(1) ?: return null
    return value.toIntOrNull()?.minus(1)
}

private val leadingIndexRegex = Regex("""^(\d+)""")

