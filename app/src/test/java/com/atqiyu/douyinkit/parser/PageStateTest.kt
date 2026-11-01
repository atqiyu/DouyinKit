package com.atqiyu.douyinkit.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageStateTest {

    @Test
    fun detectsChallengeFlagFromInjectedDomState() {
        val state = PageState(
            href = "https://www.douyin.com/video/1234567890",
            hasRoot = true,
            bodyLength = 120_000,
            challenge = true,
            challengeReason = "dom:iframe:captcha",
        )

        assertTrue(state.looksLikeChallenge)
    }

    @Test
    fun detectsVerificationNavigationUrl() {
        val state = PageState(
            href = "https://verify.zijieapi.com/captcha/index.html",
            hasRoot = true,
            bodyLength = 30_000,
        )

        assertTrue(state.looksLikeChallenge)
    }

    @Test
    fun detectsVerificationTextInPage() {
        val state = PageState(
            href = "https://www.douyin.com/video/1234567890",
            hasRoot = true,
            bodyLength = 80_000,
            text = "请完成安全验证后继续访问",
        )

        assertTrue(state.looksLikeChallenge)
    }

    @Test
    fun normalLoadedWorkPageIsNotAChallenge() {
        val state = PageState(
            href = "https://www.douyin.com/video/1234567890",
            title = "抖音作品",
            readyState = "complete",
            hasRoot = true,
            bodyLength = 400_000,
            text = "这是一个普通的作品描述，包含作者和点赞信息。",
            detailIds = listOf("1234567890"),
        )

        assertFalse(state.looksLikeChallenge)
    }
}
