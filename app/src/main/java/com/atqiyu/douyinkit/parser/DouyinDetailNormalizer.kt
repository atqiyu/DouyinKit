package com.atqiyu.douyinkit.parser

import com.atqiyu.douyinkit.core.model.Author
import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.core.model.DouyinKitException
import com.atqiyu.douyinkit.core.model.ErrorCode
import com.atqiyu.douyinkit.core.model.ImageInfo
import com.atqiyu.douyinkit.core.model.LivePhoto
import com.atqiyu.douyinkit.core.model.MusicInfo
import com.atqiyu.douyinkit.core.model.ParseSource
import com.atqiyu.douyinkit.core.model.Stats
import com.atqiyu.douyinkit.core.model.VideoCodec
import com.atqiyu.douyinkit.core.model.VideoGear
import com.atqiyu.douyinkit.core.model.VideoInfo
import com.atqiyu.douyinkit.core.util.LinkExtractor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Converts both the web detail API (snake_case) and the page player model (camelCase) into the
 * app's stable model. Upstream fields vary a lot between A/B buckets, so every field is optional.
 */
class DouyinDetailNormalizer(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun normalize(
        raw: JsonObject,
        expectedAwemeId: String,
        source: ParseSource = ParseSource.DETAIL,
        sourceUrl: String? = null,
        limited: Boolean = false,
    ): Aweme {
        val detail = unwrapDetail(raw)
        val awemeId = detail.firstString("aweme_id", "awemeId")
            ?: throw DouyinKitException(ErrorCode.DETAIL_EMPTY, "详情中缺少作品 ID")
        if (awemeId != expectedAwemeId) {
            throw DouyinKitException(
                ErrorCode.AWEME_ID_MISMATCH,
                "返回的作品 ID 与请求不一致（$awemeId）",
            )
        }

        val images = parseImages(detail)
        val isSlides = detail.firstBool("is_slides", "isSlides") || images.isNotEmpty()
        val kind = if (images.isNotEmpty() || isSlides) AwemeKind.IMAGES else AwemeKind.VIDEO
        val video = if (kind == AwemeKind.VIDEO) parseVideo(detail.firstObject("video")) else null

        if (kind == AwemeKind.VIDEO && (video == null || (video.gears.isEmpty() && video.defaultUrls.isEmpty()))) {
            throw DouyinKitException(ErrorCode.NO_MEDIA, "没有找到视频播放地址")
        }
        if (kind == AwemeKind.IMAGES && images.isEmpty()) {
            throw DouyinKitException(ErrorCode.NO_MEDIA, "没有找到图集图片")
        }

        val kindHint = if (kind == AwemeKind.IMAGES) AwemeKind.IMAGES else AwemeKind.VIDEO
        val canonical = LinkExtractor.canonicalUrl(awemeId, kindHint)

        return Aweme(
            awemeId = awemeId,
            kind = kind,
            isSlides = isSlides,
            awemeType = detail.firstInt("aweme_type", "awemeType"),
            mediaType = detail.firstInt("media_type", "mediaType"),
            desc = detail.firstString("desc", "item_title", "itemTitle").orEmpty(),
            createTimeSeconds = detail.firstLong("create_time", "createTime"),
            author = parseAuthor(detail),
            stats = parseStats(detail),
            video = video,
            images = images,
            music = parseMusic(detail),
            sourceUrl = sourceUrl,
            canonicalUrl = canonical,
            parseSource = source,
            limited = limited,
        )
    }

    fun normalizePageModel(
        raw: JsonObject,
        expectedAwemeId: String,
    ): Aweme? {
        return try {
            normalize(raw, expectedAwemeId, ParseSource.PAGE_MODEL)
        } catch (_: DouyinKitException) {
            null
        }
    }

    /** Fills gaps in a detail-API result with data extracted from the rendered page model. */
    fun enrich(base: Aweme, pageRaw: JsonObject): Aweme {
        val page = unwrapDetail(pageRaw)
        val pageVideo = parseVideo(page.firstObject("video"))
        val pageImages = parseImages(page)
        val pageMusic = parseMusic(page)
        val pageAuthor = parseAuthor(page)
        val pageStats = parseStats(page)

        var changed = false

        val mergedVideo = if (base.kind == AwemeKind.VIDEO) {
            when {
                base.video == null -> pageVideo
                pageVideo == null -> base.video
                else -> mergeVideo(base.video, pageVideo).also { changed = true }
            }
        } else {
            null
        }

        val mergedImages = if (base.images.isEmpty() && pageImages.isNotEmpty()) {
            changed = true
            pageImages
        } else if (pageImages.isNotEmpty()) {
            mergeImages(base.images, pageImages).also { if (it != base.images) changed = true }
        } else {
            base.images
        }

        val mergedMusic = when {
            base.music == null -> pageMusic
            pageMusic == null -> base.music
            else -> mergeMusic(base.music, pageMusic).also { changed = true }
        }

        val mergedAuthor = if (base.author.avatarUrls.isEmpty() && pageAuthor.avatarUrls.isNotEmpty()) {
            changed = true
            base.author.copy(avatarUrls = pageAuthor.avatarUrls)
        } else {
            base.author
        }

        val mergedStats = if (base.stats.diggCount == null && pageStats.diggCount != null) {
            changed = true
            pageStats
        } else {
            base.stats
        }

        val pageDesc = page.firstString("desc", "item_title", "itemTitle").orEmpty()
        val mergedDesc = if (base.desc.isBlank() && pageDesc.isNotBlank()) {
            changed = true
            pageDesc
        } else {
            base.desc
        }

        return base.copy(
            desc = mergedDesc,
            author = mergedAuthor,
            stats = mergedStats,
            video = mergedVideo,
            images = mergedImages,
            music = mergedMusic,
            parseSource = if (changed) ParseSource.MIXED else base.parseSource,
        )
    }

    private fun mergeVideo(base: VideoInfo, page: VideoInfo): VideoInfo {
        val byName = base.gears.associateBy { it.name }.toMutableMap()
        val merged = base.gears.toMutableList()

        page.gears.forEach { candidate ->
            val existing = byName[candidate.name]
            if (existing == null) {
                merged += candidate
                byName[candidate.name] = candidate
            } else {
                val index = merged.indexOf(existing)
                val filled = existing.copy(
                    codec = if (existing.codec == VideoCodec.UNKNOWN) candidate.codec else existing.codec,
                    width = existing.width ?: candidate.width,
                    height = existing.height ?: candidate.height,
                    fps = candidate.fps?.takeIf { it > 0 } ?: existing.fps,
                    bitrateBps = existing.bitrateBps ?: candidate.bitrateBps,
                    sizeBytes = existing.sizeBytes ?: candidate.sizeBytes,
                    urls = if (existing.urls.isEmpty()) candidate.urls else existing.urls,
                    playApi = existing.playApi ?: candidate.playApi,
                )
                merged[index] = filled
                byName[candidate.name] = filled
            }
        }

        return base.copy(
            width = base.width ?: page.width,
            height = base.height ?: page.height,
            coverUrls = (base.coverUrls + page.coverUrls).cleanDistinct(),
            defaultUrls = if (base.defaultUrls.isEmpty()) page.defaultUrls else base.defaultUrls,
            gears = merged.distinctBy { "${it.name}_${it.height}_${it.bitrateBps}" },
        )
    }

    private fun mergeImages(base: List<ImageInfo>, page: List<ImageInfo>): List<ImageInfo> {
        if (page.isEmpty()) return base
        val byUri = base.mapIndexedNotNull { index, image ->
            image.uri?.let { it to index }
        }.toMap()
        val merged = base.toMutableList()

        page.forEach { candidate ->
            val index = candidate.uri?.let { byUri[it] }
                ?: candidate.index.takeIf { it in base.indices && (base[it].uri == null || candidate.uri == null) }
            if (index == null || index !in merged.indices) {
                merged += candidate.copy(index = merged.size)
            } else {
                val existing = merged[index]
                merged[index] = existing.copy(
                    urls = if (existing.urls.isEmpty()) candidate.urls else existing.urls,
                    downloadUrls = if (existing.downloadUrls.isEmpty()) candidate.downloadUrls else existing.downloadUrls,
                    width = existing.width ?: candidate.width,
                    height = existing.height ?: candidate.height,
                    format = existing.format ?: candidate.format,
                    isLivePhoto = existing.isLivePhoto || candidate.isLivePhoto,
                    livePhoto = existing.livePhoto ?: candidate.livePhoto,
                )
            }
        }
        return merged
    }

    private fun mergeMusic(base: MusicInfo, page: MusicInfo): MusicInfo {
        return base.copy(
            id = base.id ?: page.id,
            title = base.title.ifBlank { page.title },
            author = base.author.ifBlank { page.author },
            durationMs = if (base.durationMs > 0) base.durationMs else page.durationMs,
            playUrls = if (base.playUrls.isEmpty()) page.playUrls else base.playUrls,
            coverUrls = (base.coverUrls + page.coverUrls).cleanDistinct(),
            isOriginal = base.isOriginal || page.isOriginal,
        )
    }

    private fun unwrapDetail(raw: JsonObject): JsonObject {
        raw.firstObject("aweme_detail")?.let { return it }
        raw.firstObject("data")?.firstObject("aweme_detail")?.let { return it }
        raw.firstArray("item_list")?.firstOrNull()?.asObject()?.let { return it }
        return raw
    }

    private fun parseVideo(video: JsonObject?): VideoInfo? {
        if (video == null) return null
        val coverUrls = collectUrlsFromKeys(
            video,
            "cover",
            "coverUrlList",
            "cover_url_list",
            "origin_cover",
            "originCover",
            "dynamic_cover",
            "dynamicCover",
            "cover_url",
        )
        val defaultUrls = collectUrlsFromKeys(
            video,
            "play_addr",
            "playAddr",
            "play_addr_h264",
            "playAddrH264",
            "play_url",
            "playUrl",
        )
        val downloadUrls = collectUrlsFromKeys(
            video,
            "download_addr",
            "downloadAddr",
            "download",
        )
        val gears = parseGears(video)

        val fallbackGears = if (gears.isEmpty() && defaultUrls.isNotEmpty()) {
            listOf(
                VideoGear(
                    index = 0,
                    name = "default",
                    codec = VideoCodec.UNKNOWN,
                    width = video.firstInt("width"),
                    height = video.firstInt("height"),
                    urls = defaultUrls,
                    playApi = video.firstString("play_api", "playApi"),
                ),
            )
        } else {
            gears
        }

        return VideoInfo(
            durationMs = video.firstLong("duration", "duration_ms", "durationMs") ?: 0,
            width = video.firstInt("width"),
            height = video.firstInt("height"),
            ratio = video.firstString("ratio"),
            format = video.firstString("format") ?: "mp4",
            coverUrls = coverUrls,
            defaultUrls = defaultUrls,
            downloadUrls = downloadUrls,
            gears = fallbackGears.distinctBy { "${it.name}_${it.height}_${it.bitrateBps}" },
        )
    }

    private fun parseGears(video: JsonObject): List<VideoGear> {
        val array = video.firstArray("bit_rate", "bitRateList", "bit_rate_list", "bitRate") ?: return emptyList()
        return array.mapIndexedNotNull { index, element ->
            val gear = element.asObject() ?: return@mapIndexedNotNull null
            val playAddr = gear.firstObject("play_addr", "playAddr")
            val playAddrArray = gear.firstArray("play_addr", "playAddr")
            val firstAddr = playAddrArray?.firstOrNull()?.asObject()
            val gearName = gear.firstString("gear_name", "gearName", "name") ?: "gear_$index"
            val urls = collectUrlsFromKeys(
                gear,
                "play_addr",
                "playAddr",
                "play_addr_h265",
                "playAddrH265",
                "play_url",
                "playUrl",
            )
            val playApi = gear.firstString("play_api", "playApi")
                ?: gear.firstString("play_api_h265", "playApiH265")
            if (urls.isEmpty() && playApi.isNullOrBlank()) return@mapIndexedNotNull null

            val codec = detectCodec(gear, gearName, urls)
            val width = gear.firstInt("width", "play_width")
                ?: playAddr?.firstInt("width")
                ?: firstAddr?.firstInt("width")
            val height = gear.firstInt("height", "play_height")
                ?: playAddr?.firstInt("height")
                ?: firstAddr?.firstInt("height")
            val fps = gear.firstInt("fps", "FPS")
                ?: playAddr?.firstInt("fps")
                ?: firstAddr?.firstInt("fps")
            val bitrate = gear.firstLong("bit_rate", "bitRate", "bitrate", "bitrate_bps")
                ?.takeIf { it > 0 }
            val size = gear.firstLong("data_size", "dataSize", "size")
                ?.takeIf { it > 0 }
                ?: playAddr?.firstLong("data_size", "dataSize")?.takeIf { it > 0 }
                ?: firstAddr?.firstLong("data_size", "dataSize")?.takeIf { it > 0 }

            VideoGear(
                index = index,
                name = gearName,
                qualityType = gear.firstInt("quality_type", "qualityType"),
                codec = codec,
                width = width,
                height = height,
                fps = fps,
                bitrateBps = bitrate,
                sizeBytes = size,
                format = gear.firstString("format") ?: "mp4",
                urls = urls,
                playApi = playApi,
                watermarked = false,
            )
        }
    }

    private fun detectCodec(gear: JsonObject, name: String, urls: List<String>): VideoCodec {
        if (gear.firstBool("is_h265", "isH265")) return VideoCodec.H265
        if (gear.firstBool("is_h264", "isH264")) return VideoCodec.H264
        val lowerName = name.lowercase()
        val suffix = lowerName.substringAfterLast('_', "")
        if (suffix == "1" && lowerName.contains("adapt")) return VideoCodec.H265
        if (suffix == "0" && (lowerName.contains("normal") || lowerName.contains("low"))) {
            return VideoCodec.H264
        }
        val haystack = (name + " " + urls.joinToString(" ")).lowercase()
        return when {
            haystack.contains("h265") || haystack.contains("hevc") || haystack.contains("265") -> VideoCodec.H265
            haystack.contains("h264") || haystack.contains("avc") || haystack.contains("264") -> VideoCodec.H264
            else -> VideoCodec.UNKNOWN
        }
    }

    private fun parseImages(detail: JsonObject): List<ImageInfo> {
        val array = detail.firstArray("images")
            ?: detail.firstObject("image_post_info", "imagePostInfo")?.firstArray("images")
            ?: detail.firstArray("image_infos", "imageInfos")
            ?: return emptyList()

        return array.mapIndexedNotNull { index, element ->
            val image = element.asObject() ?: return@mapIndexedNotNull null
            val urls = collectUrlsFromKeys(
                image,
                "url_list",
                "urlList",
                "origin_url_list",
                "originUrlList",
                "display_image",
                "displayImage",
                "urls",
                "url",
            )
            val downloadUrls = collectUrlsFromKeys(
                image,
                "download_url_list",
                "downloadUrlList",
                "download_url",
                "downloadUrl",
            )
            val liveVideo = image.firstObject("video", "video_info", "videoInfo", "live_photo", "livePhoto")
            val livePhotoType = image.firstInt("live_photo_type", "livePhotoType") ?: 0
            val format = image.firstString("format") ?: urls.firstNotNullOfOrNull(::formatFromUrl)

            if (urls.isEmpty() && downloadUrls.isEmpty() && liveVideo == null) {
                return@mapIndexedNotNull null
            }

            ImageInfo(
                index = index,
                uri = image.firstString("uri", "image_uri", "imageUri"),
                width = image.firstInt("width"),
                height = image.firstInt("height"),
                format = format,
                urls = urls,
                downloadUrls = downloadUrls,
                isLivePhoto = liveVideo != null || livePhotoType > 0,
                livePhoto = parseLivePhoto(liveVideo),
            )
        }
    }

    private fun parseLivePhoto(video: JsonObject?): LivePhoto? {
        if (video == null) return null
        val urls = collectUrlsFromKeys(
            video,
            "play_addr",
            "playAddr",
            "play_addr_h265",
            "playAddrH265",
            "play_url",
            "playUrl",
        )
        val playApi = video.firstString("play_api", "playApi")
            ?: video.firstString("play_api_h265", "playApiH265")
        if (urls.isEmpty() && playApi.isNullOrBlank()) return null

        val coverUrls = collectUrlsFromKeys(
            video,
            "coverUrlList",
            "cover",
            "origin_cover",
            "originCover",
        )
        val downloadUrls = collectUrlsFromKeys(
            video,
            "download_addr",
            "downloadAddr",
            "download",
        )
        return LivePhoto(
            durationMs = video.firstLong("duration", "duration_ms", "durationMs") ?: 0,
            width = video.firstInt("width"),
            height = video.firstInt("height"),
            fps = video.firstInt("fps"),
            codec = detectCodec(video, "", urls),
            sizeBytes = video.firstLong("data_size", "dataSize")?.takeIf { it > 0 },
            urls = urls,
            playApi = playApi,
            downloadUrls = downloadUrls,
            coverUrls = coverUrls,
        )
    }

    private fun parseMusic(detail: JsonObject): MusicInfo? {
        val music = detail.firstObject("music") ?: return null
        val playUrls = collectUrlsFromKeys(
            music,
            "play_url",
            "playUrl",
            "play_url_list",
            "playUrlList",
            "url_list",
            "urlList",
            "urls",
        )
        val covers = collectUrlsFromKeys(
            music,
            "cover_large",
            "coverLarge",
            "cover_medium",
            "coverMedium",
            "cover_thumb",
            "coverThumb",
            "cover",
        )
        val rawDuration = music.firstLong("duration", "duration_ms", "durationMs") ?: 0
        val durationMs = when {
            rawDuration <= 0 -> 0
            rawDuration < 1000 -> rawDuration * 1000
            else -> rawDuration
        }
        val title = music.firstString("title", "name").orEmpty()
        if (playUrls.isEmpty() && title.isBlank() && covers.isEmpty()) return null
        return MusicInfo(
            id = music.firstString("id", "mid"),
            title = title,
            author = music.firstString("author", "author_name", "authorName").orEmpty(),
            durationMs = durationMs,
            playUrls = playUrls,
            coverUrls = covers,
            isOriginal = music.firstBool("is_original", "isOriginal"),
        )
    }

    private fun parseAuthor(detail: JsonObject): Author {
        val author = detail.firstObject("author", "authorInfo") ?: return Author()
        val avatarUrls = collectUrlsFromKeys(
            author,
            "avatar_thumb",
            "avatarThumb",
            "avatar_medium",
            "avatarMedium",
            "avatar_larger",
            "avatarLarger",
            "avatar_168x168",
            "avatar_300x300",
            "avatar",
        )
        return Author(
            uid = author.firstString("uid", "user_id", "userId"),
            secUid = author.firstString("sec_uid", "secUid"),
            uniqueId = author.firstString("unique_id", "uniqueId", "short_id", "shortId"),
            nickname = author.firstString("nickname", "name").orEmpty(),
            avatarUrls = avatarUrls,
        )
    }

    private fun parseStats(detail: JsonObject): Stats {
        val stats = detail.firstObject("statistics", "stats") ?: return Stats()
        return Stats(
            diggCount = stats.firstLong("digg_count", "diggCount"),
            commentCount = stats.firstLong("comment_count", "commentCount"),
            shareCount = stats.firstLong("share_count", "shareCount"),
            collectCount = stats.firstLong("collect_count", "collectCount"),
            playCount = stats.firstLong("play_count", "playCount"),
        )
    }

    private fun collectUrlsFromKeys(obj: JsonObject, vararg keys: String): List<String> {
        val urls = mutableListOf<String>()
        keys.forEach { key ->
            obj[key]?.let { urls += collectUrls(it) }
        }
        return urls.cleanDistinct()
    }

    private fun collectUrls(element: JsonElement?): List<String> {
        if (element == null) return emptyList()
        return when (element) {
            is JsonPrimitive -> {
                if (!element.isString) return emptyList()
                val raw = element.content.trim()
                when {
                    raw.startsWith("http://", ignoreCase = true) ||
                        raw.startsWith("https://", ignoreCase = true) -> listOf(raw)

                    raw.startsWith("//") -> listOf("https:$raw")
                    else -> emptyList()
                }
            }

            is JsonArray -> element.flatMap(::collectUrls)

            is JsonObject -> {
                val out = mutableListOf<String>()
                URL_OBJECT_KEYS.forEach { key ->
                    element[key]?.let { out += collectUrls(it) }
                }
                out
            }

            else -> emptyList()
        }
    }

    private fun List<String>.cleanDistinct(): List<String> {
        return mapNotNull { raw ->
            val cleaned = raw.replace("&amp;", "&").trim()
            if (cleaned.length > 8192) return@mapNotNull null
            if (!cleaned.startsWith("http://", true) && !cleaned.startsWith("https://", true)) {
                return@mapNotNull null
            }
            cleaned
        }.distinct()
    }

    private fun formatFromUrl(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpeg", "jpg" -> "jpg"
            "webp", "png", "gif", "avif", "heic", "bmp" -> ext
            else -> null
        }
    }

    private fun JsonObject.firstString(vararg keys: String): String? {
        keys.forEach { key ->
            val element = this[key] ?: return@forEach
            val text = when (element) {
                is JsonPrimitive -> {
                    if (element.isString) element.content.trim() else element.content.trim()
                }
                else -> null
            }
            if (!text.isNullOrBlank() && text != "null") return text
        }
        return null
    }

    private fun JsonObject.firstLong(vararg keys: String): Long? {
        keys.forEach { key ->
            val value = this[key]?.asLongOrNull()
            if (value != null) return value
        }
        return null
    }

    private fun JsonObject.firstInt(vararg keys: String): Int? {
        keys.forEach { key ->
            val value = this[key]?.asLongOrNull()
            if (value != null && value in Int.MIN_VALUE..Int.MAX_VALUE) return value.toInt()
        }
        return null
    }

    private fun JsonObject.firstBool(vararg keys: String): Boolean {
        keys.forEach { key ->
            val value = this[key]?.asBoolOrNull()
            if (value != null) return value
        }
        return false
    }

    private fun JsonObject.firstArray(vararg keys: String): JsonArray? {
        keys.forEach { key ->
            (this[key] as? JsonArray)?.let { return it }
        }
        return null
    }

    private fun JsonObject.firstObject(vararg keys: String): JsonObject? {
        keys.forEach { key ->
            (this[key] as? JsonObject)?.let { return it }
        }
        return null
    }

    private fun JsonElement.asObject(): JsonObject? = this as? JsonObject

    private fun JsonElement.asLongOrNull(): Long? {
        val primitive = this as? JsonPrimitive ?: return null
        return primitive.content.toLongOrNull()
            ?: primitive.content.toDoubleOrNull()?.toLong()
    }

    private fun JsonElement.asBoolOrNull(): Boolean? {
        val primitive = this as? JsonPrimitive ?: return null
        return when (primitive.content.lowercase()) {
            "true", "1" -> true
            "false", "0", "" -> false
            else -> null
        }
    }

    companion object {
        private val URL_OBJECT_KEYS = listOf(
            "url",
            "src",
            "play_url",
            "playUrl",
            "url_list",
            "urlList",
            "urls",
            "download_url",
            "downloadUrl",
        )
    }
}
