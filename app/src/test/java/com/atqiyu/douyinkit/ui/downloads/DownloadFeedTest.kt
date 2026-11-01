package com.atqiyu.douyinkit.ui.downloads

import com.atqiyu.douyinkit.core.model.Author
import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.data.prefs.DownloadSortMode
import com.atqiyu.douyinkit.download.DownloadKind
import com.atqiyu.douyinkit.download.DownloadRequest
import com.atqiyu.douyinkit.download.DownloadStatus
import com.atqiyu.douyinkit.download.DownloadTask
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFeedTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 26)
    private val yesterday: LocalDate = today.minusDays(1)

    private fun millis(date: LocalDate, hour: Int = 12): Long {
        return date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun group(
        id: String,
        title: String,
        author: String,
        createdAt: Long,
    ): DownloadGroup {
        val aweme = Aweme(
            awemeId = id,
            kind = AwemeKind.VIDEO,
            desc = title,
            author = Author(nickname = author),
        )
        val task = DownloadTask(
            id = "task_$id",
            request = DownloadRequest(aweme = aweme, kind = DownloadKind.VIDEO),
            status = DownloadStatus.COMPLETED,
            createdAt = createdAt,
        )
        return buildDownloadGroups(listOf(task)).single()
    }

    @Test
    fun sortsNewestFirstAndAddsDateSections() {
        val groups = listOf(
            group("1", "Apple", "Alice", millis(today)),
            group("2", "Banana", "Bob", millis(yesterday)),
            group("3", "Cherry", "Carol", millis(today, 8)),
        )

        val feed = buildDownloadFeed(
            groups = groups,
            sortMode = DownloadSortMode.LATEST,
            collectionFilter = null,
            membershipByAweme = emptyMap(),
            collectionNames = emptyMap(),
            tagsByAweme = emptyMap(),
            query = "",
            dateFilter = null,
            today = today,
        )

        assertEquals(listOf("1", "3", "2"), feed.groups.map { it.awemeId })
        val headers = feed.items.filterIsInstance<DownloadFeedItem.Header>()
        assertEquals(listOf("今天", "昨天"), headers.map { it.title })
        assertTrue(feed.items[0] is DownloadFeedItem.Header)
        assertEquals(2, headers.first().count)
    }

    @Test
    fun filtersByDateSearchAuthorTagsAndCollection() {
        val groups = listOf(
            group("1", "Apple", "Alice", millis(today)),
            group("2", "Banana", "Bob", millis(yesterday)),
        )
        val membership = mapOf("1" to "c1")
        val collectionNames = mapOf("c1" to "旅行")
        val tags = mapOf("1" to listOf("cosplay"))

        fun feed(query: String = "", date: LocalDate? = null, collection: String? = null) =
            buildDownloadFeed(
                groups = groups,
                sortMode = DownloadSortMode.LATEST,
                collectionFilter = collection,
                membershipByAweme = membership,
                collectionNames = collectionNames,
                tagsByAweme = tags,
                query = query,
                dateFilter = date,
                today = today,
            )

        assertEquals(listOf("1"), feed(date = today).groups.map { it.awemeId })
        assertEquals(listOf("1"), feed(query = "Alice").groups.map { it.awemeId })
        assertEquals(listOf("1"), feed(query = "cosplay").groups.map { it.awemeId })
        assertEquals(listOf("1"), feed(query = "旅行").groups.map { it.awemeId })
        assertEquals(listOf("1"), feed(collection = "c1").groups.map { it.awemeId })
        assertEquals(
            listOf("2"),
            buildDownloadFeed(
                groups = groups,
                sortMode = DownloadSortMode.LATEST,
                collectionFilter = FAVORITES_COLLECTION,
                membershipByAweme = membership,
                collectionNames = collectionNames,
                tagsByAweme = tags,
                query = "",
                dateFilter = null,
                today = today,
                favoriteIds = setOf("2"),
            ).groups.map { it.awemeId },
        )
    }

    @Test
    fun titleSortUsesInitialSections() {
        val groups = listOf(
            group("1", "banana", "Zoe", millis(today)),
            group("2", "Apple", "alice", millis(yesterday)),
            group("3", "西瓜", "小明", millis(today)),
        )

        val feed = buildDownloadFeed(
            groups = groups,
            sortMode = DownloadSortMode.TITLE,
            collectionFilter = null,
            membershipByAweme = emptyMap(),
            collectionNames = emptyMap(),
            tagsByAweme = emptyMap(),
            query = "",
            dateFilter = null,
            today = today,
        )

        assertEquals(
            listOf("A", "B", "西"),
            feed.items.filterIsInstance<DownloadFeedItem.Header>().map { it.title },
        )
        assertEquals(listOf("2", "1", "3"), feed.groups.map { it.awemeId })
    }
}
