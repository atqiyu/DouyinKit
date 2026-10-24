package com.atqiyu.douyinkit.core.util

import com.atqiyu.douyinkit.core.model.AwemeKind
import java.net.URI

data class ExtractedLink(
    val raw: String,
    val url: String,
    val awemeId: String?,
    val kindHint: AwemeKind?,
)

/**
 * Extracts Douyin work links from arbitrary share text. Short codes are case-sensitive and must
 * not be lower-cased.
 */
object LinkExtractor {
    private val douyinUrlRegex = Regex(
        pattern = """(?i)(?<![a-z0-9.-])(?:https?://)?(?:[a-z0-9-]+\.)*(?:douyin\.com|iesdouyin\.com)/[A-Za-z0-9\-._~:/?#\[\]@!$&()*+,;=%]*""",
    )
    private val pathIdRegex = Regex(
        pattern = """/(?:video|note|slides|share/(?:video|note|slides))/(\d{8,25})""",
        option = RegexOption.IGNORE_CASE,
    )
    private val bareIdRegex = Regex("""^\d{8,25}$""")

    private val trailingPunctuation = charArrayOf(
        '，', '。', '、', '；', '！', '？', '）', '】', '》', '」', '』', '“', '”', '’', '…',
        ',', ';', '!', '?', ')', ']', '}', '>', '"', '\'',
    )

    fun extract(text: String, limit: Int = 20): List<ExtractedLink> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (bareIdRegex.matches(trimmed)) {
            return listOf(
                ExtractedLink(
                    raw = trimmed,
                    url = canonicalUrl(trimmed, null),
                    awemeId = trimmed,
                    kindHint = null,
                ),
            )
        }

        val results = mutableListOf<ExtractedLink>()
        val seenUrls = mutableSetOf<String>()
        val seenIds = mutableSetOf<String>()

        for (match in douyinUrlRegex.findAll(trimmed)) {
            val normalized = normalizeUrl(match.value)
            val parsed = parseHttpUrl(normalized) ?: continue
            val host = parsed.host?.lowercase()?.trimEnd('.') ?: continue
            if (!isDouyinHost(host)) continue

            val id = pathIdRegex.find(parsed.path.orEmpty())?.groupValues?.getOrNull(1)
            val kind = kindFromPath(parsed.path.orEmpty())

            if (!seenUrls.add(normalized)) continue
            if (id != null && !seenIds.add(id)) continue

            results += ExtractedLink(
                raw = match.value.trimEnd(*trailingPunctuation),
                url = normalized,
                awemeId = id,
                kindHint = kind,
            )
            if (results.size >= limit) break
        }

        return results
    }

    fun extractFirst(text: String): ExtractedLink? = extract(text, limit = 1).firstOrNull()

    fun awemeIdFromUrl(url: String): String? {
        val parsed = parseHttpUrl(url) ?: return null
        return pathIdRegex.find(parsed.path.orEmpty())?.groupValues?.getOrNull(1)
    }

    fun kindFromUrl(url: String): AwemeKind? {
        val parsed = parseHttpUrl(url) ?: return null
        return kindFromPath(parsed.path.orEmpty())
    }

    fun isDouyinHost(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        return h == "douyin.com" ||
            h.endsWith(".douyin.com") ||
            h == "iesdouyin.com" ||
            h.endsWith(".iesdouyin.com")
    }

    fun canonicalUrl(awemeId: String, kindHint: AwemeKind?): String {
        val path = if (kindHint == AwemeKind.IMAGES) "note" else "video"
        return "https://www.douyin.com/$path/$awemeId"
    }

    private fun kindFromPath(path: String): AwemeKind? {
        val lower = path.lowercase()
        return when {
            lower.contains("/note/") ||
                lower.contains("/slides/") ||
                lower.contains("/share/note/") ||
                lower.contains("/share/slides/") -> AwemeKind.IMAGES

            lower.contains("/video/") ||
                lower.contains("/share/video/") -> AwemeKind.VIDEO

            else -> null
        }
    }

    private fun normalizeUrl(raw: String): String {
        var value = raw.replace("&amp;", "&").trimEnd(*trailingPunctuation)
        value = value.trimEnd('.')
        value = if (value.startsWith("http://", ignoreCase = true) &&
            !value.startsWith("https://", ignoreCase = true)
        ) {
            "https://" + value.substring(7)
        } else {
            value
        }
        if (!value.startsWith("http://", ignoreCase = true) &&
            !value.startsWith("https://", ignoreCase = true)
        ) {
            value = "https://$value"
        }
        return value
    }

    private fun parseHttpUrl(url: String): URI? {
        return try {
            val uri = URI(url)
            if (!uri.scheme.equals("http", true) && !uri.scheme.equals("https", true)) null else uri
        } catch (_: Exception) {
            null
        }
    }
}
