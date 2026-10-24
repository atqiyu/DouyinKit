package com.atqiyu.douyinkit.download

import com.atqiyu.douyinkit.core.net.DouyinUserAgent
import java.io.File
import java.io.FileOutputStream
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class FileDownloader(
    private val client: OkHttpClient,
    private val userAgent: String = DouyinUserAgent.DESKTOP_FALLBACK,
    private val urlPolicy: (String) -> Boolean = UrlPolicy::isAllowed,
    private val urlNormalizer: (String) -> String? = UrlPolicy::httpsUrlOrNull,
) {
    suspend fun download(
        candidates: List<String>,
        destination: File,
        expectedSize: Long?,
        kind: MediaKind,
        onProgress: (bytes: Long, total: Long?) -> Unit = { _, _ -> },
    ): DownloadedMedia = withContext(Dispatchers.IO) {
        val urls = candidates.mapNotNull { urlNormalizer(it) }.distinct()
        if (urls.isEmpty()) {
            throw DownloadException(DownloadFailure.NO_SOURCE, "没有可用的下载地址")
        }

        val errors = mutableListOf<String>()
        val failures = mutableListOf<DownloadException>()
        for (url in urls) {
            try {
                return@withContext downloadSingle(
                    url = url,
                    destination = destination,
                    expectedSize = expectedSize,
                    kind = kind,
                    onProgress = onProgress,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errors += "${url.substringBefore('?')}: ${error.message ?: error.javaClass.simpleName}"
                if (error is DownloadException) {
                    failures += error
                    if (error.failure == DownloadFailure.NOT_MEDIA) {
                        destination.delete()
                    }
                }
            }
        }

        val failureCode = when {
            failures.isNotEmpty() && failures.all { it.failure == DownloadFailure.EXPIRED } ->
                DownloadFailure.EXPIRED

            failures.any { it.failure == DownloadFailure.NOT_MEDIA } ->
                DownloadFailure.NOT_MEDIA

            failures.any { it.failure == DownloadFailure.TOO_LARGE } ->
                DownloadFailure.TOO_LARGE

            else -> DownloadFailure.HTTP
        }
        throw DownloadException(
            failure = failureCode,
            message = if (failureCode == DownloadFailure.EXPIRED) {
                "下载地址已过期"
            } else {
                "所有下载地址都失败了"
            },
            attempts = errors,
        )
    }

    private suspend fun downloadSingle(
        url: String,
        destination: File,
        expectedSize: Long?,
        kind: MediaKind,
        onProgress: (Long, Long?) -> Unit,
    ): DownloadedMedia {
        destination.parentFile?.mkdirs()
        var existing = if (destination.exists()) destination.length() else 0L

        if (existing > 0 && expectedSize != null && existing == expectedSize) {
            validate(destination, kind, expectedSize)?.let { return it }
        }
        if (existing > 0 && expectedSize != null && existing > expectedSize) {
            destination.delete()
            existing = 0
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", userAgent)
            .header("Referer", DouyinUserAgent.REFERER)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("Accept", acceptFor(kind))
        if (existing > 0) {
            requestBuilder.header("Range", "bytes=$existing-")
        }

        val request = requestBuilder.build()
        val response = try {
            client.newCall(request).execute()
        } catch (error: SocketTimeoutException) {
            throw DownloadException(DownloadFailure.NETWORK, "下载超时", cause = error)
        } catch (error: Exception) {
            throw DownloadException(DownloadFailure.NETWORK, "下载失败：${error.message}", cause = error)
        }

        return response.use { resp ->
            if (!urlPolicy(resp.request.url.toString())) {
                throw DownloadException(DownloadFailure.HTTP, "下载被重定向到了未知域名")
            }
            if (resp.code == 416) {
                if (expectedSize != null && existing == expectedSize) {
                    validate(destination, kind, expectedSize)?.let { return it }
                }
                destination.delete()
                throw DownloadException(DownloadFailure.SIZE_MISMATCH, "续传位置无效")
            }
            if (!resp.isSuccessful) {
                throw DownloadException(
                    failure = if (resp.code == 403 || resp.code == 401 || resp.code == 410) {
                        DownloadFailure.EXPIRED
                    } else {
                        DownloadFailure.HTTP
                    },
                    message = "CDN 返回 HTTP ${resp.code}",
                )
            }

            val contentType = resp.header("Content-Type").orEmpty()
            if (contentType.contains("text/html", ignoreCase = true)) {
                throw DownloadException(DownloadFailure.NOT_MEDIA, "下载地址返回了网页而不是媒体文件")
            }

            val body = resp.body ?: throw DownloadException(DownloadFailure.EMPTY, "下载响应为空")
            val contentLength = body.contentLength()
            if (contentLength > MAX_MEDIA_BYTES) {
                throw DownloadException(DownloadFailure.TOO_LARGE, "文件超过 2GB 限制")
            }

            val append = resp.code == 206 && existing > 0
            val startBytes = if (append) existing else 0L
            if (!append && existing > 0) {
                existing = 0
            }

            val total = when {
                append && contentLength > 0 -> existing + contentLength
                contentLength > 0 -> contentLength
                expectedSize != null && expectedSize > 0 -> expectedSize
                else -> null
            }
            if (total != null && total > MAX_MEDIA_BYTES) {
                throw DownloadException(DownloadFailure.TOO_LARGE, "文件超过 2GB 限制")
            }

            var written = startBytes
            FileOutputStream(destination, append).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(written, total)
                    }
                    output.flush()
                }
            }

            val actual = destination.length()
            if (total != null && actual < total) {
                throw DownloadException(DownloadFailure.SIZE_MISMATCH, "下载不完整：$actual/$total")
            }
            if (expectedSize != null && expectedSize > 0) {
                val tolerance = maxOf(1024L, expectedSize / 100)
                if (kotlin.math.abs(actual - expectedSize) > tolerance) {
                    destination.delete()
                    throw DownloadException(
                        DownloadFailure.SIZE_MISMATCH,
                        "文件大小不符：$actual，预期 $expectedSize",
                    )
                }
            }

            validate(destination, kind, expectedSize)
                ?: throw DownloadException(DownloadFailure.NOT_MEDIA, "文件格式校验失败")
        }
    }

    private fun validate(file: File, kind: MediaKind, expectedSize: Long?): DownloadedMedia? {
        if (!file.exists() || file.length() <= 0) return null
        if (expectedSize != null && expectedSize > 0) {
            val tolerance = maxOf(1024L, expectedSize / 100)
            if (kotlin.math.abs(file.length() - expectedSize) > tolerance) return null
        }
        return when (kind) {
            MediaKind.VIDEO -> {
                if (!MimeSniffer.isMp4(file)) null
                else DownloadedMedia(file, "mp4", "video/mp4", file.length())
            }

            MediaKind.IMAGE -> {
                val ext = MimeSniffer.imageExtension(file) ?: return null
                DownloadedMedia(file, ext, MimeSniffer.imageMime(ext), file.length())
            }

            MediaKind.AUDIO -> {
                val ext = MimeSniffer.audioExtension(file) ?: return null
                DownloadedMedia(file, ext, MimeSniffer.audioMime(ext), file.length())
            }
        }
    }

    private fun acceptFor(kind: MediaKind): String = when (kind) {
        MediaKind.VIDEO -> "video/mp4,video/*;q=0.9,application/octet-stream;q=0.8,*/*;q=0.7"
        MediaKind.IMAGE -> "image/avif,image/webp,image/apng,image/*,*/*;q=0.8"
        MediaKind.AUDIO -> "audio/mpeg,audio/mp4,audio/*;q=0.9,*/*;q=0.8"
    }

    private companion object {
        const val COPY_BUFFER_SIZE = 64 * 1024
        const val MAX_MEDIA_BYTES = 2L * 1024 * 1024 * 1024
    }
}
