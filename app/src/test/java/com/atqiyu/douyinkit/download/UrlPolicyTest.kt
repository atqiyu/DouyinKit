package com.atqiyu.douyinkit.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {

    @Test
    fun allowsKnownDouyinCdnHosts() {
        assertTrue(UrlPolicy.isAllowed("https://v11-weba.douyinvod.com/video.mp4"))
        assertTrue(UrlPolicy.isAllowed("https://p3-pc-sign.douyinpic.com/image.webp"))
        assertTrue(UrlPolicy.isAllowed("https://sf6-cdn-tos.douyinstatic.com/music.mp3"))
        assertTrue(UrlPolicy.isAllowed("https://www.douyin.com/aweme/v1/play/?x=1"))
    }

    @Test
    fun rejectsLocalAndUnknownHosts() {
        assertFalse(UrlPolicy.isAllowed("http://127.0.0.1:8080/video.mp4"))
        assertFalse(UrlPolicy.isAllowed("https://localhost/video.mp4"))
        assertFalse(UrlPolicy.isAllowed("https://192.168.1.10/video.mp4"))
        assertFalse(UrlPolicy.isAllowed("https://example.com/video.mp4"))
        assertFalse(UrlPolicy.isAllowed("https://notdouyin.com/video.mp4"))
        assertFalse(UrlPolicy.isAllowed("file:///sdcard/video.mp4"))
    }

    @Test
    fun upgradesHttpToHttps() {
        val normalized = UrlPolicy.httpsUrlOrNull("http://v11-weba.douyinvod.com/a.mp4")
        assertEquals("https://v11-weba.douyinvod.com/a.mp4", normalized)
    }

    @Test
    fun returnsNullForInvalidUrl() {
        assertNull(UrlPolicy.httpsUrlOrNull("not a url"))
    }
}
