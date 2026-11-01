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
        assertFalse(state.loginRequired)
    }

    @Test
    fun loginWallIsNotAutoExpandedAsSecurityVerification() {
        val state = PageState(
            href = "https://www.douyin.com/video/1234567890",
            title = "抖音",
            readyState = "complete",
            hasRoot = true,
            bodyLength = 120_000,
            text = "登录后免费畅享高清视频\n扫码登录\n验证码登录\n密码登录",
        )

        assertTrue(state.loginRequired)
        assertFalse(state.looksLikeChallenge)
    }

    @Test
    fun loginNavigationIsNotSecurityVerification() {
        val state = PageState(
            href = "https://www.douyin.com/passport/login?next=/video/123",
            readyState = "complete",
            hasRoot = true,
            bodyLength = 40_000,
        )

        assertTrue(state.loginRequired)
        assertFalse(state.looksLikeChallenge)
    }

    @Test
    fun explicitChallengeWinsWhenLoginAndCaptchaAppearTogether() {
        val state = PageState(
            href = "https://www.douyin.com/passport/login",
            hasRoot = true,
            bodyLength = 60_000,
            text = "验证码登录",
            challenge = true,
            challengeReason = "dom:selector",
        )

        assertTrue(state.loginRequired)
        assertTrue(state.looksLikeChallenge)
    }
}
