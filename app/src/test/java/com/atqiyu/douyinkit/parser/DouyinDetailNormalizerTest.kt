package com.atqiyu.douyinkit.parser

import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.core.model.DouyinKitException
import com.atqiyu.douyinkit.core.model.ErrorCode
import com.atqiyu.douyinkit.core.model.ParseSource
import com.atqiyu.douyinkit.core.model.VideoCodec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyinDetailNormalizerTest {

    private val normalizer = DouyinDetailNormalizer()
    private val json = Json { isLenient = true }

    private fun obj(raw: String) = json.parseToJsonElement(raw).jsonObject

    @Test
    fun normalizesVideoDetailWithGearsMusicAndAuthor() {
        val aweme = normalizer.normalize(
            obj(VIDEO_DETAIL),
            "7697896601788248226",
            ParseSource.DETAIL,
            "https://v.douyin.com/i_mOpOTb9qM",
        )

        assertEquals(AwemeKind.VIDEO, aweme.kind)
        assertEquals("测试视频", aweme.desc)
        assertEquals("作者", aweme.author.displayName)
        assertEquals("123", aweme.author.uid)
        assertEquals(1, aweme.author.avatarUrls.size)
        assertEquals(12L, aweme.stats.diggCount)
        assertEquals(3L, aweme.stats.commentCount)
        assertEquals(4L, aweme.stats.shareCount)
        assertEquals(1792306221L, aweme.createTimeSeconds)
        assertEquals(3, aweme.video?.gears?.size)

        val gear1080 = aweme.video!!.gears.first { it.name == "normal_1080_0" }
        assertEquals(VideoCodec.UNKNOWN, gear1080.codec)
        assertEquals(1080, gear1080.width)
        assertEquals(1920, gear1080.height)
        assertEquals(30, gear1080.fps)
        assertEquals(4674026L, gear1080.bitrateBps)
        assertEquals(3594326L, gear1080.sizeBytes)
        assertEquals(1, gear1080.urls.size)

        val gear4k = aweme.video!!.gears.first { it.name == "adapt_lowest_4_1" }
        assertEquals(VideoCodec.H265, gear4k.codec)
        assertEquals(3840, gear4k.height)

        val gear720 = aweme.video!!.gears.first { it.name == "normal_720_0" }
        assertEquals(VideoCodec.H264, gear720.codec)
        assertEquals(2595910L, gear720.sizeBytes)
        assertEquals("https://www.douyin.com/aweme/v1/play/?x=1", gear720.playApi)
        assertEquals(1, gear720.urls.size)

        assertNotNull(aweme.music)
        assertEquals("原声", aweme.music!!.title)
        assertEquals(1, aweme.music!!.playUrls.size)
        assertTrue(aweme.music!!.isOriginal)
        assertEquals(6152L, aweme.music!!.durationMs)
    }

    @Test
    fun normalizesImagePostAndLivePhoto() {
        val aweme = normalizer.normalize(obj(IMAGE_DETAIL), "7700144500140762266")

        assertEquals(AwemeKind.IMAGES, aweme.kind)
        assertTrue(aweme.isSlides)
        assertNull(aweme.video)
        assertEquals(2, aweme.images.size)

        val first = aweme.images[0]
        assertEquals("a", first.uri)
        assertEquals("webp", first.format)
        assertEquals(1, first.urls.size)
        assertEquals(1, first.downloadUrls.size)
        assertEquals(false, first.isLivePhoto)

        val second = aweme.images[1]
        assertEquals(true, second.isLivePhoto)
        assertNotNull(second.livePhoto)
        assertEquals(1, second.livePhoto!!.urls.size)
        assertEquals(2449L, second.livePhoto!!.durationMs)
        assertEquals(217978L, second.livePhoto!!.sizeBytes)
        assertEquals(1, second.livePhoto!!.coverUrls.size)

        assertEquals("BGM", aweme.music?.title)
        assertEquals(15_000L, aweme.music?.durationMs)
    }

    @Test
    fun createsFallbackGearWhenBitRateIsEmpty() {
        val aweme = normalizer.normalize(
            obj(
                """
                {
                  "aweme_id": "7697896601788248226",
                  "desc": "fallback",
                  "video": {
                    "duration": 5000,
                    "width": 720,
                    "height": 1280,
                    "play_addr": {"url_list": ["https://v11-weba.douyinvod.com/default.mp4"]},
                    "bit_rate": []
                  },
                  "images": []
                }
                """.trimIndent(),
            ),
            "7697896601788248226",
        )
        assertEquals(1, aweme.video!!.gears.size)
        assertEquals("default", aweme.video!!.gears[0].name)
        assertEquals(1, aweme.video!!.gears[0].urls.size)
        assertEquals(720, aweme.video!!.gears[0].width)
    }

    @Test
    fun throwsOnAwemeIdMismatch() {
        val error = assertThrows(DouyinKitException::class.java) {
            normalizer.normalize(obj(VIDEO_DETAIL), "1234567890")
        }
        assertEquals(ErrorCode.AWEME_ID_MISMATCH, error.code)
    }

    @Test
    fun throwsWhenNoMedia() {
        val error = assertThrows(DouyinKitException::class.java) {
            normalizer.normalize(
                obj(
                    """
                    {"aweme_id":"7697896601788248226","desc":"empty","video":{"bit_rate":[]},"images":[]}
                    """.trimIndent(),
                ),
                "7697896601788248226",
            )
        }
        assertEquals(ErrorCode.NO_MEDIA, error.code)
    }

    @Test
    fun enrichesVideoWithPageModel() {
        val base = normalizer.normalize(obj(VIDEO_DETAIL), "7697896601788248226")
        val page = obj(
            """
            {
              "aweme_id": "7697896601788248226",
              "video": {
                "bitRateList": [
                  {
                    "gearName": "normal_1080_0",
                    "playApi": "https://www.douyin.com/aweme/v1/play/?page=1",
                    "fps": 60,
                    "playAddr": [{"src": ["https://v26-web.douyinvod.com/1080p60.mp4"], "width": 1080, "height": 1920}]
                  }
                ],
                "playApi": "https://www.douyin.com/aweme/v1/play/?video=1"
              },
              "music": {
                "title": "原声",
                "playUrl": {"urlList": ["https://sf6-cdn-tos.douyinstatic.com/obj/ies-music/page.mp3"]}
              }
            }
            """.trimIndent(),
        )
        val enriched = normalizer.enrich(base, page)
        val gear = enriched.video!!.gears.first { it.name == "normal_1080_0" }
        assertEquals(60, gear.fps)
        assertEquals("https://www.douyin.com/aweme/v1/play/?page=1", gear.playApi)
        assertEquals(ParseSource.MIXED, enriched.parseSource)
        assertTrue(enriched.music!!.playUrls.isEmpty().not())
    }

    @Test
    fun enrichesImageLivePhotoFromPageModel() {
        val base = normalizer.normalize(obj(IMAGE_DETAIL), "7700144500140762266")
        val page = obj(
            """
            {
              "aweme_id": "7700144500140762266",
              "images": [
                {
                  "uri": "a",
                  "video": {
                    "duration": 1800,
                    "fps": 30,
                    "playAddr": [{"src": ["https://v11-weba.douyinvod.com/a-live.mp4"], "width": 1440, "height": 1914}]
                  }
                }
              ]
            }
            """.trimIndent(),
        )
        val enriched = normalizer.enrich(base, page)
        val first = enriched.images.first { it.uri == "a" }
        assertEquals(true, first.isLivePhoto)
        assertNotNull(first.livePhoto)
        assertEquals("https://v11-weba.douyinvod.com/a-live.mp4", first.livePhoto!!.urls.first())
    }

    @Test
    fun parsesNumericStringsAndCamelCasePageModel() {
        val aweme = normalizer.normalize(
            obj(
                """
                {
                  "awemeId": "7697896601788248226",
                  "desc": "camel",
                  "createTime": "1792306221",
                  "authorInfo": {"nickname": "camel作者", "avatarThumb": {"urlList": ["https://p3.douyinpic.com/avatar.webp"]}},
                  "stats": {"diggCount": "99", "commentCount": 8},
                  "video": {
                    "duration": 3000,
                    "bitRateList": [
                      {
                        "gearName": "normal_720_0",
                        "isH265": true,
                        "playAddr": [{"src": ["https://v11-weba.douyinvod.com/720h265.mp4"], "width": 720, "height": 1280, "dataSize": "123456"}]
                      }
                    ]
                  },
                  "images": []
                }
                """.trimIndent(),
            ),
            "7697896601788248226",
        )
        assertEquals("camel作者", aweme.author.displayName)
        assertEquals(99L, aweme.stats.diggCount)
        assertEquals(1792306221L, aweme.createTimeSeconds)
        assertEquals(VideoCodec.H265, aweme.video!!.gears[0].codec)
        assertEquals(123456L, aweme.video!!.gears[0].sizeBytes)
    }

    companion object {
        private val VIDEO_DETAIL = """
            {
              "aweme_id": "7697896601788248226",
              "desc": "测试视频",
              "create_time": 1792306221,
              "aweme_type": 4,
              "author": {
                "uid": 123,
                "nickname": "作者",
                "sec_uid": "sec",
                "avatar_thumb": {"url_list": ["https://p3.douyinpic.com/avatar.webp"]}
              },
              "statistics": {
                "digg_count": 12,
                "comment_count": "3",
                "share_count": 4,
                "collect_count": 5
              },
              "video": {
                "duration": 6152,
                "width": 1080,
                "height": 1920,
                "ratio": "720p",
                "cover": {"url_list": ["https://p3.douyinpic.com/cover.webp"]},
                "play_addr": {"url_list": ["https://v11-weba.douyinvod.com/default.mp4"]},
                "bit_rate": [
                  {
                    "gear_name": "normal_1080_0",
                    "quality_type": 1,
                    "bit_rate": 4674026,
                    "fps": 30,
                    "play_addr": {
                      "url_list": ["https://v11-weba.douyinvod.com/1080.mp4"],
                      "width": 1080,
                      "height": 1920,
                      "data_size": 3594326
                    }
                  },
                  {
                    "gear_name": "adapt_lowest_4_1",
                    "quality_type": 72,
                    "is_h265": 1,
                    "play_addr": {
                      "url_list": ["https://v11-weba.douyinvod.com/4k.mp4"],
                      "width": 2160,
                      "height": 3840
                    }
                  },
                  {
                    "gear_name": "normal_720_0",
                    "bit_rate": 3376000,
                    "is_h264": true,
                    "playAddr": [
                      {
                        "src": ["https://v26-web.douyinvod.com/720.mp4"],
                        "dataSize": 2595910,
                        "width": 720,
                        "height": 1280
                      }
                    ],
                    "playApi": "https://www.douyin.com/aweme/v1/play/?x=1"
                  }
                ]
              },
              "music": {
                "id": "123",
                "title": "原声",
                "author": "作者",
                "duration": 6152,
                "play_url": {"url_list": ["https://sf6-cdn-tos.douyinstatic.com/obj/ies-music/123.mp3"]},
                "cover_large": {"url_list": ["https://p3.douyinpic.com/music.webp"]},
                "is_original": true
              },
              "images": []
            }
        """.trimIndent()

        private val IMAGE_DETAIL = """
            {
              "aweme_id": "7700144500140762266",
              "desc": "图文",
              "aweme_type": 68,
              "is_slides": true,
              "author": {"nickname": "作者"},
              "video": {
                "duration": 0,
                "width": 2160,
                "height": 2872,
                "play_addr": {"url_list": ["https://v11-weba.douyinvod.com/watermarked.mp4"]},
                "bit_rate": []
              },
              "images": [
                {
                  "uri": "a",
                  "width": 2160,
                  "height": 2872,
                  "url_list": ["https://p3-pc-sign.douyinpic.com/a.webp"],
                  "download_url_list": ["https://p3-pc-sign.douyinpic.com/a-water.webp"]
                },
                {
                  "uri": "b",
                  "width": 960,
                  "height": 720,
                  "url_list": ["https://p3-pc-sign.douyinpic.com/b.webp"],
                  "live_photo_type": 1,
                  "video": {
                    "duration": 2449,
                    "width": 960,
                    "height": 720,
                    "fps": 29,
                    "data_size": 217978,
                    "play_addr": {"url_list": ["https://v11-weba.douyinvod.com/live.mp4"]},
                    "coverUrlList": ["https://p3.douyinpic.com/livecover.webp"]
                  }
                }
              ],
              "music": {
                "title": "BGM",
                "duration": 15,
                "play_url": {"url_list": ["https://sf6-cdn-tos.douyinstatic.com/obj/ies-music/bgm.mp3"]}
              }
            }
        """.trimIndent()
    }
}
