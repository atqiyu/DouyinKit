package com.atqiyu.douyinkit.parser

import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.ErrorCode

enum class ParseStage {
    IDLE,
    RESOLVING,
    OPENING,
    WAITING_DETAIL,
    VERIFYING,
    WAITING_NOTE,
    ENRICHING,
    DONE,
    FAILED,
    CANCELLED,
    ;

    val label: String
        get() = when (this) {
            IDLE -> "准备解析"
            RESOLVING -> "正在解析链接"
            OPENING -> "正在打开作品页"
            WAITING_DETAIL -> "正在获取作品数据"
            VERIFYING -> "需要完成安全验证"
            WAITING_NOTE -> "正在读取图文数据"
            ENRICHING -> "正在整理媒体信息"
            DONE -> "解析完成"
            FAILED -> "解析失败"
            CANCELLED -> "已取消"
        }
}

data class ParseFailure(
    val input: String,
    val message: String,
    val code: ErrorCode? = null,
)

data class ParseUiState(
    val running: Boolean = false,
    val stage: ParseStage = ParseStage.IDLE,
    val currentIndex: Int = 0,
    val total: Int = 0,
    val elapsedMs: Long = 0,
    val pageProgress: Int = 0,
    val showWebView: Boolean = false,
    val needsVerification: Boolean = false,
    val message: String = "",
    val results: List<Aweme> = emptyList(),
    val failures: List<ParseFailure> = emptyList(),
    val pageState: PageState? = null,
) {
    val currentNumber: Int get() = if (total == 0) 0 else (currentIndex + 1).coerceAtMost(total)
    val hasAnySuccess: Boolean get() = results.isNotEmpty()
}

data class ParseBatchResult(
    val results: List<Aweme>,
    val failures: List<ParseFailure>,
    val startedAtMillis: Long = System.currentTimeMillis(),
)
