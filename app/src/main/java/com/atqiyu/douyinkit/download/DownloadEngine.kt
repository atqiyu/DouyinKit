package com.atqiyu.douyinkit.download

import android.content.Context
import android.os.SystemClock
import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.core.model.VideoCodec
import com.atqiyu.douyinkit.core.model.VideoGear
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class DownloadOutcome(
    val files: List<SavedFile> = emptyList(),
    val zip: SavedFile? = null,
    val warnings: List<String> = emptyList(),
) {
    val isPartial: Boolean get() = warnings.isNotEmpty()
}

class DownloadEngine(
    private val context: Context,
    private val downloader: FileDownloader,
    private val saver: MediaStoreSaver,
    private val userAgent: String = com.atqiyu.douyinkit.core.net.DouyinUserAgent.DESKTOP_FALLBACK,
) {
    private val audioExtractor = AudioExtractor()
    private val zipExporter = ZipExporter()
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    suspend fun execute(
        request: DownloadRequest,
        onProgress: (DownloadProgress) -> Unit,
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        val tempRoot = (context.getExternalFilesDir(null) ?: context.filesDir)
            .resolve("downloads")
            .apply { mkdirs() }
        val tempDir = File(
            tempRoot,
            "${request.aweme.awemeId}_${System.currentTimeMillis()}",
        ).apply { mkdirs() }
        if (!tempDir.exists()) {
            throw DownloadException(DownloadFailure.STORAGE, "无法创建临时下载目录")
        }

        val progress = ProgressTracker(onProgress)
        progress.start()
        try {
            when (request.kind) {
                DownloadKind.VIDEO -> downloadVideo(request, tempDir, progress)
                DownloadKind.IMAGES -> downloadImages(request, tempDir, progress)
                DownloadKind.MUSIC -> downloadMusicOnly(request, tempDir, progress)
                DownloadKind.COVER -> downloadCover(request, tempDir, progress)
                DownloadKind.ZIP -> downloadImages(request.copy(createZip = true), tempDir, progress)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private suspend fun downloadVideo(
        request: DownloadRequest,
        tempDir: File,
        progress: ProgressTracker,
    ): DownloadOutcome {
        currentCoroutineContext().ensureActive()
        val aweme = request.aweme
        val video = aweme.video
            ?: throw DownloadException(DownloadFailure.NO_SOURCE, "该作品没有视频信息")
        val gear = request.gear
            ?: VideoGearSelector.select(aweme)
            ?: throw DownloadException(DownloadFailure.NO_SOURCE, "没有可下载的清晰度")
        val candidates = videoCandidates(aweme, gear)
        if (candidates.isEmpty()) {
            throw DownloadException(DownloadFailure.NO_SOURCE, "该清晰度没有可用地址")
        }

        progress.totalItems = 1
        progress.totalBytes = gear.sizeBytes
        val temp = File(tempDir, "video.mp4")
        val media = downloader.download(
            candidates = candidates,
            destination = temp,
            expectedSize = gear.sizeBytes,
            kind = MediaKind.VIDEO,
        ) { bytes, _ -> progress.updateBytes(bytes) }

        progress.itemDone()
        val saved = saver.saveVideo(
            source = media.file,
            displayName = DownloadNaming.videoDisplayName(aweme, gear),
        )
        return DownloadOutcome(files = listOf(saved))
    }

    private suspend fun downloadImages(
        request: DownloadRequest,
        tempDir: File,
        progress: ProgressTracker,
    ): DownloadOutcome {
        val aweme = request.aweme
        if (aweme.images.isEmpty()) {
            throw DownloadException(DownloadFailure.NO_SOURCE, "该作品没有图集图片")
        }

        val skipKeys = request.skipMediaKeys
        val warnings = mutableListOf<String>()
        val savedFiles = mutableListOf<SavedFile>()
        val zipEntries = mutableListOf<ZipEntrySource>()
        val folder = DownloadNaming.imageFolder(aweme)
        val imageFlags = aweme.images.mapIndexed { index, image ->
            val downloadImage = DownloadMediaKeys.image(index) !in skipKeys
            val downloadLive = request.includeLivePhotos &&
                image.isLivePhoto &&
                image.livePhoto != null &&
                DownloadMediaKeys.live(index) !in skipKeys
            downloadImage to downloadLive
        }
        val musicRequested = request.includeMusic &&
            aweme.music != null &&
            DownloadMediaKeys.MUSIC !in skipKeys
        val zipRequested = request.createZip && DownloadMediaKeys.ZIP !in skipKeys

        progress.totalItems = imageFlags.sumOf { (image, live) ->
            (if (image) 1 else 0) + (if (live) 1 else 0)
        } + (if (musicRequested) 1 else 0) + (if (zipRequested) 1 else 0)
        progress.totalBytes = null

        aweme.images.forEachIndexed { index, image ->
            currentCoroutineContext().ensureActive()
            val downloadImage = imageFlags[index].first
            val downloadLive = imageFlags[index].second

            if (downloadImage) {
                val candidates = buildList {
                    addAll(image.urls)
                    if (request.allowWatermarkedFallback) addAll(image.downloadUrls)
                }
                if (candidates.isEmpty()) {
                    warnings += "第 ${index + 1} 张图片没有下载地址"
                } else {
                    val temp = File(tempDir, "image_${index + 1}")
                    try {
                        val media = downloader.download(
                            candidates = candidates,
                            destination = temp,
                            expectedSize = null,
                            kind = MediaKind.IMAGE,
                        ) { bytes, _ -> progress.updateBytes(bytes) }
                        val displayName = DownloadNaming.imageDisplayName(index, media.extension)
                        val saved = saver.saveImage(
                            source = media.file,
                            displayName = displayName,
                            mimeType = media.mimeType,
                            folder = "DouyinKit/$folder",
                        )
                        savedFiles += saved
                        zipEntries += ZipEntrySource(media.file, displayName)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        warnings += "第 ${index + 1} 张图片下载失败：${error.message}"
                    }
                }
                progress.itemDone()
            }

            val live = image.livePhoto
            if (downloadLive && live != null) {
                currentCoroutineContext().ensureActive()
                val liveCandidates = buildList {
                    addAll(live.urls)
                    live.playApi?.let(::add)
                    if (request.allowWatermarkedFallback) addAll(live.downloadUrls)
                }
                if (liveCandidates.isEmpty()) {
                    warnings += "第 ${index + 1} 张动图没有视频地址"
                } else {
                    val liveTemp = File(tempDir, "image_${index + 1}_live.mp4")
                    try {
                        val media = downloader.download(
                            candidates = liveCandidates,
                            destination = liveTemp,
                            expectedSize = live.sizeBytes,
                            kind = MediaKind.VIDEO,
                        ) { bytes, _ -> progress.updateBytes(bytes) }
                        val name = DownloadNaming.liveDisplayName(index)
                        val saved = saver.saveLivePhotoVideo(
                            source = media.file,
                            displayName = name,
                            folder = "DouyinKit/$folder",
                        )
                        savedFiles += saved
                        zipEntries += ZipEntrySource(media.file, name)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        warnings += "第 ${index + 1} 张动图下载失败：${error.message}"
                    }
                }
                progress.itemDone()
            }
        }

        if (musicRequested) {
            currentCoroutineContext().ensureActive()
            val musicFile = try {
                downloadMusic(aweme, request.gear, tempDir) { bytes, _ ->
                    progress.updateBytes(bytes)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                warnings += "BGM 下载失败：${error.message}"
                null
            }
            musicFile?.let { media ->
                val name = DownloadNaming.musicDisplayName(aweme, media.extension)
                val saved = saver.saveAudio(
                    source = media.file,
                    displayName = name,
                    mimeType = media.mimeType,
                )
                savedFiles += saved
                zipEntries += ZipEntrySource(media.file, name)
            }
            progress.itemDone()
        }

        var zipSaved: SavedFile? = null
        if (zipRequested) {
            currentCoroutineContext().ensureActive()
            val info = buildPostInfo(aweme, zipEntries.map { it.entryName })
            val zipTemp = File(tempDir, "post.zip")
            try {
                val zipFile = zipExporter.zip(
                    sources = zipEntries,
                    outputFile = zipTemp,
                    infoJson = json.encodeToString(info),
                ) { written, total -> progress.updateBytes(written, total) }
                zipSaved = saver.saveDownload(
                    source = zipFile,
                    displayName = DownloadNaming.zipDisplayName(aweme),
                )
            } catch (error: Exception) {
                warnings += "ZIP 打包失败：${error.message}"
            }
            progress.itemDone()
        }

        if (savedFiles.isEmpty() && zipSaved == null) {
            throw DownloadException(
                DownloadFailure.NO_SOURCE,
                warnings.firstOrNull() ?: "没有成功下载任何文件",
            )
        }
        return DownloadOutcome(files = savedFiles, zip = zipSaved, warnings = warnings)
    }

    private suspend fun downloadMusicOnly(
        request: DownloadRequest,
        tempDir: File,
        progress: ProgressTracker,
    ): DownloadOutcome {
        val aweme = request.aweme
        progress.totalItems = 1
        val media = downloadMusic(aweme, request.gear, tempDir) { bytes, _ ->
            progress.updateBytes(bytes)
        }
        progress.itemDone()
        val saved = saver.saveAudio(
            source = media.file,
            displayName = DownloadNaming.musicDisplayName(aweme, media.extension),
            mimeType = media.mimeType,
        )
        return DownloadOutcome(files = listOf(saved))
    }

    private suspend fun downloadCover(
        request: DownloadRequest,
        tempDir: File,
        progress: ProgressTracker,
    ): DownloadOutcome {
        val aweme = request.aweme
        val url = aweme.primaryCoverUrl
            ?: throw DownloadException(DownloadFailure.NO_SOURCE, "没有封面地址")
        progress.totalItems = 1
        val temp = File(tempDir, "cover")
        val media = downloader.download(
            candidates = listOf(url),
            destination = temp,
            expectedSize = null,
            kind = MediaKind.IMAGE,
        ) { bytes, _ -> progress.updateBytes(bytes) }
        progress.itemDone()
        val saved = saver.saveImage(
            source = media.file,
            displayName = DownloadNaming.coverDisplayName(aweme, media.extension),
            mimeType = media.mimeType,
        )
        return DownloadOutcome(files = listOf(saved))
    }

    private suspend fun downloadMusic(
        aweme: Aweme,
        preferredGear: VideoGear?,
        tempDir: File,
        onBytes: (Long, Long?) -> Unit,
    ): DownloadedMedia {
        val music = aweme.music
            ?: throw DownloadException(DownloadFailure.NO_SOURCE, "该作品没有 BGM 信息")

        if (music.playUrls.isNotEmpty()) {
            val temp = File(tempDir, "music")
            try {
                return downloader.download(
                    candidates = music.playUrls,
                    destination = temp,
                    expectedSize = null,
                    kind = MediaKind.AUDIO,
                    onProgress = onBytes,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Fall through to extracting the audio track from the video.
            }
        }

        val video = aweme.video
            ?: throw DownloadException(
                DownloadFailure.NO_SOURCE,
                "没有 BGM 直链，且该作品没有可用于提取音频的视频",
            )
        val gear = preferredGear
            ?: VideoGearSelector.select(aweme)
            ?: throw DownloadException(DownloadFailure.NO_SOURCE, "没有可用的视频档位")
        val candidates = videoCandidates(aweme, gear)
        if (candidates.isEmpty()) {
            throw DownloadException(DownloadFailure.NO_SOURCE, "没有可用的视频地址")
        }

        val videoTemp = File(tempDir, "music_source.mp4")
        val videoMedia = downloader.download(
            candidates = candidates,
            destination = videoTemp,
            expectedSize = gear.sizeBytes,
            kind = MediaKind.VIDEO,
            onProgress = onBytes,
        )
        val audioTemp = File(tempDir, "music.m4a")
        audioExtractor.extractAudio(videoMedia.file, audioTemp) { current, duration ->
            if (duration > 0) onBytes(current, duration)
        }
        return DownloadedMedia(audioTemp, "m4a", "audio/mp4", audioTemp.length())
    }

    private fun videoCandidates(aweme: Aweme, gear: VideoGear): List<String> {
        val video = aweme.video ?: return emptyList()
        return buildList {
            addAll(gear.urls)
            gear.playApi?.let(::add)
            if (gear.name == "default" || gear.urls.isEmpty()) {
                addAll(video.defaultUrls)
            }
        }.distinct()
    }

    private fun buildPostInfo(aweme: Aweme, files: List<String>): PostInfo {
        return PostInfo(
            awemeId = aweme.awemeId,
            title = aweme.desc,
            author = aweme.author.displayName,
            kind = aweme.kind.name,
            sourceUrl = aweme.sourceUrl.orEmpty(),
            canonicalUrl = aweme.canonicalUrl,
            parsedAt = aweme.parsedAtMillis,
            files = files,
            disclaimer = "仅用于个人学习与已授权内容的本地备份，请遵守平台条款与版权规定。",
        )
    }

    @Serializable
    private data class PostInfo(
        val awemeId: String,
        val title: String,
        val author: String,
        val kind: String,
        val sourceUrl: String,
        val canonicalUrl: String,
        val parsedAt: Long,
        val files: List<String>,
        val disclaimer: String,
    )

    private class ProgressTracker(
        private val callback: (DownloadProgress) -> Unit,
    ) {
        var totalItems: Int = 0
        var totalBytes: Long? = null
        private var doneItems: Int = 0
        private var baseBytes: Long = 0
        private var currentBytes: Long = 0
        private var maxBytes: Long = 0
        private var startedAt: Long = 0
        private var lastEmitAt: Long = 0
        private var lastEmitBytes: Long = 0

        fun start() {
            startedAt = SystemClock.elapsedRealtime()
            lastEmitAt = startedAt
            emit(force = true)
        }

        fun updateBytes(bytes: Long, total: Long? = null) {
            currentBytes = bytes.coerceAtLeast(0)
            if (total != null && total > 0) {
                totalBytes = (baseBytes + total).coerceAtLeast(currentBytes)
            }
            val aggregate = baseBytes + currentBytes
            if (aggregate > maxBytes) maxBytes = aggregate
            val now = SystemClock.elapsedRealtime()
            if (now - lastEmitAt >= 300 || aggregate - lastEmitBytes >= 512 * 1024) {
                emit()
            }
        }

        fun itemDone() {
            doneItems += 1
            baseBytes = maxBytes
            currentBytes = 0
            emit(force = true)
        }

        private fun emit(force: Boolean = false) {
            val now = SystemClock.elapsedRealtime()
            if (!force && now - lastEmitAt < 200) return
            lastEmitAt = now
            lastEmitBytes = maxBytes + currentBytes
            val elapsed = (now - startedAt).coerceAtLeast(1)
            val bytes = maxBytes + currentBytes
            val speed = bytes * 1000 / elapsed
            callback(
                DownloadProgress(
                    bytes = bytes,
                    total = totalBytes,
                    itemsDone = doneItems,
                    itemsTotal = totalItems,
                    speedBps = speed,
                ),
            )
        }
    }
}

object VideoGearSelector {
    fun select(
        aweme: Aweme,
        preferH264: Boolean = true,
        preferredHeight: Int = 1080,
    ): VideoGear? {
        val gears = aweme.video?.gears.orEmpty().filter { it.hasDownloadSource }
        if (gears.isEmpty()) return null

        gears.firstOrNull { it.name == "normal_${preferredHeight}_0" }?.let { return it }

        val h264 = gears.filter { it.codec == VideoCodec.H264 }
        val pool = if (preferH264 && h264.isNotEmpty()) h264 else gears
        return pool
            .filter { gear ->
                val side = gear.shortSide ?: 0
                side in (preferredHeight - 200)..(preferredHeight + 200)
            }
            .maxByOrNull { it.bitrateBps ?: 0 }
            ?: pool
                .filter { (it.shortSide ?: 0) in 1..1920 }
                .maxByOrNull { it.bitrateBps ?: 0 }
            ?: pool.maxByOrNull { it.bitrateBps ?: 0 }
    }
}
