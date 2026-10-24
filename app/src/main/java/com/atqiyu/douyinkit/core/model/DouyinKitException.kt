package com.atqiyu.douyinkit.core.model

/** Low-level error codes shared by link resolution, WebView parsing, downloading and saving. */
enum class ErrorCode {
    NO_LINK,
    INVALID_INPUT,
    UNSUPPORTED_HOST,
    SHORT_LINK_FAILED,
    SHORT_LINK_TIMEOUT,
    SHORT_LINK_UNRESOLVED,
    WEBVIEW_UNAVAILABLE,
    PAGE_LOAD_FAILED,
    DETAIL_TIMEOUT,
    DETAIL_EMPTY,
    AWEME_ID_MISMATCH,
    NO_MEDIA,
    NEEDS_VERIFICATION,
    NETWORK,
    STORAGE,
    PERMISSION,
    CANCELLED,
    UNKNOWN,
}

class DouyinKitException(
    val code: ErrorCode,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
