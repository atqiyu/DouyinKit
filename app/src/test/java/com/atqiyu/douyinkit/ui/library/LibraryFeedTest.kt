package com.atqiyu.douyinkit.ui.library

import com.atqiyu.douyinkit.core.model.Author
import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.data.db.AwemeEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFeedTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 26)
    private val yesterday: LocalDate = today.minusDays(1)

    private fun millis(date: LocalDate, hour: Int = 12): Long {
        return date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun entry(
        id: String,
        title: String,
        author: String,
        kind: AwemeKind = AwemeKind.VIDEO,
        tags: String = "",
        favorite: Boolean = false,
        sortDate: LocalDate = today,
    ): LibraryEntry {
        val parsedAt = millis(sortDate)
        val aweme = Aweme(
            awemeId = id,
            kind = kind,
            desc = title,
            author = Author(nickname = author),
        )
        val entity = AwemeEntity(
            awemeId = id,
            title = title,
            authorName = author,
            desc = title,
            kind = kind.name,
            coverUrl = null,
            sourceUrl = null,
            snapshotJson = "{}",
            isFavorite = favorite,
            tags = tags,
            parsedAt = parsedAt,
            createTimeSeconds = null,
        )
        return LibraryEntry(
            entity = entity,
            aweme = aweme,
            tags = tags.split(',').filter { it.isNotBlank() },
            sortMillis = parsedAt,
            downloadedAt = null,
        )
    }

    @Test
    fun filtersByQueryTagKindAndFavorite() {
        val entries = listOf(
            entry("1", "Apple", "Alice", tags = "cosplay"),
            entry("2", "Banana", "Bob", kind = AwemeKind.IMAGES, favorite = true),
        )

        assertEquals(
            listOf("1"),
            filterLibraryEntries(entries, "alice", LibraryFilter.ALL, null)
                .map { it.entity.awemeId },
        )
        assertEquals(
            listOf("1"),
            filterLibraryEntries(entries, "", LibraryFilter.ALL, "cosplay")
                .map { it.entity.awemeId },
        )
        assertEquals(
            listOf("2"),
            filterLibraryEntries(entries, "", LibraryFilter.IMAGES, null)
                .map { it.entity.awemeId },
        )
        assertEquals(
            listOf("2"),
            filterLibraryEntries(entries, "", LibraryFilter.FAVORITE, null)
                .map { it.entity.awemeId },
        )
    }

    @Test
    fun recentFeedAddsDateSectionsAndOrdersNewestFirst() {
        val entries = listOf(
            entry("1", "Apple", "Alice", sortDate = yesterday),
            entry("2", "Banana", "Bob", sortDate = today),
        )

        val feed = buildLibraryFeed(entries, LibrarySortMode.RECENT, today)

        assertEquals(listOf("2", "1"), feed.filterIsInstance<LibraryFeedItem.Entry>()
            .map { it.item.entity.awemeId })
        assertEquals(
            listOf("今天", "昨天"),
            feed.filterIsInstance<LibraryFeedItem.Header>().map { it.title },
        )
    }

    @Test
    fun titleFeedUsesInitialSections() {
        val entries = listOf(
            entry("1", "banana", "Zoe"),
            entry("2", "Apple", "alice"),
            entry("3", "西瓜", "小明"),
        )

        val feed = buildLibraryFeed(entries, LibrarySortMode.TITLE, today)

        assertEquals(
            listOf("A", "B", "西"),
            feed.filterIsInstance<LibraryFeedItem.Header>().map { it.title },
        )
        assertEquals(
            listOf("2", "1", "3"),
            feed.filterIsInstance<LibraryFeedItem.Entry>().map { it.item.entity.awemeId },
        )
    }
}
