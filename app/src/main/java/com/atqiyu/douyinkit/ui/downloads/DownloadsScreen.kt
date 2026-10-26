package com.atqiyu.douyinkit.ui.downloads

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.core.util.formatBytes
import com.atqiyu.douyinkit.data.db.DownloadCollectionEntity
import com.atqiyu.douyinkit.data.prefs.DownloadSortMode
import com.atqiyu.douyinkit.di.appContainer
import com.atqiyu.douyinkit.download.DownloadRegistry
import com.atqiyu.douyinkit.download.DownloadService
import com.atqiyu.douyinkit.ui.theme.BrandCyan
import com.atqiyu.douyinkit.ui.theme.BrandPink
import kotlinx.coroutines.launch

@Composable
fun DownloadsScreen() {
    val tasks by DownloadRegistry.tasks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val repository = context.appContainer.downloadHistoryRepository
    val settingsRepository = context.appContainer.settingsRepository
    val loadedSettings by settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val persisted by repository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val liveIds = remember(tasks) { tasks.map { it.id }.toSet() }
    val historyTasks = remember(persisted, liveIds) {
        persisted
            .filter { it.taskId !in liveIds }
            .mapNotNull { repository.toTask(it) }
    }
    val groups = remember(tasks, historyTasks) {
        buildDownloadGroups(tasks + historyTasks)
    }
    val collectionRepository = context.appContainer.downloadCollectionRepository
    val collections by collectionRepository.collections.collectAsStateWithLifecycle(emptyList())
    val memberships by collectionRepository.memberships.collectAsStateWithLifecycle(emptyList())
    val membershipByAweme = remember(memberships) {
        memberships.associate { it.awemeId to it.collectionId }
    }
    val collectionById = remember(collections) { collections.associateBy { it.id } }
    val collectionCounts = remember(groups, membershipByAweme) {
        val counts = mutableMapOf<String, Int>()
        groups.forEach { group ->
            val id = membershipByAweme[group.awemeId] ?: return@forEach
            counts[id] = (counts[id] ?: 0) + 1
        }
        counts
    }
    val uncategorizedCount = groups.count { it.awemeId !in membershipByAweme }

    var collectionFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var sortModeOverride by remember { mutableStateOf<DownloadSortMode?>(null) }
    val sortMode = sortModeOverride ?: loadedSettings?.downloadsSortMode ?: DownloadSortMode.LATEST
    val sortedGroups = remember(groups, sortMode) { sortDownloadGroups(groups, sortMode) }
    val visibleGroups = remember(sortedGroups, collectionFilter, membershipByAweme) {
        filterDownloadGroups(sortedGroups, collectionFilter, membershipByAweme)
    }
    val activeTasks = visibleGroups.sumOf { it.activeTasks.size }
    val totalFiles = visibleGroups.sumOf { it.fileCount }
    val totalBytes = visibleGroups.sumOf { it.totalBytes }
    val filterLabel = when (collectionFilter) {
        null -> null
        UNCATEGORIZED_COLLECTION -> "未分类"
        else -> collectionById[collectionFilter]?.name
    }

    var gridModeOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val gridMode = gridModeOverride ?: loadedSettings?.downloadsGridView ?: true
    var previewAwemeId by rememberSaveable { mutableStateOf<String?>(null) }
    var previewInitialUri by remember { mutableStateOf<String?>(null) }
    var cleanupOpen by remember { mutableStateOf(false) }
    var partsGroup by remember { mutableStateOf<DownloadGroup?>(null) }
    var partsSelectionUri by remember { mutableStateOf<String?>(null) }
    var partDetails by remember { mutableStateOf<GroupMediaItem?>(null) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var collectionMenuOpen by remember { mutableStateOf(false) }
    var showCreateCollection by remember { mutableStateOf(false) }
    var createForAwemeId by remember { mutableStateOf<String?>(null) }
    var showManageCollections by remember { mutableStateOf(false) }
    var moveGroup by remember { mutableStateOf<DownloadGroup?>(null) }
    var renameTarget by remember { mutableStateOf<DownloadCollectionEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<DownloadCollectionEntity?>(null) }
    val previewGroup = groups.firstOrNull { it.awemeId == previewAwemeId }

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun moveWorkToCollection(group: DownloadGroup, collectionId: String?) {
        scope.launch {
            runCatching { collectionRepository.move(group.awemeId, collectionId) }
                .onSuccess {
                    moveGroup = null
                    showToast(
                        collectionId?.let { id ->
                            "已移动到「${collectionById[id]?.name ?: "合集"}」"
                        } ?: "已移出合集",
                    )
                }
                .onFailure { showToast("移动失败：${it.message ?: "未知错误"}") }
        }
    }

    LaunchedEffect(collections, collectionFilter) {
        val filter = collectionFilter ?: return@LaunchedEffect
        if (filter != UNCATEGORIZED_COLLECTION && collections.none { it.id == filter }) {
            collectionFilter = null
        }
    }

    fun removeGroup(group: DownloadGroup) {
        group.tasks.forEach { task ->
            if (task.isActiveDownload()) {
                DownloadService.cancel(context, task.id)
            }
            DownloadRegistry.remove(task.id)
        }
        scope.launch {
            group.tasks.forEach { repository.remove(it.id) }
        }
        if (previewAwemeId == group.awemeId) {
            previewAwemeId = null
        }
    }

    LazyVerticalGrid(
        columns = if (gridMode) GridCells.Fixed(2) else GridCells.Fixed(1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(
            key = "header",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "下载",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "选择排序方式",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false },
                        ) {
                            DownloadSortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
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
                                        sortModeOverride = mode
                                        scope.launch {
                                            settingsRepository.setDownloadsSortMode(mode)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { collectionMenuOpen = true }) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "合集",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(
                            expanded = collectionMenuOpen,
                            onDismissRequest = { collectionMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("新建合集") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(19.dp),
                                    )
                                },
                                onClick = {
                                    collectionMenuOpen = false
                                    createForAwemeId = null
                                    showCreateCollection = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("管理合集（${collections.size}）") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(19.dp),
                                    )
                                },
                                onClick = {
                                    collectionMenuOpen = false
                                    showManageCollections = true
                                },
                            )
                        }
                    }
                    if (groups.any { it.cleanupUnits().isNotEmpty() }) {
                        IconButton(onClick = { cleanupOpen = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "管理下载记录",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = CircleShape,
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            ViewModeButton(
                                selected = gridMode,
                                icon = Icons.Default.GridView,
                                label = "网格视图",
                                onClick = {
                                    gridModeOverride = true
                                    scope.launch { settingsRepository.setDownloadsGridView(true) }
                                },
                            )
                            ViewModeButton(
                                selected = !gridMode,
                                icon = Icons.AutoMirrored.Filled.ViewList,
                                label = "列表视图",
                                onClick = {
                                    gridModeOverride = false
                                    scope.launch { settingsRepository.setDownloadsGridView(false) }
                                },
                            )
                        }
                    }
                }
                Text(
                    text = when {
                        groups.isEmpty() -> "下载完成后会出现在系统相册"
                        activeTasks > 0 ->
                            "$activeTasks 个任务进行中 · ${visibleGroups.size} 个作品"

                        filterLabel != null -> buildString {
                            append("$filterLabel · ${visibleGroups.size} 个作品 · $totalFiles 个文件")
                            if (totalBytes > 0) append(" · ${formatBytes(totalBytes)}")
                        }

                        else -> buildString {
                            append("${visibleGroups.size} 个作品 · $totalFiles 个文件")
                            if (totalBytes > 0) append(" · ${formatBytes(totalBytes)}")
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                CollectionFilterRow(
                    totalCount = groups.size,
                    uncategorizedCount = uncategorizedCount,
                    collections = collections,
                    selected = collectionFilter,
                    onSelect = { collectionFilter = it },
                    onCreate = {
                        createForAwemeId = null
                        showCreateCollection = true
                    },
                )
            }
        }

        if (groups.isEmpty()) {
            item(
                key = "empty",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                EmptyDownloads()
            }
        } else if (visibleGroups.isEmpty()) {
            item(
                key = "empty_collection",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                EmptyCollection(
                    label = filterLabel ?: "这个合集",
                    onShowAll = { collectionFilter = null },
                )
            }
        } else {
            items(
                items = visibleGroups,
                key = { it.awemeId },
                contentType = { if (gridMode) "download-grid" else "download-list" },
            ) { group ->
                val collectionName = membershipByAweme[group.awemeId]
                    ?.let(collectionById::get)
                    ?.name
                if (gridMode) {
                    DownloadGridCard(
                        group = group,
                        collectionName = collectionName,
                        onClick = {
                            previewInitialUri = null
                            previewAwemeId = group.awemeId
                        },
                        onLongClick = {
                            partsGroup = group
                            partsSelectionUri = group.featuredMedia?.file?.uri?.toString()
                        },
                    )
                } else {
                    DownloadListCard(
                        group = group,
                        collectionName = collectionName,
                        onClick = {
                            previewInitialUri = null
                            previewAwemeId = group.awemeId
                        },
                        onLongClick = {
                            partsGroup = group
                            partsSelectionUri = group.featuredMedia?.file?.uri?.toString()
                        },
                    )
                }
            }
        }
    }

    if (previewGroup != null) {
        WorkPreviewDialog(
            group = previewGroup,
            initialFileUri = previewInitialUri,
            onDismiss = {
                previewAwemeId = null
                previewInitialUri = null
            },
            onRemoveGroup = { removeGroup(it) },
        )
    }

    partsGroup?.let { group ->
        WorkPartsSheet(
            group = group,
            selectedUri = partsSelectionUri,
            currentCollectionName = membershipByAweme[group.awemeId]
                ?.let(collectionById::get)
                ?.name,
            onSelect = { partsSelectionUri = it.file.uri.toString() },
            onDismiss = { partsGroup = null },
            onDetails = {
                partDetails = it
                partsGroup = null
            },
            onAnalyze = { item ->
                previewInitialUri = item.file.uri.toString()
                previewAwemeId = group.awemeId
                partsGroup = null
            },
            onOpen = {
                openSavedFile(context, it.file)
                partsGroup = null
            },
            onMoveToCollection = {
                moveGroup = group
                partsGroup = null
            },
        )
    }

    partDetails?.let { item ->
        PartDetailsDialog(
            item = item,
            onDismiss = { partDetails = null },
        )
    }

    if (cleanupOpen) {
        DownloadCleanupDialog(
            groups = groups,
            onDismiss = { cleanupOpen = false },
            onDelete = { units, deleteLocalFiles ->
                cleanupOpen = false
                scope.launch {
                    val result = performDownloadCleanup(
                        context = context,
                        repository = repository,
                        units = units,
                        deleteLocalFiles = deleteLocalFiles,
                    )
                    val removedItems = (units.size - result.failedFiles.size).coerceAtLeast(0)
                    val message = buildString {
                        if (deleteLocalFiles) {
                            append("已删除 ${result.deletedFiles} 个本地文件、$removedItems 个下载内容")
                        } else {
                            append("已移除 $removedItems 个下载内容记录")
                        }
                        if (result.failedFiles.isNotEmpty()) {
                            append("；${result.failedFiles.size} 个文件删除失败，对应记录已保留")
                        }
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    if (showManageCollections) {
        ManageCollectionsDialog(
            collections = collections,
            counts = collectionCounts,
            onDismiss = { showManageCollections = false },
            onCreate = {
                showManageCollections = false
                createForAwemeId = null
                showCreateCollection = true
            },
            onRename = { renameTarget = it },
            onDelete = { deleteTarget = it },
        )
    }

    if (showCreateCollection) {
        CollectionNameDialog(
            title = if (createForAwemeId != null) "新建合集并移动" else "新建合集",
            initialName = "",
            confirmLabel = "创建",
            onDismiss = {
                showCreateCollection = false
                createForAwemeId = null
            },
            onConfirm = { name ->
                val cleanName = name.trim()
                if (collections.any { it.name.equals(cleanName, ignoreCase = true) }) {
                    showToast("已有同名合集")
                } else {
                    val moveTarget = createForAwemeId
                    scope.launch {
                        runCatching { collectionRepository.create(cleanName) }
                            .onSuccess { created ->
                                if (moveTarget != null) {
                                    runCatching {
                                        collectionRepository.move(moveTarget, created.id)
                                    }.onSuccess {
                                        showToast("已创建「${created.name}」并移动作品")
                                    }.onFailure {
                                        showToast("合集已创建，但移动作品失败")
                                    }
                                } else {
                                    showToast("已创建合集「${created.name}」")
                                }
                                collectionFilter = created.id
                                createForAwemeId = null
                                showCreateCollection = false
                            }
                            .onFailure { showToast("创建合集失败：${it.message ?: "未知错误"}") }
                    }
                }
            },
        )
    }

    renameTarget?.let { target ->
        CollectionNameDialog(
            title = "重命名合集",
            initialName = target.name,
            confirmLabel = "保存",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                val cleanName = name.trim()
                if (collections.any {
                        it.id != target.id && it.name.equals(cleanName, ignoreCase = true)
                    }
                ) {
                    showToast("已有同名合集")
                } else {
                    scope.launch {
                        runCatching { collectionRepository.rename(target.id, cleanName) }
                            .onSuccess {
                                renameTarget = null
                                showToast("合集已重命名")
                            }
                            .onFailure {
                                showToast("重命名失败：${it.message ?: "未知错误"}")
                            }
                    }
                }
            },
        )
    }

    deleteTarget?.let { target ->
        DeleteCollectionDialog(
            collection = target,
            workCount = collectionCounts[target.id] ?: 0,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                scope.launch {
                    runCatching { collectionRepository.delete(target.id) }
                        .onSuccess {
                            if (collectionFilter == target.id) collectionFilter = null
                            deleteTarget = null
                            showToast("已删除合集「${target.name}」")
                        }
                        .onFailure {
                            showToast("删除合集失败：${it.message ?: "未知错误"}")
                        }
                }
            },
        )
    }

    moveGroup?.let { group ->
        MoveToCollectionSheet(
            workTitle = group.title,
            collections = collections,
            counts = collectionCounts,
            currentCollectionId = membershipByAweme[group.awemeId],
            onDismiss = { moveGroup = null },
            onSelect = { collectionId -> moveWorkToCollection(group, collectionId) },
            onCreateNew = {
                moveGroup = null
                createForAwemeId = group.awemeId
                showCreateCollection = true
            },
        )
    }
}

@Composable
private fun ViewModeButton(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(34.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                CircleShape,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(19.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadGridCard(
    group: DownloadGroup,
    collectionName: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = "打开作品预览",
                onLongClickLabel = "选择作品内容",
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.76f),
            ) {
                GroupCover(
                    group = group,
                    modifier = Modifier.fillMaxSize(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    CoverBadge(
                        text = if (group.aweme.kind == AwemeKind.IMAGES) "图文" else "视频",
                        color = if (group.aweme.kind == AwemeKind.IMAGES) BrandCyan else BrandPink,
                    )
                    if (group.isActive) {
                        CoverBadge(text = "下载中", color = BrandPink)
                    } else if (group.fileCount > 0) {
                        CoverBadge(text = "${group.fileCount} 个文件", color = Color.White)
                    }
                }

                if (group.isActive) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                                ),
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        val fraction = group.progressFraction
                        if (fraction != null) {
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = BrandPink,
                                trackColor = Color.White.copy(alpha = 0.2f),
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = BrandPink,
                                trackColor = Color.White.copy(alpha = 0.2f),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = group.statusLine,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${group.aweme.author.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (collectionName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = collectionName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = if (group.isActive) {
                        group.statusLine
                    } else {
                        group.summaryLine
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = groupStatusColor(group),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadListCard(
    group: DownloadGroup,
    collectionName: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = "打开作品预览",
                onLongClickLabel = "选择作品内容",
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .aspectRatio(0.74f)
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                GroupCover(
                    group = group,
                    modifier = Modifier.fillMaxSize(),
                )
                if (group.isActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 5.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = group.statusLine,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (group.aweme.kind == AwemeKind.IMAGES) "图文" else "视频",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (group.aweme.kind == AwemeKind.IMAGES) BrandCyan else BrandPink,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = group.summaryLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${group.aweme.author.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (collectionName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = collectionName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                val fraction = group.progressFraction
                if (group.isActive && fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = BrandPink,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
                Text(
                    text = group.statusLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = groupStatusColor(group),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = "打开作品预览",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp),
            )
        }
    }
}

@Composable
private fun GroupCover(
    group: DownloadGroup,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (group.aweme.kind == AwemeKind.IMAGES) {
                Icons.Default.PhotoLibrary
            } else {
                Icons.Default.PlayCircle
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(34.dp),
        )
        if (group.coverIsVideo) {
            group.aweme.primaryCoverUrl?.let { remoteCover ->
                AsyncImage(
                    model = remoteCover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        group.coverModel?.let { cover ->
            AsyncImage(
                model = cover,
                contentDescription = group.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        if (group.coverIsVideo && !group.isActive) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.38f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun CoverBadge(text: String, color: Color) {
    Surface(
        color = Color.Black.copy(alpha = 0.52f),
        shape = CircleShape,
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun groupStatusColor(group: DownloadGroup): Color {
    return when {
        group.isActive -> BrandPink
        group.failedTasks.isNotEmpty() && group.completedTasks.isEmpty() ->
            MaterialTheme.colorScheme.error

        group.completedTasks.isNotEmpty() && group.failedTasks.isNotEmpty() ->
            MaterialTheme.colorScheme.tertiary

        group.completedTasks.isNotEmpty() -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun EmptyDownloads() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(42.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "还没有下载任务",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "在解析结果页选择视频清晰度或下载图集，同一个作品的文件会自动归档在一起。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyCollection(
    label: String,
    onShowAll: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(42.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "$label 还没有作品",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "长按任意作品卡片，选择“移动到合集”，即可把作品分类到这里。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onShowAll) {
                Text("查看全部作品")
            }
        }
    }
}
