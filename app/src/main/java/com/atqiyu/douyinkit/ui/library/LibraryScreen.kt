package com.atqiyu.douyinkit.ui.library

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.data.db.AwemeEntity
import com.atqiyu.douyinkit.di.appContainer
import com.atqiyu.douyinkit.ui.theme.BrandCyan
import com.atqiyu.douyinkit.ui.theme.BrandPink
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private enum class LibraryFilter(val label: String) {
    ALL("全部"),
    VIDEO("视频"),
    IMAGES("图文"),
    FAVORITE("收藏"),
}

@Composable
fun LibraryScreen(
    onOpen: (Aweme) -> Unit,
) {
    val context = LocalContext.current
    val repository = context.appContainer.historyRepository
    val scope = rememberCoroutineScope()
    val entities by repository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<AwemeEntity?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    val filter = LibraryFilter.entries[filterIndex]

    val decoded = remember(entities) {
        entities.mapNotNull { entity ->
            repository.decode(entity)?.let { entity to it }
        }
    }
    val allTags = remember(entities) {
        entities.flatMap { repository.tagsOf(it) }.distinct().take(24)
    }
    val filtered = remember(decoded, query, filter, selectedTag) {
        decoded.filter { (entity, aweme) ->
            val matchesQuery = query.isBlank() ||
                aweme.displayTitle.contains(query, ignoreCase = true) ||
                aweme.author.displayName.contains(query, ignoreCase = true) ||
                aweme.desc.contains(query, ignoreCase = true) ||
                entity.tags.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                LibraryFilter.ALL -> true
                LibraryFilter.VIDEO -> aweme.kind == AwemeKind.VIDEO
                LibraryFilter.IMAGES -> aweme.kind == AwemeKind.IMAGES
                LibraryFilter.FAVORITE -> entity.isFavorite
            }
            val matchesTag = selectedTag == null || selectedTag in repository.tagsOf(entity)
            matchesQuery && matchesFilter && matchesTag
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selectionMode) "已选 ${selectedIds.size} 项" else "资料库",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (selectionMode) {
                            "点击作品可多选"
                        } else {
                            "${entities.size} 条解析记录 · 收藏 ${entities.count { it.isFavorite }}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selectionMode) {
                    val allFilteredSelected = filtered.isNotEmpty() &&
                        filtered.all { it.first.awemeId in selectedIds }
                    TextButton(
                        onClick = {
                            selectedIds = if (allFilteredSelected) {
                                emptySet()
                            } else {
                                filtered.map { it.first.awemeId }.toSet()
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
                    TextButton(
                        onClick = { selectionMode = true },
                        enabled = entities.isNotEmpty(),
                    ) {
                        Text("选择")
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索标题、作者或标签") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
        }

        item {
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

        if (filtered.isEmpty()) {
            item {
                EmptyLibrary(hasHistory = entities.isNotEmpty())
            }
        } else {
            items(filtered, key = { it.first.awemeId }) { (entity, aweme) ->
                LibraryCard(
                    entity = entity,
                    aweme = aweme,
                    tags = repository.tagsOf(entity),
                    selectionMode = selectionMode,
                    selected = entity.awemeId in selectedIds,
                    onClick = {
                        if (selectionMode) {
                            selectedIds = if (entity.awemeId in selectedIds) {
                                selectedIds - entity.awemeId
                            } else {
                                selectedIds + entity.awemeId
                            }
                        } else {
                            onOpen(aweme)
                        }
                    },
                    onToggleSelected = {
                        selectedIds = if (entity.awemeId in selectedIds) {
                            selectedIds - entity.awemeId
                        } else {
                            selectedIds + entity.awemeId
                        }
                    },
                    onToggleFavorite = {
                        scope.launch {
                            repository.setFavorite(entity.awemeId, !entity.isFavorite)
                        }
                    },
                    onDelete = { pendingDelete = entity },
                )
            }
        }
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
}

@Composable
private fun LibraryCard(
    entity: AwemeEntity,
    aweme: Aweme,
    tags: List<String>,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleSelected: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
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
                if (entity.coverUrl != null) {
                    AsyncImage(
                        model = entity.coverUrl,
                        contentDescription = entity.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        text = formatParsedAt(entity.parsedAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

private fun formatParsedAt(timestamp: Long): String {
    return runCatching {
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
    }.getOrDefault("")
}
