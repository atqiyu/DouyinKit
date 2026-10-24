package com.atqiyu.douyinkit.parser

import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.core.model.DouyinKitException
import com.atqiyu.douyinkit.core.model.ErrorCode
import com.atqiyu.douyinkit.core.net.DouyinUserAgent
import com.atqiyu.douyinkit.core.util.LinkExtractor
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class ResolvedLink(
    val awemeId: String,
    val kindHint: AwemeKind?,
    val sourceUrl: String,
    val canonicalUrl: String,
)

class ShortLinkResolver(
    private val client: OkHttpClient,
    private val userAgent: String = DouyinUserAgent.MOBILE_FALLBACK,
) {
    suspend fun resolve(input: String): ResolvedLink = withContext(Dispatchers.IO) {
        val extracted = LinkExtractor.extractFirst(input)
            ?: throw DouyinKitException(ErrorCode.NO_LINK, "没有找到抖音作品链接")

        val directId = extracted.awemeId
        if (directId != null) {
            return@withContext ResolvedLink(
                awemeId = directId,
                kindHint = extracted.kindHint,
                sourceUrl = extracted.url,
                canonicalUrl = LinkExtractor.canonicalUrl(directId, extracted.kindHint),
            )
        }

        val requestUrl = try {
            extracted.url.toHttpUrl()
        } catch (error: Exception) {
            throw DouyinKitException(ErrorCode.INVALID_INPUT, "链接格式不正确", error)
        }

        if (!LinkExtractor.isDouyinHost(requestUrl.host)) {
            throw DouyinKitException(ErrorCode.UNSUPPORTED_HOST, "不是抖音链接")
        }

        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (error: SocketTimeoutException) {
            throw DouyinKitException(ErrorCode.SHORT_LINK_TIMEOUT, "短链解析超时", error)
        } catch (error: InterruptedIOException) {
            throw DouyinKitException(ErrorCode.SHORT_LINK_TIMEOUT, "短链解析超时", error)
        } catch (error: IOException) {
            throw DouyinKitException(ErrorCode.SHORT_LINK_FAILED, "短链请求失败：${error.message}", error)
        }

        response.use { resp ->
            val location = resp.header("Location")
                ?: resp.header("location")
                ?: ""
            val resolvedUrl = if (location.isNotBlank()) {
                request.url.resolve(location)?.toString().orEmpty()
            } else {
                ""
            }

            val locationId = if (resolvedUrl.isNotBlank()) {
                LinkExtractor.awemeIdFromUrl(resolvedUrl)
            } else {
                null
            }
            val locationKind = if (resolvedUrl.isNotBlank()) {
                LinkExtractor.kindFromUrl(resolvedUrl)
            } else {
                null
            }

            if (locationId == null) {
                throw DouyinKitException(
                    ErrorCode.SHORT_LINK_UNRESOLVED,
                    "短链没有跳转到作品页（HTTP ${resp.code}）",
                )
            }
            if (!LinkExtractor.isDouyinHost(resolvedUrl.toHttpUrl().host)) {
                throw DouyinKitException(ErrorCode.UNSUPPORTED_HOST, "短链跳转到了非抖音域名")
            }

            ResolvedLink(
                awemeId = locationId,
                kindHint = locationKind ?: extracted.kindHint,
                sourceUrl = extracted.url,
                canonicalUrl = LinkExtractor.canonicalUrl(locationId, locationKind ?: extracted.kindHint),
            )
        }
    }
}
