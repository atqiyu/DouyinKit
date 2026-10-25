package com.atqiyu.douyinkit.ui.downloads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadCleanupDialog(
    groups: List<DownloadGroup>,
    onDismiss: () -> Unit,
    onDelete: (List<DownloadCleanupUnit>, Boolean) -> Unit,
) {
    val unitsByGroup = remember(groups) {
        groups.associate { group -> group.awemeId to group.cleanupUnits() }
    }
    val allUnits = remember(unitsByGroup) { unitsByGroup.values.flatten() }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pickerGroup by remember { mutableStateOf<DownloadGroup?>(null) }
    var pickerSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showConfirm by remember { mutableStateOf(false) }
    var deleteLocalFiles by remember { mutableStateOf(false) }

    val selectedUnits = allUnits.filter { it.key in selectedKeys }
    val allSelected = allUnits.isNotEmpty() && allUnits.all { it.key in selectedKeys }

    fun toggleGroup(group: DownloadGroup) {
        val keys = unitsByGroup[group.awemeId].orEmpty().map { it.key }.toSet()
        if (keys.isEmpty()) return
        selectedKeys = if (keys.all { it in selectedKeys }) {
            selectedKeys - keys
        } else {
            selectedKeys + keys
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        DialogSystemBars()
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 8.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "管理下载记录",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "已选 ${selectedUnits.size} / ${allUnits.size} 个内容 · 长按作品选择部分",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = {
                            selectedKeys = if (allSelected) {
                                emptySet()
                            } else {
                                allUnits.map { it.key }.toSet()
                            }
                        },
                        enabled = allUnits.isNotEmpty(),
                    ) {
                        Text(if (allSelected) "取消全选" else "全选")
                    }
                }

                if (groups.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "没有可管理的下载记录",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(groups, key = { it.awemeId }) { group ->
                            val units = unitsByGroup[group.awemeId].orEmpty()
                            val selectedCount = units.count { it.key in selectedKeys }
                            val state = when {
                                units.isEmpty() -> ToggleableState.Off
                                selectedCount == 0 -> ToggleableState.Off
                                selectedCount == units.size -> ToggleableState.On
                                else -> ToggleableState.Indeterminate
                            }
                            CleanupGroupRow(
                                group = group,
                                unitCount = units.size,
                                selectedCount = selectedCount,
                                state = state,
                                onClick = { toggleGroup(group) },
                                onLongClick = {
                                    if (units.isNotEmpty()) {
                                        pickerGroup = group
                                        pickerSelection = units
                                            .filter { it.key in selectedKeys }
                                            .map { it.key }
                                            .toSet()
                                    }
                                },
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedUnits.isEmpty()) {
                                "请选择要删除的记录"
                            } else {
                                "已选 ${selectedUnits.size} 个内容"
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = "${selectedUnits.map { it.group.awemeId }.distinct().size} 个作品",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            deleteLocalFiles = false
                            showConfirm = true
                        },
                        enabled = selectedUnits.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("删除记录")
                    }
                }
            }
        }

        val groupForPicker = pickerGroup
        if (groupForPicker != null) {
            val units = unitsByGroup[groupForPicker.awemeId].orEmpty()
            val groupKeys = units.map { it.key }.toSet()
            val allGroupSelected = groupKeys.isNotEmpty() && groupKeys.all { it in pickerSelection }
            ModalBottomSheet(
                onDismissRequest = { pickerGroup = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 16.dp),
                ) {
                    Text(
                        text = "选择要删除的内容",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = groupForPicker.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "已选 ${pickerSelection.size} / ${units.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                pickerSelection = if (allGroupSelected) {
                                    emptySet()
                                } else {
                                    groupKeys
                                }
                            },
                            enabled = units.isNotEmpty(),
                        ) {
                            Text(if (allGroupSelected) "取消全选" else "全选")
                        }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                    ) {
                        items(units, key = { it.key }) { unit ->
                            val checked = unit.key in pickerSelection
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        pickerSelection = if (checked) {
                                            pickerSelection - unit.key
                                        } else {
                                            pickerSelection + unit.key
                                        }
                                    }
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = null,
                                )
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(
                                            (unit.kind?.color ?: MaterialTheme.colorScheme.onSurfaceVariant)
                                                .copy(alpha = 0.14f),
                                            RoundedCornerShape(10.dp),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = unit.kind?.icon ?: Icons.Default.Download,
                                        contentDescription = null,
                                        tint = unit.kind?.color
                                            ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = unit.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = unit.detail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            selectedKeys = (selectedKeys - groupKeys) + pickerSelection
                            pickerGroup = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("确定（${pickerSelection.size}）")
                    }
                }
            }
        }

        if (showConfirm) {
            val hasLocalFiles = selectedUnits.any { it.file != null }
            AlertDialog(
                onDismissRequest = { showConfirm = false },
                title = { Text("删除所选下载记录") },
                text = {
                    Column {
                        Text(
                            text = "将删除 ${selectedUnits.size} 个下载内容记录，涉及 ${
                                selectedUnits.map { it.group.awemeId }.distinct().size
                            } 个作品。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (hasLocalFiles) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { deleteLocalFiles = !deleteLocalFiles },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = deleteLocalFiles,
                                    onCheckedChange = null,
                                )
                                Column {
                                    Text(
                                        text = "同时删除本地文件",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        text = "从相册、音乐或下载目录中永久删除对应文件",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (deleteLocalFiles) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "本地文件删除后无法恢复，请确认已不再需要这些文件。",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "所选内容没有本地文件，仅删除记录。",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showConfirm = false
                            onDelete(selectedUnits, deleteLocalFiles)
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("确认删除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirm = false }) {
                        Text("取消")
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CleanupGroupRow(
    group: DownloadGroup,
    unitCount: Int,
    selectedCount: Int,
    state: ToggleableState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val enabled = unitCount > 0
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = "选择这个作品的全部内容",
                onLongClickLabel = "选择部分内容",
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (selectedCount > 0) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            1.dp,
            if (selectedCount > 0) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TriStateCheckbox(
                state = state,
                onClick = null,
                enabled = enabled,
            )
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .aspectRatio(0.74f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                group.coverModel?.let { cover ->
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${group.aweme.author.displayName} · $unitCount 个内容",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        !enabled -> "暂无可删除内容"
                        selectedCount == 0 -> "长按可选择单个文件"
                        else -> "已选 $selectedCount / $unitCount"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selectedCount > 0) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun DialogSystemBars() {
    val view = LocalView.current
    val lightBackground = MaterialTheme.colorScheme.background.luminance() > 0.5f
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBackground
            isAppearanceLightNavigationBars = lightBackground
        }
    }
}
