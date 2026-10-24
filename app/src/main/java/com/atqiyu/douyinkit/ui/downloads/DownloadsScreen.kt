package com.atqiyu.douyinkit.ui.downloads

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.core.util.formatBytes
import com.atqiyu.douyinkit.di.appContainer
import com.atqiyu.douyinkit.download.DownloadKind
import com.atqiyu.douyinkit.download.DownloadRegistry
import com.atqiyu.douyinkit.download.DownloadService
import com.atqiyu.douyinkit.download.DownloadStatus
import com.atqiyu.douyinkit.download.DownloadTask
import com.atqiyu.douyinkit.download.SavedFile
import kotlinx.coroutines.launch

@Composable
fun DownloadsScreen() {
    val tasks by DownloadRegistry.tasks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val repository = context.appContainer.downloadHistoryRepository
    val scope = rememberCoroutineScope()
    val persisted by repository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val liveIds = remember(tasks) { tasks.map { it.id }.toSet() }
    val historyTasks = remember(persisted, liveIds) {
        persisted
            .filter { it.taskId !in liveIds }
            .mapNotNull { repository.toTask(it) }
    }
    val ordered = (tasks + historyTasks).sortedByDescending { it.createdAt }
    val active = ordered.count {
        it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED
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
                        text = "下载",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (active > 0) "$active 个任务进行中" else "下载完成后会出现在系统相册",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (ordered.any { !it.isActive() }) {
                    TextButton(
                        onClick = {
                            DownloadRegistry.clearFinished()
                            scope.launch { repository.clearFinished() }
                        },
                    ) {
                        Text("清理已结束")
                    }
                }
            }
        }

        if (ordered.isEmpty()) {
            item { EmptyDownloads() }
        } else {
            items(ordered, key = { it.id }) { task ->
                DownloadTaskCard(
                    task = task,
                    context = context,
                    onRemove = {
                        DownloadRegistry.remove(task.id)
                        scope.launch { repository.remove(task.id) }
                    },
                )
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    context: Context,
    onRemove: () -> Unit,
) {
    val aweme = task.request.aweme
    val progress = task.progress
    val fraction = progress.total?.takeIf { it > 0 }?.let {
        (progress.bytes.toFloat() / it).coerceIn(0f, 1f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = aweme.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${typeLabel(task)} · ${statusLabel(task.status)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor(task.status),
                    )
                }
            }

            if (task.isActive()) {
                Spacer(Modifier.height(12.dp))
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = progressText(task),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatSpeed(progress.speedBps),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (task.message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = task.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (task.status == DownloadStatus.FAILED || task.status == DownloadStatus.NEEDS_REFRESH) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (task.isActive()) {
                    OutlinedAction(
                        onClick = { DownloadService.cancel(context, task.id) },
                        icon = Icons.Default.StopCircle,
                        label = "取消",
                    )
                } else {
                    task.files.firstOrNull()?.let { file ->
                        ActionButton(
                            onClick = { openFile(context, file) },
                            icon = Icons.Default.OpenInNew,
                            label = "打开",
                            primary = true,
                        )
                        IconButton(onClick = { shareFile(context, file) }) {
                            Icon(Icons.Default.Share, contentDescription = "分享")
                        }
                    }
                    if (task.status == DownloadStatus.FAILED) {
                        OutlinedAction(
                            onClick = {
                                runCatching {
                                    DownloadService.enqueue(context, task.request)
                                }.onFailure {
                                    Toast.makeText(context, it.message ?: "重试失败", Toast.LENGTH_SHORT).show()
                                }
                            },
                            icon = Icons.Default.Refresh,
                            label = "重试",
                        )
                    }
                    if (task.status == DownloadStatus.NEEDS_REFRESH) {
                        OutlinedAction(
                            onClick = {
                                Toast.makeText(
                                    context,
                                    "下载地址已过期，请重新解析作品后再下载",
                                    Toast.LENGTH_LONG,
                                ).show()
                            },
                            icon = Icons.Default.Refresh,
                            label = "刷新解析",
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "移除记录",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    primary: Boolean,
) {
    if (primary) {
        Button(onClick = onClick) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label)
        }
    } else {
        OutlinedAction(onClick = onClick, icon = icon, label = label)
    }
}

@Composable
private fun OutlinedAction(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
private fun EmptyDownloads() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
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
                text = "在解析结果页选择视频清晰度或下载图集",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun DownloadTask.isActive(): Boolean {
    return status == DownloadStatus.RUNNING || status == DownloadStatus.QUEUED
}

private fun typeLabel(task: DownloadTask): String = when (task.request.kind) {
    DownloadKind.VIDEO -> "视频"
    DownloadKind.IMAGES -> if (task.request.aweme.kind == AwemeKind.IMAGES) "图集" else "图片"
    DownloadKind.MUSIC -> "BGM"
    DownloadKind.COVER -> "封面"
    DownloadKind.ZIP -> "ZIP"
}

private fun statusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.QUEUED -> "排队中"
    DownloadStatus.RUNNING -> "下载中"
    DownloadStatus.COMPLETED -> "已完成"
    DownloadStatus.PARTIAL -> "部分完成"
    DownloadStatus.FAILED -> "失败"
    DownloadStatus.NEEDS_REFRESH -> "需要刷新"
    DownloadStatus.CANCELLED -> "已取消"
}

@Composable
private fun statusColor(status: DownloadStatus): androidx.compose.ui.graphics.Color {
    return when (status) {
        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
        DownloadStatus.PARTIAL -> MaterialTheme.colorScheme.tertiary
        DownloadStatus.FAILED, DownloadStatus.NEEDS_REFRESH -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun progressText(task: DownloadTask): String {
    val progress = task.progress
    val items = if (progress.itemsTotal > 1) {
        " ${progress.itemsDone}/${progress.itemsTotal}"
    } else {
        ""
    }
    val size = if (progress.total != null && progress.total > 0) {
        "${formatBytes(progress.bytes)} / ${formatBytes(progress.total)}"
    } else {
        formatBytes(progress.bytes)
    }
    return "$size$items"
}

private fun formatSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0) return ""
    val mb = bytesPerSecond / (1024.0 * 1024.0)
    return if (mb >= 1) {
        String.format(java.util.Locale.CHINA, "%.1f MB/s", mb)
    } else {
        "${bytesPerSecond / 1024} KB/s"
    }
}

private fun openFile(context: Context, file: SavedFile) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(file.uri, file.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        context.startActivity(Intent.createChooser(intent, "打开文件"))
    }.onFailure {
        Toast.makeText(context, "没有可以打开该文件的应用", Toast.LENGTH_SHORT).show()
    }
}

private fun shareFile(context: Context, file: SavedFile) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType(file.mimeType)
        .putExtra(Intent.EXTRA_STREAM, file.uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        context.startActivity(Intent.createChooser(intent, "分享文件"))
    }.onFailure {
        Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
    }
}
