package com.atqiyu.douyinkit.floating

import com.atqiyu.douyinkit.data.prefs.FloatingMode

enum class DouyinTarget { MAIN, LITE }

enum class ForegroundTarget { DOUYIN, DOUYIN_LITE, OTHER, UNKNOWN }

val ForegroundTarget.isDouyin: Boolean
    get() = this == ForegroundTarget.DOUYIN || this == ForegroundTarget.DOUYIN_LITE

val ForegroundTarget.label: String
    get() = when (this) {
        ForegroundTarget.DOUYIN -> "抖音前台"
        ForegroundTarget.DOUYIN_LITE -> "抖音极速版前台"
        ForegroundTarget.OTHER -> "其他应用"
        ForegroundTarget.UNKNOWN -> "状态未知"
    }

enum class FloatingPhase { IDLE, WAITING, PARSING, VERIFYING, DOWNLOADING, DONE, ERROR }

data class FloatingUiState(
    val enabled: Boolean = false,
    val mode: FloatingMode = FloatingMode.STANDARD,
    val autoDownload: Boolean = true,
    val accessibilityReady: Boolean = false,
    val overlayReady: Boolean = false,
    val foreground: ForegroundTarget = ForegroundTarget.UNKNOWN,
    val clipboardLink: String? = null,
    val promptVisible: Boolean = false,
    val expanded: Boolean = false,
    val phase: FloatingPhase = FloatingPhase.IDLE,
    val title: String? = null,
    val message: String = "待命",
    val detail: String? = null,
    val queueSize: Int = 0,
    val progress: Float? = null,
    val resultReady: Boolean = false,
    val automationBusy: Boolean = false,
    val needsVerification: Boolean = false,
    val needsLogin: Boolean = false,
    val appForeground: Boolean = false,
)

object DouyinPackages {
    const val MAIN = "com.ss.android.ugc.aweme"
    const val LITE = "com.ss.android.ugc.aweme.lite"

    fun of(packageName: String?): DouyinTarget? = when (packageName) {
        MAIN -> DouyinTarget.MAIN
        LITE -> DouyinTarget.LITE
        else -> null
    }

    fun targetOf(packageName: String?): ForegroundTarget = when (packageName) {
        MAIN -> ForegroundTarget.DOUYIN
        LITE -> ForegroundTarget.DOUYIN_LITE
        else -> ForegroundTarget.OTHER
    }

    fun isSystemUi(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return true
        return packageName == "com.android.systemui" ||
            packageName.startsWith("com.oplus.") ||
            packageName.startsWith("com.coloros.") ||
            packageName.startsWith("com.android.permissioncontroller")
    }
}
