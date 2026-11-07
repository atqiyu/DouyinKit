package com.atqiyu.douyinkit.data

import com.atqiyu.douyinkit.core.util.LinkExtractor
import com.atqiyu.douyinkit.data.db.ShareLinkDao
import com.atqiyu.douyinkit.data.db.ShareLinkRecordEntity
import kotlinx.coroutines.flow.Flow

enum class ShareLinkStatus {
    RECORDED,
    PROCESSING,
    COMPLETED,
    FAILED,
    ;

    companion object {
        fun of(raw: String?): ShareLinkStatus =
            runCatching { valueOf(raw.orEmpty()) }.getOrDefault(RECORDED)
    }
}

/**
 * Stores links captured by 连播 mode. This is intentionally separate from parse history:
 * "连播不解析不下载" records the raw share URL without opening a WebView, and the user can
 * later batch-process unfinished rows from the Library.
 */
class ShareLinkRepository(private val dao: ShareLinkDao) {
    fun observeAll(): Flow<List<ShareLinkRecordEntity>> = dao.observeAll()

    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    suspend fun getPending(): List<ShareLinkRecordEntity> = dao.getPending()

    /** Returns the normalized URL, or null when [raw] contains no Douyin link. */
    suspend fun record(raw: String, now: Long = System.currentTimeMillis()): String? {
        val link = LinkExtractor.extract(raw).firstOrNull()?.url ?: return null
        val existing = dao.get(link)
        val status = when (ShareLinkStatus.of(existing?.status)) {
            ShareLinkStatus.COMPLETED, ShareLinkStatus.PROCESSING -> existing?.status
            else -> ShareLinkStatus.RECORDED.name
        }
        // Clipboard-change events can fire several times for one copy action. Do not inflate
        // the capture counter when the same row is touched again within a short window.
        val repeatedWithin = existing != null && now - existing.lastCapturedAt < 10_000L
        dao.upsert(
            ShareLinkRecordEntity(
                url = link,
                awemeId = existing?.awemeId,
                title = existing?.title,
                status = status ?: ShareLinkStatus.RECORDED.name,
                message = if (status == ShareLinkStatus.RECORDED.name) null else existing?.message,
                firstCapturedAt = existing?.firstCapturedAt ?: now,
                lastCapturedAt = if (repeatedWithin) existing.lastCapturedAt else now,
                captureCount = existing?.captureCount?.plus(if (repeatedWithin) 0 else 1) ?: 1,
                updatedAt = now,
                completedAt = existing?.completedAt,
            ),
        )
        return link
    }

    suspend fun markProcessing(url: String) {
        dao.markProcessing(url, System.currentTimeMillis())
    }

    suspend fun markCompleted(
        url: String,
        awemeId: String? = null,
        title: String? = null,
        message: String? = "已完成",
    ) {
        dao.markCompleted(
            url = url,
            awemeId = awemeId,
            title = title,
            message = message,
            now = System.currentTimeMillis(),
        )
    }

    suspend fun markFailed(url: String, message: String?) {
        dao.markFailed(url, message, System.currentTimeMillis())
    }

    suspend fun markInterrupted() {
        dao.markInterrupted(System.currentTimeMillis())
    }

    suspend fun delete(url: String) {
        dao.delete(url)
    }

    suspend fun clearCompleted() {
        dao.clearCompleted()
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}