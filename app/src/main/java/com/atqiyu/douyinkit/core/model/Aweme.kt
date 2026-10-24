package com.atqiyu.douyinkit.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Aweme(
    val schemaVersion: Int = 1,
    val awemeId: String,
    val kind: AwemeKind,
    val isSlides: Boolean = false,
    val awemeType: Int? = null,
    val mediaType: Int? = null,
    val desc: String = "",
    val createTimeSeconds: Long? = null,
    val author: Author = Author(),
    val stats: Stats = Stats(),
    val video: VideoInfo? = null,
    val images: List<ImageInfo> = emptyList(),
    val music: MusicInfo? = null,
    val sourceUrl: String? = null,
    val canonicalUrl: String = "",
    val parsedAtMillis: Long = System.currentTimeMillis(),
    val parseSource: ParseSource = ParseSource.DETAIL,
    val limited: Boolean = false,
) {
    val displayTitle: String
        get() = desc.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(80)
            .orEmpty()
            .ifBlank { "抖音作品 $awemeId" }

    val primaryCoverUrl: String?
        get() = video?.coverUrls?.firstOrNull()
            ?: images.firstOrNull()?.urls?.firstOrNull()

    val isVideo: Boolean get() = kind == AwemeKind.VIDEO
    val isImages: Boolean get() = kind == AwemeKind.IMAGES
}

@Serializable
enum class AwemeKind { VIDEO, IMAGES }

@Serializable
enum class VideoCodec { H264, H265, UNKNOWN }

@Serializable
enum class ParseSource { DETAIL, PAGE_MODEL, SSR, SHARE_FALLBACK, MIXED }

@Serializable
data class Author(
    val uid: String? = null,
    val secUid: String? = null,
    val uniqueId: String? = null,
    val nickname: String = "",
    val avatarUrls: List<String> = emptyList(),
) {
    val displayName: String get() = nickname.ifBlank { "抖音用户" }
}

@Serializable
data class Stats(
    val diggCount: Long? = null,
    val commentCount: Long? = null,
    val shareCount: Long? = null,
    val collectCount: Long? = null,
    val playCount: Long? = null,
)

@Serializable
data class VideoInfo(
    val durationMs: Long = 0,
    val width: Int? = null,
    val height: Int? = null,
    val ratio: String? = null,
    val format: String = "mp4",
    val coverUrls: List<String> = emptyList(),
    val defaultUrls: List<String> = emptyList(),
    val downloadUrls: List<String> = emptyList(),
    val gears: List<VideoGear> = emptyList(),
) {
    val aspectRatio: Float
        get() {
            val w = width ?: return 9f / 16f
            val h = height ?: return 9f / 16f
            if (w <= 0 || h <= 0) return 9f / 16f
            return w.toFloat() / h.toFloat()
        }
}

@Serializable
data class VideoGear(
    val index: Int,
    val name: String,
    val qualityType: Int? = null,
    val codec: VideoCodec = VideoCodec.UNKNOWN,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Int? = null,
    val bitrateBps: Long? = null,
    val sizeBytes: Long? = null,
    val format: String = "mp4",
    val urls: List<String> = emptyList(),
    val playApi: String? = null,
    val watermarked: Boolean = false,
) {
    val resolutionLabel: String
        get() {
            val h = height
            return when {
                h == null -> "未知清晰度"
                h >= 2000 -> "4K"
                h >= 1400 -> "2K"
                h >= 1000 -> "1080P"
                h >= 700 -> "720P"
                h >= 500 -> "540P"
                else -> "${h}P"
            }
        }

    val codecLabel: String
        get() = when (codec) {
            VideoCodec.H264 -> "H.264"
            VideoCodec.H265 -> "H.265"
            VideoCodec.UNKNOWN -> "未知编码"
        }

    val hasDownloadSource: Boolean
        get() = urls.isNotEmpty() || !playApi.isNullOrBlank()
}

@Serializable
data class ImageInfo(
    val index: Int,
    val uri: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val format: String? = null,
    val urls: List<String> = emptyList(),
    val downloadUrls: List<String> = emptyList(),
    val isLivePhoto: Boolean = false,
    val livePhoto: LivePhoto? = null,
) {
    val hasDownloadSource: Boolean
        get() = urls.isNotEmpty() || downloadUrls.isNotEmpty()
}

@Serializable
data class LivePhoto(
    val durationMs: Long = 0,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Int? = null,
    val codec: VideoCodec = VideoCodec.UNKNOWN,
    val sizeBytes: Long? = null,
    val urls: List<String> = emptyList(),
    val playApi: String? = null,
    val downloadUrls: List<String> = emptyList(),
    val coverUrls: List<String> = emptyList(),
) {
    val hasDownloadSource: Boolean
        get() = urls.isNotEmpty() || !playApi.isNullOrBlank()
}

@Serializable
data class MusicInfo(
    val id: String? = null,
    val title: String = "",
    val author: String = "",
    val durationMs: Long = 0,
    val playUrls: List<String> = emptyList(),
    val coverUrls: List<String> = emptyList(),
    val isOriginal: Boolean = false,
) {
    val displayTitle: String get() = title.ifBlank { "原声" }
    val hasDirectAudio: Boolean get() = playUrls.isNotEmpty()
}
