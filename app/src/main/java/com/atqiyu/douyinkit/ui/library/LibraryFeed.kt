package com.atqiyu.douyinkit.ui.library

import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.data.db.AwemeEntity
import com.atqiyu.douyinkit.ui.components.feedDateSectionTitle
import com.atqiyu.douyinkit.ui.components.feedInitialOf
import com.atqiyu.douyinkit.ui.components.feedLocalDate
import java.time.LocalDate

enum class LibraryFilter(val label: String) {
    ALL("全部"),
    VIDEO("视频"),
    IMAGES("图文"),
    FAVORITE("收藏"),
}

enum class LibrarySortMode(val label: String, val caption: String) {
    RECENT("最近", "按时间"),
    OLDEST("最早", "按时间"),
    TITLE("标题", "按标题"),
    AUTHOR("作者", "按作者"),
}

data class LibraryEntry(
    val entity: AwemeEntity,
    val aweme: Aweme,
    val tags: List<String>,
    val sortMillis: Long,
    val downloadedAt: Long?,
)

sealed interface LibraryFeedItem {
    val key: String

    data class Header(
        override val key: String,
        val title: String,
        val subtitle: String,
        val count: Int,
    ) : LibraryFeedItem

    data class Entry(val item: LibraryEntry) : LibraryFeedItem {
        override val key: String get() = "library:${item.entity.awemeId}"
    }
}

fun filterLibraryEntries(
    entries: List<LibraryEntry>,
    query: String,
    filter: LibraryFilter,
    selectedTag: String?,
): List<LibraryEntry> {
    val needle = query.trim()
    return entries.filter { entry ->
        val matchesQuery = needle.isEmpty() ||
            entry.aweme.displayTitle.contains(needle, ignoreCase = true) ||
            entry.aweme.author.displayName.contains(needle, ignoreCase = true) ||
            entry.aweme.desc.contains(needle, ignoreCase = true) ||
            entry.entity.tags.contains(needle, ignoreCase = true)
        val matchesFilter = when (filter) {
            LibraryFilter.ALL -> true
            LibraryFilter.VIDEO -> entry.aweme.kind == AwemeKind.VIDEO
            LibraryFilter.IMAGES -> entry.aweme.kind == AwemeKind.IMAGES
            LibraryFilter.FAVORITE -> entry.entity.isFavorite
        }
        val matchesTag = selectedTag == null || selectedTag in entry.tags
        matchesQuery && matchesFilter && matchesTag
    }
}

fun buildLibraryFeed(
    entries: List<LibraryEntry>,
    sortMode: LibrarySortMode,
    today: LocalDate = LocalDate.now(),
): List<LibraryFeedItem> {
    val sorted = when (sortMode) {
        LibrarySortMode.RECENT -> entries.sortedByDescending { it.sortMillis }
        LibrarySortMode.OLDEST -> entries.sortedBy { it.sortMillis }
        LibrarySortMode.TITLE -> entries.sortedBy { it.aweme.displayTitle.lowercase() }
        LibrarySortMode.AUTHOR -> entries.sortedBy { it.aweme.author.displayName.lowercase() }
    }

    val items = mutableListOf<LibraryFeedItem>()
    fun addSection(title: String, subtitle: String, bucket: List<LibraryEntry>) {
        if (bucket.isEmpty()) return
        items += LibraryFeedItem.Header(
            key = "header:${sortMode.name}:$title",
            title = title,
            subtitle = subtitle,
            count = bucket.size,
        )
        bucket.forEach { items += LibraryFeedItem.Entry(it) }
    }

    when (sortMode) {
        LibrarySortMode.RECENT, LibrarySortMode.OLDEST -> {
            val buckets = linkedMapOf<LocalDate, MutableList<LibraryEntry>>()
            sorted.forEach { entry ->
                buckets.getOrPut(feedLocalDate(entry.sortMillis)) { mutableListOf() }.add(entry)
            }
            buckets.forEach { (date, bucket) ->
                addSection(
                    title = feedDateSectionTitle(date, today),
                    subtitle = "解析/下载日期",
                    bucket = bucket,
                )
            }
        }

        LibrarySortMode.TITLE, LibrarySortMode.AUTHOR -> {
            val buckets = linkedMapOf<String, MutableList<LibraryEntry>>()
            sorted.forEach { entry ->
                val value = if (sortMode == LibrarySortMode.TITLE) {
                    entry.aweme.displayTitle
                } else {
                    entry.aweme.author.displayName
                }
                buckets.getOrPut(feedInitialOf(value)) { mutableListOf() }.add(entry)
            }
            buckets.forEach { (initial, bucket) ->
                addSection(
                    title = initial,
                    subtitle = if (sortMode == LibrarySortMode.TITLE) "作品标题" else "作者昵称",
                    bucket = bucket,
                )
            }
        }
    }
    return items
}
