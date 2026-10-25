package com.atqiyu.douyinkit.download

import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.core.model.ImageInfo
import com.atqiyu.douyinkit.core.model.LivePhoto
import com.atqiyu.douyinkit.core.model.MusicInfo
import com.atqiyu.douyinkit.core.model.VideoCodec
import com.atqiyu.douyinkit.core.model.VideoGear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadDuplicatesTest {

    private fun imagesAweme(): Aweme = Aweme(
        awemeId = "100",
        kind = AwemeKind.IMAGES,
        images = listOf(
            ImageInfo(index = 0, urls = listOf("https://cdn/1.webp")),
            ImageInfo(
                index = 1,
                urls = listOf("https://cdn/2.webp"),
                isLivePhoto = true,
                livePhoto = LivePhoto(urls = listOf("https://cdn/2.mp4")),
            ),
        ),
        music = MusicInfo(title = "BGM", playUrls = listOf("https://cdn/bgm.mp3")),
    )

    private fun imageRequest(): DownloadRequest = DownloadRequest(
        aweme = imagesAweme(),
        kind = DownloadKind.IMAGES,
        includeLivePhotos = true,
        includeMusic = true,
        createZip = true,
    )

    @Test
    fun imageRequestExposesEveryDownloadableKey() {
        val keys = imageRequest().requestedMediaKeys()
        assertTrue(DownloadMediaKeys.image(0) in keys)
        assertTrue(DownloadMediaKeys.image(1) in keys)
        assertTrue(DownloadMediaKeys.live(1) in keys)
        assertTrue(DownloadMediaKeys.MUSIC in keys)
        assertTrue(DownloadMediaKeys.ZIP in keys)
    }

    @Test
    fun skipLeavesOnlyMissingImageContent() {
        val allKeys = imageRequest().requestedMediaKeys()
        assertFalse(imageRequest().hasContentAfterSkip(allKeys))
        assertTrue(
            imageRequest().hasContentAfterSkip(
                allKeys - setOf(DownloadMediaKeys.image(1), DownloadMediaKeys.live(1)),
            ),
        )
    }

    @Test
    fun duplicateMatchesOnlyExistingKeys() {
        val request = imageRequest()
        val existing = mapOf(
            DownloadMediaKeys.image(0) to ExistingDuplicateContent(
                DownloadMediaKeys.image(0),
                "第 1 张图片",
                emptyList(),
            ),
            DownloadMediaKeys.MUSIC to ExistingDuplicateContent(
                DownloadMediaKeys.MUSIC,
                "BGM",
                emptyList(),
            ),
        )

        val check = findDuplicateDownloads(listOf(request), existing)
        assertTrue(check.hasDuplicates)
        assertEquals(
            setOf(DownloadMediaKeys.image(0), DownloadMediaKeys.MUSIC),
            check.matches.single().keys,
        )
        assertEquals(listOf("第 1 张图片", "BGM"), check.labels)
    }

    @Test
    fun videoKeysUseVisibleQualityAndCodec() {
        val h264 = VideoGear(
            index = 0,
            name = "normal_1080_0",
            codec = VideoCodec.H264,
            width = 1080,
            height = 1920,
            fps = 30,
        )
        val h264Same = h264.copy(index = 1, name = "adapt_1080_0")
        val h265 = h264.copy(name = "adapt_1080_1", codec = VideoCodec.H265)

        assertEquals(DownloadMediaKeys.video(h264), DownloadMediaKeys.video(h264Same))
        assertNotEquals(DownloadMediaKeys.video(h264), DownloadMediaKeys.video(h265))
    }

    @Test
    fun videoSkipRemovesTheWholeRequest() {
        val gear = VideoGear(
            index = 0,
            name = "normal_1080_0",
            codec = VideoCodec.H264,
            width = 1080,
            height = 1920,
            fps = 30,
        )
        val request = DownloadRequest(
            aweme = Aweme(awemeId = "1", kind = AwemeKind.VIDEO),
            kind = DownloadKind.VIDEO,
            gear = gear,
        )

        assertTrue(request.hasContentAfterSkip(emptySet()))
        assertFalse(
            request.hasContentAfterSkip(setOf(DownloadMediaKeys.video(gear))),
        )
    }
}
