package com.atqiyu.douyinkit.core.net

object DouyinUserAgent {
    const val REFERER = "https://www.douyin.com/"
    const val ORIGIN = "https://www.douyin.com"
    const val DESKTOP_FALLBACK =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
    const val MOBILE_FALLBACK =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36"

    fun desktopFrom(defaultUa: String): String {
        val version = Regex("""Chrome/([\d.]+)""")
            .find(defaultUa)
            ?.groupValues
            ?.getOrNull(1)
            ?: return DESKTOP_FALLBACK
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$version Safari/537.36"
    }

    fun mobileFrom(defaultUa: String): String {
        val version = Regex("""Chrome/([\d.]+)""")
            .find(defaultUa)
            ?.groupValues
            ?.getOrNull(1)
            ?: return MOBILE_FALLBACK
        return "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$version Mobile Safari/537.36"
    }
}
