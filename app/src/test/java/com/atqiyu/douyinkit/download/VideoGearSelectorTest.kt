package com.atqiyu.douyinkit.download

import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.core.model.VideoCodec
import com.atqiyu.douyinkit.core.model.VideoGear
import com.atqiyu.douyinkit.core.model.VideoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoGearSelectorTest {

    @Test
    fun labelsPortraitResolutionByShortSide() {
        val gear = gear(
            name = "low_540_0",
            width = 576,
            height = 1024,
            codec = VideoCodec.H264,
            bitrate = 2_900_000,
        )
        assertEquals("540P", gear.resolutionLabel)
    }

    @Test
    fun choosesRequestedResolutionOnPortraitVideo() {
        val aweme = aweme(
            gear("gear_4k", 2160, 3840, VideoCodec.H265, 5_000_000),
            gear("gear_1080", 1080, 1920, VideoCodec.H264, 4_600_000),
            gear("gear_540", 576, 1024, VideoCodec.H264, 2_900_000),
        )
        assertEquals("gear_1080", VideoGearSelector.select(aweme, preferredHeight = 1080)?.name)
        assertEquals("gear_540", VideoGearSelector.select(aweme, preferredHeight = 540)?.name)
        val fourK = VideoGearSelector.select(
            aweme,
            preferH264 = false,
            preferredHeight = 2160,
        )
        assertEquals("gear_4k", fourK?.name)
        assertEquals("4K", fourK?.resolutionLabel)
    }

    @Test
    fun prefersNormal1080GearWhenPresent() {
        val aweme = aweme(
            gear("gear_1080", 1080, 1920, VideoCodec.H264, 4_600_000),
            gear("normal_1080_0", 1080, 1920, VideoCodec.H264, 4_000_000),
        )
        assertEquals("normal_1080_0", VideoGearSelector.select(aweme)?.name)
    }

    private fun aweme(vararg gears: VideoGear): Aweme {
        return Aweme(
            awemeId = "7697896601788248226",
            kind = AwemeKind.VIDEO,
            video = VideoInfo(gears = gears.toList()),
        )
    }

    private fun gear(
        name: String,
        width: Int,
        height: Int,
        codec: VideoCodec,
        bitrate: Long,
    ): VideoGear {
        return VideoGear(
            index = 0,
            name = name,
            codec = codec,
            width = width,
            height = height,
            bitrateBps = bitrate,
            sizeBytes = 1_000_000,
            urls = listOf("https://v11-weba.douyinvod.com/$name.mp4"),
        )
    }
}
