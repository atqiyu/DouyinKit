package com.atqiyu.douyinkit.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atqiyu.douyinkit.BuildConfig
import com.atqiyu.douyinkit.backup.BackupException
import com.atqiyu.douyinkit.backup.BackupPhase
import com.atqiyu.douyinkit.backup.BackupProgress
import com.atqiyu.douyinkit.backup.ConfigBackup
import com.atqiyu.douyinkit.backup.HistoryBackup
import com.atqiyu.douyinkit.di.appContainer
import com.atqiyu.douyinkit.floating.FloatingAssistantScheduler
import com.atqiyu.douyinkit.floating.FloatingAssistantService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress = remember { MutableStateFlow<BackupProgress?>(null) }
    val currentProgress by progress.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<BackupDialog?>(null) }
    val version = BuildConfig.VERSION_NAME

    fun run(
        title: String,
        block: suspend () -> String,
    ) {
        if (busy) return
        busy = true
        dialog = BackupDialog(title = title, message = "准备中…", running = true)
        scope.launch {
            try {
                val message = block()
                dialog = BackupDialog(
                    title = title,
                    message = message,
                    running = false,
                )
            } catch (error: Exception) {
                dialog = BackupDialog(
                    title = title,
                    message = if (error is BackupException) {
                        error.message ?: "备份操作失败"
                    } else {
                        "备份操作失败：${error.message ?: "未知错误"}"
                    },
                    running = false,
                    isError = true,
                )
            } finally {
                busy = false
                progress.value = null
            }
        }
    }

    val exportConfig = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            run("导出配置") {
                ConfigBackup.export(context, uri)
                "配置已导出，包含当前全部设置和版本信息（$version）。"
            }
        }
    }
    val importConfig = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            run("导入配置") {
                val (sourceCode, sourceVersion) = ConfigBackup.import(context, uri)
                val settings = context.appContainer.settingsRepository.settings.first()
                if (settings.floatingBallEnabled) {
                    runCatching { FloatingAssistantService.start(context) }
                    FloatingAssistantScheduler.schedule(context)
                } else {
                    FloatingAssistantService.stop(context)
                    FloatingAssistantScheduler.cancel(context)
                }
                "配置已导入（来源 ${sourceVersion.ifBlank { "未知版本" }}，" +
                    "versionCode $sourceCode）。设置已立即生效。"
            }
        }
    }
    val exportHistory = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            run("导出解析与下载记录") {
                val count = HistoryBackup.export(context, uri) { progress.value = it }
                "已导出 $count 条记录。压缩包包含 manifest.json、awemes.jsonl 和 downloads.jsonl；" +
                    "只包含记录和文件位置，不包含媒体文件本身。"
            }
        }
    }
    val importHistory = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            run("导入解析与下载记录") {
                val result = HistoryBackup.import(context, uri) { progress.value = it }
                buildString {
                    append("导入完成：解析记录 ${result.historyImported} 条，")
                    append("下载记录 ${result.downloadsImported} 条，")
                    append("跳过 ${result.skipped} 条。")
                    if (result.errors.isNotEmpty()) {
                        append("\n\n问题：\n")
                        append(result.errors.take(6).joinToString("\n"))
                    }
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "备份与恢复",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "配置与记录分开保存；记录以 ZIP + JSONL 流式处理，适合较多历史。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
            )
            BackupActionRow(
                title = "导出当前配置",
                subtitle = "JSON，包含全部设置、schema 和应用版本号",
                enabled = !busy,
                onClick = {
                    exportConfig.launch("DouyinKit-config-v$version.json")
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            BackupActionRow(
                title = "导入当前配置",
                subtitle = "兼容缺少字段的旧配置；更高 schema 会被拒绝",
                enabled = !busy,
                onClick = { importConfig.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            BackupActionRow(
                title = "导出解析与下载记录",
                subtitle = "ZIP 压缩包；包含作品信息和本地文件 URI/路径，不包含媒体文件",
                enabled = !busy,
                onClick = {
                    exportHistory.launch("DouyinKit-history-v$version.zip")
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            BackupActionRow(
                title = "导入解析与下载记录",
                subtitle = "同名作品/任务会覆盖；跨设备导入后本地文件路径可能失效",
                enabled = !busy,
                onClick = {
                    importHistory.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                },
            )
            if (currentProgress != null) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { currentProgress?.fraction ?: 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = currentProgress?.message.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    dialog?.let { state ->
        AlertDialog(
            onDismissRequest = {
                if (!state.running) dialog = null
            },
            title = { Text(state.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.running) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                currentProgress?.message ?: state.message,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        currentProgress?.fraction?.let { fraction ->
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            },
            confirmButton = {
                if (!state.running) {
                    TextButton(onClick = { dialog = null }) { Text("好") }
                }
            },
        )
    }
}

private data class BackupDialog(
    val title: String,
    val message: String,
    val running: Boolean,
    val isError: Boolean = false,
)

@Composable
private fun BackupActionRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClick, enabled = enabled) { Text("选择") }
    }
}
