package com.atqiyu.douyinkit.core.util

import com.atqiyu.douyinkit.core.model.AwemeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkExtractorTest {

    @Test
    fun extractsShortLinkFromShareText() {
        val text = "7.95 复制打开抖音，看看【作者的作品】文案 https://v.douyin.com/i_mOpOTb9qM/ 复制此链接，打开抖音搜索，直接观看视频！"
        val links = LinkExtractor.extract(text)
        assertEquals(1, links.size)
        assertEquals("https://v.douyin.com/i_mOpOTb9qM/", links[0].url)
        assertEquals(null, links[0].awemeId)
    }

    @Test
    fun preservesShortCodeCase() {
        val text = "https://v.douyin.com/IOGKzR5b54o"
        val links = LinkExtractor.extract(text)
        assertEquals(1, links.size)
        assertTrue(links[0].url.contains("IOGKzR5b54o"))
    }

    @Test
    fun extractsLongVideoLinkWithQuery() {
        val text = "看看这个 https://www.douyin.com/video/7697896601788248226?previous_page=app_code_link 很有趣"
        val link = LinkExtractor.extractFirst(text)
        assertEquals("7697896601788248226", link?.awemeId)
        assertEquals(AwemeKind.VIDEO, link?.kindHint)
    }

    @Test
    fun extractsNoteAndSlidesAsImages() {
        val note = LinkExtractor.extractFirst("https://www.douyin.com/note/7700144500140762266")
        assertEquals("7700144500140762266", note?.awemeId)
        assertEquals(AwemeKind.IMAGES, note?.kindHint)

        val slides = LinkExtractor.extractFirst("https://www.iesdouyin.com/share/slides/7700144500140762266/")
        assertEquals("7700144500140762266", slides?.awemeId)
        assertEquals(AwemeKind.IMAGES, slides?.kindHint)
    }

    @Test
    fun extractsIesShareVideoLink() {
        val link = LinkExtractor.extractFirst("https://www.iesdouyin.com/share/video/7697896601788248226/?region=CN")
        assertEquals("7697896601788248226", link?.awemeId)
        assertEquals(AwemeKind.VIDEO, link?.kindHint)
    }

    @Test
    fun deduplicatesMultipleLinks() {
        val text = """
            https://www.douyin.com/video/7697896601788248226
            重复 https://v.douyin.com/abc123/
            再看 https://www.douyin.com/video/7697896601788248226?x=1
        """.trimIndent()
        val links = LinkExtractor.extract(text)
        assertEquals(2, links.size)
        assertEquals("7697896601788248226", links[0].awemeId)
    }

    @Test
    fun acceptsBareAwemeId() {
        val link = LinkExtractor.extractFirst("7697896601788248226")
        assertEquals("7697896601788248226", link?.awemeId)
        assertEquals("https://www.douyin.com/video/7697896601788248226", link?.url)
    }

    @Test
    fun acceptsSchemeLessShortLink() {
        val link = LinkExtractor.extractFirst("v.douyin.com/i_mOpOTb9qM")
        assertEquals("https://v.douyin.com/i_mOpOTb9qM", link?.url)
    }

    @Test
    fun stripsTrailingChinesePunctuation() {
        val link = LinkExtractor.extractFirst("链接：https://v.douyin.com/i_mOpOTb9qM，复制打开")
        assertEquals("https://v.douyin.com/i_mOpOTb9qM", link?.url)
    }

    @Test
    fun rejectsNonDouyinHosts() {
        assertTrue(LinkExtractor.extract("https://example.com/video/7697896601788248226").isEmpty())
        assertTrue(LinkExtractor.extract("https://notdouyin.com/video/7697896601788248226").isEmpty())
    }

    @Test
    fun keepsCaseSensitiveShortCodeInRawAndUrl() {
        val upper = LinkExtractor.extractFirst("https://v.douyin.com/lOGKzR5b54o")
        val lower = LinkExtractor.extractFirst("https://v.douyin.com/lOGKzR5b54o".lowercase())
        assertTrue(upper?.url?.contains("lOGKzR5b54o") == true)
        assertTrue(lower?.url?.contains("logkzr5b54o") == true)
    }
}
