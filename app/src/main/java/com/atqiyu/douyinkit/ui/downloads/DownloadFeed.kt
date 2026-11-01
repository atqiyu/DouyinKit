package com.atqiyu.douyinkit.ui.downloads

import com.atqiyu.douyinkit.data.prefs.DownloadSortMode
import com.atqiyu.douyinkit.ui.components.feedDateSectionTitle
import com.atqiyu.douyinkit.ui.components.feedInitialOf
import com.atqiyu.douyinkit.ui.components.feedLocalDate
import java.time.LocalDate

sealed interface DownloadFeedItem {
    val key: String

    data class Header(
        override val key: String,
        val title: String,
        val subtitle: String,
        val count: Int,
    ) : DownloadFeedItem

    data class Work(
        val group: DownloadGroup,
        val collectionName: String?,
    ) : DownloadFeedItem {
        override val key: String get() = "work:${group.awemeId}"
    }
}

data class DownloadFeed(
    val items: List<DownloadFeedItem>,
    val groups: List<DownloadGroup>,
)

fun buildDownloadFeed(
    groups: List<DownloadGroup>,
    sortMode: DownloadSortMode,
    collectionFilter: String?,
    membershipByAweme: Map<String, String>,
    collectionNames: Map<String, String>,
    tagsByAweme: Map<String, List<String>>,
    query: String,
    dateFilter: LocalDate?,
    today: LocalDate = LocalDate.now(),
): DownloadFeed {
    val collectionFiltered = when (collectionFilter) {
        null -> groups
        UNCATEGORIZED_COLLECTION -> groups.filter { it.awemeId !in membershipByAweme }
        else -> groups.filter { membershipByAweme[it.awemeId] == collectionFilter }
    }
    val normalizedQuery = query.trim()
    val searched = if (normalizedQuery.isEmpty()) {
        collectionFiltered
    } else {
        collectionFiltered.filter { group ->
            group.matchesQuery(
                query = normalizedQuery,
                collectionName = membershipByAweme[group.awemeId]?.let(collectionNames::get),
                tags = tagsByAweme[group.awemeId].orEmpty(),
            )
        }
    }
    val dated = dateFilter?.let { targetDate ->
        searched.filter { feedLocalDate(it.latestAt) == targetDate }
    } ?: searched
    val sorted = sortDownloadGroups(dated, sortMode)

    val items = mutableListOf<DownloadFeedItem>()
    fun addSection(
        title: String,
        subtitle: String,
        sectionGroups: List<DownloadGroup>,
    ) {
        if (sectionGroups.isEmpty()) return
        items += DownloadFeedItem.Header(
            key = "header:${sortMode.name}:$title",
            title = title,
            subtitle = subtitle,
            count = sectionGroups.size,
        )
        sectionGroups.forEach { group ->
            items += DownloadFeedItem.Work(
                group = group,
                collectionName = membershipByAweme[group.awemeId]?.let(collectionNames::get),
            )
        }
    }

    when (sortMode) {
        DownloadSortMode.LATEST, DownloadSortMode.OLDEST -> {
            val buckets = linkedMapOf<LocalDate, MutableList<DownloadGroup>>()
            sorted.forEach { group ->
                buckets.getOrPut(feedLocalDate(group.latestAt)) { mutableListOf() }.add(group)
            }
            buckets.forEach { (date, bucket) ->
                addSection(
                    title = feedDateSectionTitle(date, today),
                    subtitle = "下载日期",
                    sectionGroups = bucket,
                )
            }
        }

        DownloadSortMode.TITLE, DownloadSortMode.AUTHOR -> {
            val buckets = linkedMapOf<String, MutableList<DownloadGroup>>()
            sorted.forEach { group ->
                val value = if (sortMode == DownloadSortMode.TITLE) {
                    group.title
                } else {
                    group.aweme.author.displayName
                }
                buckets.getOrPut(feedInitialOf(value)) { mutableListOf() }.add(group)
            }
            buckets.forEach { (initial, bucket) ->
                addSection(
                    title = initial,
                    subtitle = if (sortMode == DownloadSortMode.TITLE) "作品标题" else "作者昵称",
                    sectionGroups = bucket,
                )
            }
        }

        DownloadSortMode.LARGEST -> {
            val buckets = linkedMapOf<String, MutableList<DownloadGroup>>()
            sorted.forEach { group ->
                buckets.getOrPut(sizeBand(group.totalBytes)) { mutableListOf() }.add(group)
            }
            buckets.forEach { (band, bucket) ->
                addSection(
                    title = band,
                    subtitle = "文件大小",
                    sectionGroups = bucket,
                )
            }
        }

        DownloadSortMode.MOST_FILES -> {
            val buckets = linkedMapOf<String, MutableList<DownloadGroup>>()
            sorted.forEach { group ->
                buckets.getOrPut(fileCountBand(group.fileCount)) { mutableListOf() }.add(group)
            }
            buckets.forEach { (band, bucket) ->
                addSection(
                    title = band,
                    subtitle = "文件数量",
                    sectionGroups = bucket,
                )
            }
        }
    }

    return DownloadFeed(items = items, groups = sorted)
}

private fun DownloadGroup.matchesQuery(
    query: String,
    collectionName: String?,
    tags: List<String>,
): Boolean {
    val needle = query.lowercase()
    if (title.lowercase().contains(needle)) return true
    if (aweme.desc.lowercase().contains(needle)) return true
    if (aweme.author.displayName.lowercase().contains(needle)) return true
    if (aweme.awemeId.contains(needle)) return true
    if (aweme.music?.displayTitle?.lowercase()?.contains(needle) == true) {
        return true
    }
    if (!collectionName.isNullOrBlank() && collectionName.lowercase().contains(needle)) {
        return true
    }
    return tags.any { it.lowercase().contains(needle) }
}

private fun sizeBand(bytes: Long): String = when {
    bytes >= 100L * 1024 * 1024 -> "100 MB 以上"
    bytes >= 20L * 1024 * 1024 -> "20–100 MB"
    bytes >= 5L * 1024 * 1024 -> "5–20 MB"
    else -> "5 MB 以下"
}

private fun fileCountBand(count: Int): String = when {
    count >= 10 -> "10 个文件以上"
    count >= 5 -> "5–9 个文件"
    count >= 2 -> "2–4 个文件"
    else -> "1 个文件"
}
