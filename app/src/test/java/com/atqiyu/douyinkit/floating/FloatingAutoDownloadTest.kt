package com.atqiyu.douyinkit.floating

import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.core.model.ImageInfo
import com.atqiyu.douyinkit.core.model.MusicInfo
import com.atqiyu.douyinkit.core.model.VideoCodec
import com.atqiyu.douyinkit.core.model.VideoGear
import com.atqiyu.douyinkit.core.model.VideoInfo
import com.atqiyu.douyinkit.data.prefs.AppSettings
import com.atqiyu.douyinkit.download.DownloadKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingAutoDownloadTest {

    private fun gear(
        name: String,
        width: Int,
        height: Int,
        codec: VideoCodec,
        bitrate: Long,
    ) = VideoGear(
        index = 0,
        name = name,
        codec = codec,
        width = width,
        height = height,
        bitrateBps = bitrate,
        sizeBytes = 1_000_000,
        urls = listOf("https://v11-weba.douyinvod.com/$name.mp4"),
    )

    @Test
    fun videoUsesConfiguredDefaultQuality() {
        val aweme = Aweme(
            awemeId = "1",
            kind = AwemeKind.VIDEO,
            video = VideoInfo(
                gears = listOf(
                    gear("gear_4k", 2160, 3840, VideoCodec.H265, 6_000_000),
                    gear("gear_1080", 1080, 1920, VideoCodec.H264, 4_600_000),
                ),
            ),
        )

        val request = buildFloatingDownloadRequest(
            aweme,
            AppSettings(defaultQualityHeight = 1080, preferH264 = true),
        )
        assertEquals(DownloadKind.VIDEO, request?.kind)
        assertEquals("gear_1080", request?.gear?.name)
    }

    @Test
    fun imagePostFollowsSavedDefaults() {
        val aweme = Aweme(
            awemeId = "2",
            kind = AwemeKind.IMAGES,
            images = listOf(ImageInfo(index = 0, urls = listOf("https://cdn/1.webp"))),
            music = MusicInfo(title = "BGM", playUrls = listOf("https://cdn/bgm.mp3")),
        )

        val request = buildFloatingDownloadRequest(
            aweme,
            AppSettings(
                includeLivePhotos = false,
                includeMusic = true,
                createZip = true,
            ),
        )
        assertEquals(DownloadKind.IMAGES, request?.kind)
        assertEquals(false, request?.includeLivePhotos)
        assertTrue(request?.includeMusic == true)
        assertTrue(request?.createZip == true)
    }

    @Test
    fun videoWithoutDownloadableGearIsSkipped() {
        val aweme = Aweme(
            awemeId = "3",
            kind = AwemeKind.VIDEO,
            video = VideoInfo(gears = emptyList()),
        )
        assertNull(buildFloatingDownloadRequest(aweme, AppSettings()))
    }
}
