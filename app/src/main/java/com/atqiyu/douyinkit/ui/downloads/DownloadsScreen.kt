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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val activeTasks = groups.sumOf { it.activeTasks.size }
    val totalFiles = groups.sumOf { it.fileCount }
    val totalBytes = groups.sumOf { it.totalBytes }

    var gridModeOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val gridMode = gridModeOverride ?: loadedSettings?.downloadsGridView ?: true
    var previewAwemeId by rememberSaveable { mutableStateOf<String?>(null) }
    var previewInitialUri by remember { mutableStateOf<String?>(null) }
    var cleanupOpen by remember { mutableStateOf(false) }
    var partsGroup by remember { mutableStateOf<DownloadGroup?>(null) }
    var partsSelectionUri by remember { mutableStateOf<String?>(null) }
    var partDetails by remember { mutableStateOf<GroupMediaItem?>(null) }
    val previewGroup = groups.firstOrNull { it.awemeId == previewAwemeId }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "下载",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = when {
                            activeTasks > 0 ->
                                "$activeTasks 个任务进行中 · ${groups.size} 个作品"

                            groups.isEmpty() -> "下载完成后会出现在系统相册"
                            else -> buildString {
                                append("${groups.size} 个作品 · $totalFiles 个文件")
                                if (totalBytes > 0) append(" · ${formatBytes(totalBytes)}")
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                if (groups.any { it.cleanupUnits().isNotEmpty() }) {
                    IconButton(onClick = { cleanupOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "管理下载记录",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (groups.isEmpty()) {
            item(
                key = "empty",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                EmptyDownloads()
            }
        } else {
            items(
                items = groups,
                key = { it.awemeId },
                contentType = { if (gridMode) "download-grid" else "download-list" },
            ) { group ->
                if (gridMode) {
                    DownloadGridCard(
                        group = group,
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
