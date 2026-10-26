package com.atqiyu.douyinkit.ui.downloads

import com.atqiyu.douyinkit.core.model.Author
import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.data.prefs.DownloadSortMode
import com.atqiyu.douyinkit.download.DownloadKind
import com.atqiyu.douyinkit.download.DownloadRequest
import com.atqiyu.douyinkit.download.DownloadStatus
import com.atqiyu.douyinkit.download.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadSortingTest {

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
    fun sortsByNewestAndOldestDownload() {
        val groups = listOf(
            group("1", "B", "Bob", 200),
            group("2", "A", "Alice", 100),
            group("3", "C", "Carol", 300),
        )

        assertEquals(
            listOf("3", "1", "2"),
            sortDownloadGroups(groups, DownloadSortMode.LATEST).map { it.awemeId },
        )
        assertEquals(
            listOf("2", "1", "3"),
            sortDownloadGroups(groups, DownloadSortMode.OLDEST).map { it.awemeId },
        )
    }

    @Test
    fun sortsByTitleAndAuthorIgnoringCase() {
        val groups = listOf(
            group("1", "banana", "Zoe", 100),
            group("2", "Apple", "alice", 200),
            group("3", "cherry", "Bob", 300),
        )

        assertEquals(
            listOf("2", "1", "3"),
            sortDownloadGroups(groups, DownloadSortMode.TITLE).map { it.awemeId },
        )
        assertEquals(
            listOf("2", "3", "1"),
            sortDownloadGroups(groups, DownloadSortMode.AUTHOR).map { it.awemeId },
        )
    }

    @Test
    fun filtersCollectionsAndUncategorizedWorks() {
        val groups = listOf(
            group("1", "A", "Alice", 100),
            group("2", "B", "Bob", 200),
            group("3", "C", "Carol", 300),
        )
        val membership = mapOf("1" to "travel", "2" to "food")

        assertEquals(
            listOf("1"),
            filterDownloadGroups(groups, "travel", membership).map { it.awemeId },
        )
        assertEquals(
            listOf("3"),
            filterDownloadGroups(
                groups,
                UNCATEGORIZED_COLLECTION,
                membership,
            ).map { it.awemeId },
        )
        assertEquals(
            listOf("1", "2", "3"),
            filterDownloadGroups(groups, null, membership).map { it.awemeId },
        )
    }
}
