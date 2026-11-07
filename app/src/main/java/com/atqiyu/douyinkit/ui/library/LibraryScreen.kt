package com.atqiyu.douyinkit.ui.library

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.request.ImageRequest
import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.core.model.Author
import com.atqiyu.douyinkit.data.db.AwemeEntity
import com.atqiyu.douyinkit.data.db.AwemeMeta
import com.atqiyu.douyinkit.di.appContainer
import com.atqiyu.douyinkit.floating.FloatingAssistantService
import com.atqiyu.douyinkit.parser.ParseActivity
import com.atqiyu.douyinkit.ui.components.FadeInAsyncImage
import com.atqiyu.douyinkit.ui.components.FeedSectionHeader
import com.atqiyu.douyinkit.ui.components.ScrollProgressOverlay
import com.atqiyu.douyinkit.ui.theme.BrandCyan
import com.atqiyu.douyinkit.ui.theme.BrandPink
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(
    onOpen: (Aweme) -> Unit,
) {
    val context = LocalContext.current
    val repository = context.appContainer.historyRepository
    val downloadHistoryRepository = context.appContainer.downloadHistoryRepository
    val shareLinkRepository = context.appContainer.shareLinkRepository
    val settingsRepository = context.appContainer.settingsRepository
    val pendingLinkCount by shareLinkRepository.observePendingCount()
        .collectAsStateWithLifecycle(initialValue = 0)
    val scope = rememberCoroutineScope()
    // List rows only need display metadata. Full snapshots are decoded when a work is opened,
    // so 800+ records no longer inflate the heap while the queue is saving new works.
    val metas by repository.observeMeta().collectAsStateWithLifecycle(initialValue = null)
    val downloadTimes by downloadHistoryRepository.observeLatestDownloadTimes()
        .collectAsStateWithLifecycle(initialValue = emptyMap())

    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var sortIndex by rememberSaveable { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<AwemeEntity?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var showLinkRecords by remember { mutableStateOf(false) }
    val filter = LibraryFilter.entries[filterIndex]
    val sortMode = LibrarySortMode.entries[sortIndex]

    val metaList = metas.orEmpty()
    val entries = remember(metas, downloadTimes) {
        metaList.map { meta ->
            val downloadedAt = downloadTimes[meta.awemeId]
            LibraryEntry(
                entity = meta.toStubEntity(),
                aweme = meta.toStubAweme(),
                tags = repository.tagsOf(meta),
                sortMillis = downloadedAt ?: meta.parsedAt,
                downloadedAt = downloadedAt,
            )
        }
    }
    val loading = metas == null
    val allTags = remember(entries) {
        entries.flatMap { it.tags }.distinct().take(24)
    }
    val filtered = remember(entries, query, filter, selectedTag) {
        filterLibraryEntries(entries, query, filter, selectedTag)
    }
    val today = remember { LocalDate.now() }
    val feed = remember(filtered, sortMode, today) {
        buildLibraryFeed(filtered, sortMode, today)
    }

    val listState = rememberLazyListState()
    var loadImages by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling) {
                loadImages = false
            } else {
                delay(350)
                loadImages = true
            }
        }
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            delay(80)
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    fun closeSearch() {
        searchOpen = false
        query = ""
        keyboardController?.hide()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "library_header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (searchOpen && !selectionMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(searchFocusRequester),
                                placeholder = {
                                    Text(
                                        text = "搜索标题、作者或标签",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                trailingIcon = if (query.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { query = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "清空搜索",
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                } else {
                                    null
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(26.dp),
                            )
                            TextButton(onClick = ::closeSearch) {
                                Text("取消")
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (selectionMode) {
                                        "已选 ${selectedIds.size} 项"
                                    } else {
                                        "资料库"
                                    },
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = when {
                                        selectionMode -> "点击作品可多选"
                                        loading -> "正在整理解析记录…"
                                        query.isNotBlank() ||
                                            filter != LibraryFilter.ALL ||
                                            selectedTag != null ->
                                            "找到 ${filtered.size} 条记录"

                                        else -> "${metaList.size} 条解析记录 · " +
                                            "收藏 ${metaList.count { it.isFavorite }}"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (selectionMode) {
                                val allFilteredSelected = filtered.isNotEmpty() &&
                                    filtered.all { it.entity.awemeId in selectedIds }
                                TextButton(
                                    onClick = {
                                        selectedIds = if (allFilteredSelected) {
                                            emptySet()
                                        } else {
                                            filtered.map { it.entity.awemeId }.toSet()
                                        }
                                    },
                                    enabled = filtered.isNotEmpty(),
                                ) {
                                    Text(if (allFilteredSelected) "取消全选" else "全选")
                                }
                                TextButton(
                                    onClick = { showBulkDeleteDialog = true },
                                    enabled = selectedIds.isNotEmpty(),
                                ) {
                                    Text("删除")
                                }
                                TextButton(
                                    onClick = {
                                        selectionMode = false
                                        selectedIds = emptySet()
                                    },
                                ) {
                                    Text("取消")
                                }
                            } else {
                                IconButton(onClick = { showLinkRecords = true }) {
                                    BadgedBox(
                                        badge = {
                                            if (pendingLinkCount > 0) {
                                                Badge { Text(pendingLinkCount.toString()) }
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Link,
                                            contentDescription = "抖音作品链接分享记录",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(onClick = { searchOpen = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "搜索资料库",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        selectionMode = true
                                        searchOpen = false
                                    },
                                    enabled = metaList.isNotEmpty(),
                                ) {
                                    Text("选择")
                                }
                            }
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(LibraryFilter.entries, key = { it.name }) { item ->
                            val index = item.ordinal
                            FilterChip(
                                selected = filterIndex == index,
                                onClick = { filterIndex = index },
                                label = {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                                modifier = Modifier.height(34.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandPink.copy(alpha = 0.16f),
                                    selectedLabelColor = BrandPink,
                                ),
                            )
                        }
                        item {
                            Box {
                                AssistChip(
                                    onClick = { sortMenuOpen = true },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Sort,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = sortMode.label,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    },
                                    modifier = Modifier.height(34.dp),
                                )
                                DropdownMenu(
                                    expanded = sortMenuOpen,
                                    onDismissRequest = { sortMenuOpen = false },
                                ) {
                                    LibrarySortMode.entries.forEach { mode ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(mode.label)
                                                    Text(
                                                        text = mode.caption,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            },
                                            trailingIcon = {
                                                if (sortMode == mode) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = BrandPink,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                            },
                                            onClick = {
                                                sortMenuOpen = false
                                                sortIndex = mode.ordinal
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        if (allTags.isNotEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .width(1.dp)
                                        .height(24.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant),
                                )
                            }
                            items(allTags, key = { it }) { tag ->
                                FilterChip(
                                    selected = selectedTag == tag,
                                    onClick = {
                                        selectedTag = if (selectedTag == tag) null else tag
                                    },
                                    label = {
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    },
                                    modifier = Modifier.height(34.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandCyan.copy(alpha = 0.16f),
                                        selectedLabelColor = MaterialTheme.colorScheme.secondary,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            when {
                loading -> {
                    item(key = "library_loading") {
                        LoadingLibrary()
                    }
                }

                feed.isEmpty() -> {
                    item(key = "library_empty") {
                        EmptyLibrary(hasHistory = metaList.isNotEmpty())
                    }
                }

                else -> {
                    items(
                        items = feed,
                        key = { it.key },
                        contentType = { item ->
                            if (item is LibraryFeedItem.Header) "library-section" else "library-entry"
                        },
                    ) { item ->
                        when (item) {
                            is LibraryFeedItem.Header -> {
                                FeedSectionHeader(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    count = item.count,
                                    modifier = Modifier.padding(horizontal = 2.dp),
                                )
                            }

                            is LibraryFeedItem.Entry -> {
                                val entry = item.item
                                val downloadedAt = entry.downloadedAt
                                val timeLabel = remember(downloadedAt, entry.entity.parsedAt) {
                                    if (downloadedAt != null) {
                                        "下载 ${formatParsedAt(downloadedAt)}"
                                    } else {
                                        "解析 ${formatParsedAt(entry.entity.parsedAt)}"
                                    }
                                }
                                LibraryCard(
                                    entity = entry.entity,
                                    aweme = entry.aweme,
                                    tags = entry.tags,
                                    timeLabel = timeLabel,
                                    loadImage = loadImages,
                                    selectionMode = selectionMode,
                                    selected = entry.entity.awemeId in selectedIds,
                                    onClick = {
                                        if (selectionMode) {
                                            selectedIds = if (
                                                entry.entity.awemeId in selectedIds
                                            ) {
                                                selectedIds - entry.entity.awemeId
                                            } else {
                                                selectedIds + entry.entity.awemeId
                                            }
                                        } else {
                                            scope.launch {
                                                val full = withContext(Dispatchers.IO) {
                                                    repository.get(entry.entity.awemeId)
                                                        ?.let { repository.decode(it) }
                                                }
                                                if (full != null) onOpen(full)
                                            }
                                        }
                                    },
                                    onToggleSelected = {
                                        selectedIds = if (entry.entity.awemeId in selectedIds) {
                                            selectedIds - entry.entity.awemeId
                                        } else {
                                            selectedIds + entry.entity.awemeId
                                        }
                                    },
                                    onToggleFavorite = {
                                        scope.launch {
                                            repository.setFavorite(
                                                entry.entity.awemeId,
                                                !entry.entity.isFavorite,
                                            )
                                        }
                                    },
                                    onDelete = { pendingDelete = entry.entity },
                                )
                            }
                        }
                    }
                }
            }
        }

        ScrollProgressOverlay(
            progressProvider = {
                val info = listState.layoutInfo
                val total = info.totalItemsCount
                if (total <= 1) {
                    0f
                } else {
                    val first = info.visibleItemsInfo.firstOrNull()
                    val itemFraction = if (first != null && first.size > 0) {
                        (-first.offset / first.size.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    ((first?.index ?: 0) + itemFraction) / (total - 1)
                }
            },
            canScrollProvider = {
                val info = listState.layoutInfo
                val first = info.visibleItemsInfo.firstOrNull()?.index ?: 0
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                info.totalItemsCount > 0 &&
                    (first > 0 || last < info.totalItemsCount - 1)
            },
            isScrollInProgressProvider = { listState.isScrollInProgress },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp),
        )
    }

    pendingDelete?.let { entity ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除记录") },
            text = { Text("只删除本机的解析记录，不会删除已经下载到相册的文件。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch { repository.delete(entity.awemeId) }
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (showBulkDeleteDialog) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("删除 $count 条记录") },
            text = { Text("只删除本机的解析记录，不会删除已经下载到相册的文件。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBulkDeleteDialog = false
                        val ids = selectedIds
                        scope.launch {
                            ids.forEach { repository.delete(it) }
                            selectedIds = emptySet()
                            selectionMode = false
                        }
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showLinkRecords) {
        ShareLinkRecordsDialog(
            onDismiss = { showLinkRecords = false },
            onProcess = { links, background ->
                showLinkRecords = false
                if (background) {
                    scope.launch {
                        val enabled = runCatching {
                            settingsRepository.settings.first().floatingBallEnabled
                        }.getOrDefault(false)
                        if (!enabled) {
                            Toast.makeText(
                                context,
                                "请先在设置中开启 Kit 浮球，再使用后台批量处理",
                                Toast.LENGTH_LONG,
                            ).show()
                            return@launch
                        }
                        runCatching {
                            FloatingAssistantService.start(context)
                            FloatingAssistantService.enqueueBatch(context, links)
                        }.onFailure {
                            Toast.makeText(
                                context,
                                "无法启动后台队列：${it.message ?: "系统限制"}",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                } else {
                    val launched = links.take(20)
                    if (links.size > launched.size) {
                        Toast.makeText(
                            context,
                            "前台单次最多 20 条，已先处理前 ${launched.size} 条",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            launched.forEach { url ->
                                runCatching { shareLinkRepository.markProcessing(url) }
                            }
                        }
                        val intent = ParseActivity.intent(context, launched)
                        val activity = context as? Activity
                        if (activity != null) {
                            activity.startActivity(intent)
                        } else {
                            context.startActivity(
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun LibraryCard(
    entity: AwemeEntity,
    aweme: Aweme,
    tags: List<String>,
    timeLabel: String,
    loadImage: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleSelected: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var hasRequestedCover by remember(entity.awemeId) { mutableStateOf(false) }
    LaunchedEffect(loadImage) {
        if (loadImage) hasRequestedCover = true
    }
    val coverRequest = remember(hasRequestedCover, entity.coverUrl) {
        if (!hasRequestedCover) {
            null
        } else {
            entity.coverUrl?.let { url ->
                ImageRequest.Builder(context)
                    .data(url)
                    .size(240, 320)
                    .build()
            }
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(
            1.dp,
            if (selected) BrandPink else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelected() },
                )
                Spacer(Modifier.width(4.dp))
            }
            Box(
                modifier = Modifier
                    .width(86.dp)
                    .aspectRatio(0.74f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (coverRequest != null) {
                    FadeInAsyncImage(
                        model = coverRequest,
                        contentDescription = entity.title,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (aweme.kind == AwemeKind.IMAGES) "图文" else "视频",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (aweme.kind == AwemeKind.IMAGES) BrandCyan else BrandPink,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    if (!selectionMode) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = if (entity.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "收藏",
                                tint = if (entity.isFavorite) BrandPink else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Text(
                    text = entity.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = entity.authorName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        tags.take(3).forEach { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = CircleShape,
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingLibrary() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 30.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LinearProgressIndicator(
                modifier = Modifier.width(52.dp),
                color = BrandPink,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Column {
                Text(
                    text = "正在加载资料库",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "记录较多时会先在后台解析快照，列表随后出现。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(hasHistory: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 30.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(42.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (hasHistory) "没有匹配的记录" else "还没有解析记录",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (hasHistory) "换个关键词或筛选条件试试" else "解析过的作品会自动出现在这里",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun AwemeMeta.toStubEntity(): AwemeEntity = AwemeEntity(
    awemeId = awemeId,
    title = title,
    authorName = authorName,
    desc = desc,
    kind = kind,
    coverUrl = coverUrl,
    sourceUrl = sourceUrl,
    snapshotJson = "",
    isFavorite = isFavorite,
    tags = tags,
    parsedAt = parsedAt,
    createTimeSeconds = createTimeSeconds,
)

private fun AwemeMeta.toStubAweme(): Aweme = Aweme(
    awemeId = awemeId,
    kind = runCatching { AwemeKind.valueOf(kind) }.getOrDefault(AwemeKind.VIDEO),
    desc = desc,
    createTimeSeconds = createTimeSeconds,
    author = Author(nickname = authorName),
    sourceUrl = sourceUrl,
    canonicalUrl = sourceUrl.orEmpty(),
    parsedAtMillis = parsedAt,
)

private fun formatParsedAt(timestamp: Long): String {
    return runCatching {
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
    }.getOrDefault("")
}
