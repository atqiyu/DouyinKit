package com.atqiyu.douyinkit.ui.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atqiyu.douyinkit.core.util.formatBytes
import com.atqiyu.douyinkit.core.util.formatSpeed
import com.atqiyu.douyinkit.download.DownloadKind
import com.atqiyu.douyinkit.download.DownloadRegistry
import com.atqiyu.douyinkit.download.DownloadService
import com.atqiyu.douyinkit.download.DownloadStatus
import com.atqiyu.douyinkit.download.DownloadTask
import com.atqiyu.douyinkit.ui.downloads.openSavedFile

@Composable
fun DownloadStatusSection(
    awemeId: String,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val tasks by DownloadRegistry.tasks.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val activeStatuses = setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING)
    val relevant = tasks
        .filter { task ->
            task.request.aweme.awemeId == awemeId &&
                (
                    task.status in activeStatuses ||
                        task.status == DownloadStatus.FAILED ||
                        task.status == DownloadStatus.NEEDS_REFRESH ||
                        (
                            (task.status == DownloadStatus.COMPLETED ||
                                task.status == DownloadStatus.PARTIAL) &&
                                now - task.createdAt < COMPLETED_VISIBLE_MS
                            )
                    )
        }
        .takeLast(3)

    AnimatedVisibility(
        visible = relevant.isNotEmpty(),
        enter = fadeIn(tween(220)) + expandVertically(tween(220)),
        exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            relevant.forEach { task ->
                DownloadStatusCard(
                    task = task,
                    onCancel = { DownloadService.cancel(context, task.id) },
                    onRetry = {
                        runCatching {
                            DownloadService.enqueue(context, task.request)
                        }.onFailure {
                            onMessage(it.message ?: "重试失败")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DownloadStatusCard(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val active = task.status == DownloadStatus.RUNNING || task.status == DownloadStatus.QUEUED
    val failed = task.status == DownloadStatus.FAILED || task.status == DownloadStatus.NEEDS_REFRESH
    val progress = task.progress
    val fraction = progress.total?.takeIf { it > 0 }?.let {
        (progress.bytes.toFloat() / it).coerceIn(0f, 1f)
    }
    val animatedFraction by animateFloatAsState(
        targetValue = fraction ?: 0f,
        animationSpec = tween(450),
        label = "download-progress",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        active -> Icons.Default.Download
                        failed -> Icons.Default.ErrorOutline
                        else -> Icons.Default.CheckCircle
                    },
                    contentDescription = null,
                    tint = when {
                        active -> MaterialTheme.colorScheme.secondary
                        failed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.secondary
                    },
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = task.typeLabelWithQuality(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (active) {
                    IconButton(onClick = onCancel, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "取消下载",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            if (active) {
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = { animatedFraction },
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                    )
                }
                Spacer(Modifier.height(5.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = progressSummary(task, fraction),
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
            } else if (failed) {
                Text(
                    text = if (task.status == DownloadStatus.NEEDS_REFRESH) {
                        "链接已过期，请点右上角刷新后重试"
                    } else {
                        task.message.ifBlank { "下载失败" }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.status == DownloadStatus.FAILED) {
                    TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("重试")
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (task.status == DownloadStatus.PARTIAL) {
                            "部分完成：${task.message}"
                        } else {
                            "已保存到相册"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    task.files.firstOrNull()?.let { file ->
                        TextButton(onClick = { openSavedFile(context, file) }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("打开")
                        }
                    }
                }
            }
        }
    }
}

private fun DownloadTask.typeLabelWithQuality(): String {
    val gear = request.gear
    return when (request.kind) {
        DownloadKind.VIDEO -> buildString {
            append("视频")
            gear?.let {
                append(" · ${it.resolutionLabel}")
                if (it.codec != com.atqiyu.douyinkit.core.model.VideoCodec.UNKNOWN) {
                    append(" · ${it.codecLabel}")
                }
            }
        }

        DownloadKind.IMAGES -> "图集"
        DownloadKind.MUSIC -> "BGM"
        DownloadKind.COVER -> "封面"
        DownloadKind.ZIP -> "ZIP"
    }
}

private fun progressSummary(task: DownloadTask, fraction: Float?): String {
    val progress = task.progress
    if (fraction != null) {
        val percent = (fraction * 100).toInt()
        return "$percent% · ${formatBytes(progress.bytes)} / ${formatBytes(progress.total)}"
    }
    val items = if (progress.itemsTotal > 1) {
        " ${progress.itemsDone}/${progress.itemsTotal}"
    } else {
        ""
    }
    return "${formatBytes(progress.bytes)}$items"
}

private const val COMPLETED_VISIBLE_MS = 3 * 60 * 1000L
