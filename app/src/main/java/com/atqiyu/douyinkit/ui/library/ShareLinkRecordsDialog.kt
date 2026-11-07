package com.atqiyu.douyinkit.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atqiyu.douyinkit.data.ShareLinkStatus
import com.atqiyu.douyinkit.data.db.ShareLinkRecordEntity
import com.atqiyu.douyinkit.di.appContainer
import com.atqiyu.douyinkit.ui.theme.BrandCyan
import com.atqiyu.douyinkit.ui.theme.BrandPink
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LinkRecordFilter(val label: String) {
    ALL("全部"),
    PENDING("未完成"),
    COMPLETED("已完成"),
    FAILED("失败"),
}

@Composable
fun ShareLinkRecordsDialog(
    onDismiss: () -> Unit,
    onProcess: (links: List<String>, background: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val repository = context.appContainer.shareLinkRepository
    val records by repository.observeAll().collectAsStateWithLifecycle(emptyList())
    var filter by remember { mutableStateOf(LinkRecordFilter.ALL) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var background by remember { mutableStateOf(false) }

    val pendingRecords = remember(records) {
        records.filter {
            val status = ShareLinkStatus.of(it.status)
            status == ShareLinkStatus.RECORDED || status == ShareLinkStatus.FAILED
        }
    }
    LaunchedEffect(records) {
        val available = pendingRecords.map { it.url }.toSet()
        selected = selected.intersect(available)
    }
    val visible = remember(records, filter) {
        when (filter) {
            LinkRecordFilter.ALL -> records
            LinkRecordFilter.PENDING -> records.filter {
                ShareLinkStatus.of(it.status) == ShareLinkStatus.RECORDED
            }

            LinkRecordFilter.COMPLETED -> records.filter {
                ShareLinkStatus.of(it.status) == ShareLinkStatus.COMPLETED
            }

            LinkRecordFilter.FAILED -> records.filter {
                ShareLinkStatus.of(it.status) == ShareLinkStatus.FAILED
            }
        }
    }
    val completedCount = records.count {
        ShareLinkStatus.of(it.status) == ShareLinkStatus.COMPLETED
    }
    val failedCount = records.count {
        ShareLinkStatus.of(it.status) == ShareLinkStatus.FAILED
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
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
                        .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = BrandPink,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "抖音作品链接分享记录",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${records.size} 条记录 · 已完成 $completedCount · 失败 $failedCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭链接记录")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LinkRecordFilter.entries.forEach { item ->
                        FilterChip(
                            selected = filter == item,
                            onClick = { filter = item },
                            label = {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandPink.copy(alpha = 0.16f),
                                selectedLabelColor = BrandPink,
                            ),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            selected = if (pendingRecords.isEmpty()) {
                                emptySet()
                            } else {
                                pendingRecords.map { it.url }.toSet()
                            }
                        },
                        enabled = pendingRecords.isNotEmpty(),
                    ) {
                        Text(
                            if (selected.size == pendingRecords.size && pendingRecords.isNotEmpty()) {
                                "取消全选"
                            } else {
                                "全选未完成"
                            },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "已选 ${selected.size} 条",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (visible.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (records.isEmpty()) {
                                    "还没有分享链接记录"
                                } else {
                                    "这个筛选下没有记录"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (records.isEmpty()) {
                                Text(
                                    text = "在“连播不解析不下载”模式下，自动分享复制的链接会记录在这里。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 36.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(visible, key = { it.url }) { record ->
                            LinkRecordCard(
                                record = record,
                                selected = record.url in selected,
                                onToggle = {
                                    selected = if (record.url in selected) {
                                        selected - record.url
                                    } else {
                                        selected + record.url
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
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "后台处理",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = if (background) {
                                "使用 Kit 浮球的隐藏解析队列，可离开 App"
                            } else {
                                "在前台解析页处理；单次最多 20 条"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = background,
                        onCheckedChange = { background = it },
                    )
                }
                Button(
                    onClick = {
                        val links = pendingRecords.map { it.url }
                            .filter { it in selected }
                        if (links.isNotEmpty()) onProcess(links, background)
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("批量处理完成（${selected.size}）")
                }
            }
        }
    }
}

@Composable
private fun LinkRecordCard(
    record: ShareLinkRecordEntity,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val status = ShareLinkStatus.of(record.status)
    val selectable = status == ShareLinkStatus.RECORDED || status == ShareLinkStatus.FAILED
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(
            1.dp,
            if (selected) BrandPink.copy(alpha = 0.65f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { if (selectable) onToggle() },
                enabled = selectable,
            )
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(status)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatLinkTime(record.lastCapturedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (record.captureCount > 1) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "获取 ${record.captureCount} 次",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = record.url,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                record.title?.takeIf { it.isNotBlank() }?.let { title ->
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                record.message?.takeIf { it.isNotBlank() }?.let { message ->
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (status == ShareLinkStatus.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: ShareLinkStatus) {
    val (label, color) = when (status) {
        ShareLinkStatus.RECORDED -> "未处理" to BrandPink
        ShareLinkStatus.PROCESSING -> "处理中" to MaterialTheme.colorScheme.tertiary
        ShareLinkStatus.COMPLETED -> "已完成" to BrandCyan
        ShareLinkStatus.FAILED -> "失败" to MaterialTheme.colorScheme.error
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = CircleShape,
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private fun formatLinkTime(timestamp: Long): String {
    return runCatching {
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
    }.getOrDefault("")
}