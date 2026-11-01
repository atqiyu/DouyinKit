package com.atqiyu.douyinkit.ui.downloads

import com.atqiyu.douyinkit.data.prefs.DownloadSortMode

const val FAVORITES_COLLECTION = "__favorites__"

val DownloadSortMode.label: String
    get() = when (this) {
        DownloadSortMode.LATEST -> "最近下载"
        DownloadSortMode.OLDEST -> "最早下载"
        DownloadSortMode.LARGEST -> "文件最大"
        DownloadSortMode.MOST_FILES -> "文件最多"
        DownloadSortMode.TITLE -> "按作品标题"
        DownloadSortMode.AUTHOR -> "按作者昵称"
    }

val DownloadSortMode.shortLabel: String
    get() = when (this) {
        DownloadSortMode.LATEST -> "最近"
        DownloadSortMode.OLDEST -> "最早"
        DownloadSortMode.LARGEST -> "最大"
        DownloadSortMode.MOST_FILES -> "最多"
        DownloadSortMode.TITLE -> "标题"
        DownloadSortMode.AUTHOR -> "作者"
    }

fun sortDownloadGroups(
    groups: List<DownloadGroup>,
    mode: DownloadSortMode,
): List<DownloadGroup> {
    val comparator: Comparator<DownloadGroup> = when (mode) {
        DownloadSortMode.LATEST -> compareByDescending { it.latestAt }
        DownloadSortMode.OLDEST -> compareBy { it.latestAt }
        DownloadSortMode.LARGEST -> compareByDescending<DownloadGroup> { it.totalBytes }
            .thenByDescending { it.latestAt }

        DownloadSortMode.MOST_FILES -> compareByDescending<DownloadGroup> { it.fileCount }
            .thenByDescending { it.latestAt }

        DownloadSortMode.TITLE -> compareBy<DownloadGroup> { it.title.lowercase() }
            .thenByDescending { it.latestAt }

        DownloadSortMode.AUTHOR -> compareBy<DownloadGroup> {
            it.aweme.author.displayName.lowercase()
        }.thenByDescending { it.latestAt }
    }
    return groups.sortedWith(comparator)
}

fun filterDownloadGroups(
    groups: List<DownloadGroup>,
    filter: String?,
    membershipByAweme: Map<String, String>,
    favoriteIds: Set<String> = emptySet(),
): List<DownloadGroup> {
    return when (filter) {
        null -> groups
        FAVORITES_COLLECTION -> groups.filter { it.awemeId in favoriteIds }
        else -> groups.filter { membershipByAweme[it.awemeId] == filter }
    }
}
