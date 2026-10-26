package com.atqiyu.douyinkit.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atqiyu.douyinkit.core.util.formatBytes
import com.atqiyu.douyinkit.core.util.formatDuration
import com.atqiyu.douyinkit.ui.theme.BrandPink
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkPartsSheet(
    group: DownloadGroup,
    selectedUri: String?,
    currentCollectionName: String?,
    onSelect: (GroupMediaItem) -> Unit,
    onDismiss: () -> Unit,
    onDetails: (GroupMediaItem) -> Unit,
    onAnalyze: (GroupMediaItem) -> Unit,
    onOpen: (GroupMediaItem) -> Unit,
    onMoveToCollection: () -> Unit,
) {
    val selected = group.mediaItems.firstOrNull {
        it.file.uri.toString() == selectedUri
    } ?: group.mediaItems.firstOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp),
        ) {
            Text(
                text = "选择作品内容",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = group.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))

            if (group.mediaItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "这个作品还没有可预览的本地文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(
                        items = group.mediaItems,
                        key = { it.file.uri.toString() },
                    ) { item ->
                        PartRow(
                            item = item,
                            selected = item.file.uri.toString() == selected?.file?.uri?.toString(),
                            onClick = { onSelect(item) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { selected?.let(onDetails) },
                    enabled = selected != null,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                ) {
                    Text("查看详情", maxLines = 1, softWrap = false)
                }
                OutlinedButton(
                    onClick = { selected?.let(onAnalyze) },
                    enabled = selected != null,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                ) {
                    Text("分析", maxLines = 1, softWrap = false)
                }
                Button(
                    onClick = { selected?.let(onOpen) },
                    enabled = selected != null,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPink),
                ) {
                    Text("打开", maxLines = 1, softWrap = false)
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onMoveToCollection,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = currentCollectionName?.let { "已在合集「$it」· 点击移动" }
                        ?: "移动到合集",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PartRow(
    item: GroupMediaItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(item.kind.color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.kind.icon,
                contentDescription = null,
                tint = item.kind.color,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.file.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.detailSummary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RadioButton(
            selected = selected,
            onClick = null,
        )
    }
}

@Composable
fun PartDetailsDialog(
    item: GroupMediaItem,
    onDismiss: () -> Unit,
) {
    val type = when (item.kind) {
        GroupMediaKind.VIDEO -> "视频"
        GroupMediaKind.IMAGE -> "图片"
        GroupMediaKind.LIVE -> "LivePhoto 动图"
        GroupMediaKind.AUDIO -> "BGM 音频"
        GroupMediaKind.ZIP -> "ZIP 压缩包"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文件详情") },
        text = {
            Column {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = item.file.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                DetailRow("类型", type)
                item.dimensionLabel?.let { DetailRow("分辨率", it) }
                item.fps?.takeIf { it > 0 }?.let { DetailRow("帧率", "${it}fps") }
                item.codec?.let { codec ->
                    DetailRow(
                        "编码",
                        when (codec) {
                            com.atqiyu.douyinkit.core.model.VideoCodec.H264 -> "H.264"
                            com.atqiyu.douyinkit.core.model.VideoCodec.H265 -> "H.265"
                            com.atqiyu.douyinkit.core.model.VideoCodec.UNKNOWN -> "未知编码"
                        },
                    )
                }
                item.bitrateBps?.takeIf { it > 0 }?.let {
                    DetailRow("码率", "${it / 1000} kbps")
                }
                item.durationMs.takeIf { it > 0 }?.let {
                    DetailRow("时长", formatDuration(it))
                }
                if (item.formatLabel.isNotBlank()) {
                    DetailRow("格式", item.formatLabel)
                }
                DetailRow("大小", formatBytes(item.file.bytes))
                DetailRow(
                    "保存位置",
                    item.file.path?.substringAfterLast('/')?.let { "本机相册 / $it" }
                        ?: "系统媒体库",
                )
                DetailRow(
                    "下载时间",
                    formatSavedAt(item.task.createdAt),
                )
                item.music?.takeIf { it.author.isNotBlank() }?.let {
                    DetailRow("BGM 作者", it.author)
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    text = "作品 ID ${item.task.request.aweme.awemeId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatSavedAt(timestamp: Long): String {
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
    }.getOrDefault("未知")
}
