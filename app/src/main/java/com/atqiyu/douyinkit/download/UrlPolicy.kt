package com.atqiyu.douyinkit.download

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Restricts media downloads to known ByteDance/Douyin CDN hosts and blocks SSRF targets. */
object UrlPolicy {
    private val allowedSuffixes = listOf(
        "douyinvod.com",
        "douyinpic.com",
        "douyinstatic.com",
        "snssdk.com",
        "byteimg.com",
        "ibytedtos.com",
        "bytecdn.cn",
        "douyin.com",
        "iesdouyin.com",
    )
    private val ipv4 = Regex("""^(?:\d{1,3}\.){3}\d{1,3}$""")

    fun isAllowed(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        if (parsed.scheme != "https" && parsed.scheme != "http") return false
        val host = parsed.host.lowercase()
        if (host == "localhost" || host == "127.0.0.1" || host == "0.0.0.0") return false
        if (ipv4.matches(host) || host.startsWith("[") || host.contains(":")) return false
        return allowedSuffixes.any { suffix ->
            host == suffix || host.endsWith(".$suffix")
        }
    }

    fun httpsUrlOrNull(url: String): String? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "https" && parsed.scheme != "http") return null
        if (!isAllowed(url)) return null
        return parsed.newBuilder().scheme("https").build().toString()
    }
}
