package com.atqiyu.douyinkit.ui.settings

import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewCompat
import com.atqiyu.douyinkit.BuildConfig
import com.atqiyu.douyinkit.data.prefs.AppSettings
import com.atqiyu.douyinkit.data.prefs.ThemeMode
import com.atqiyu.douyinkit.di.appContainer
import com.atqiyu.douyinkit.download.DownloadRegistry
import com.atqiyu.douyinkit.download.DownloadStatus
import com.atqiyu.douyinkit.ui.theme.BrandPink
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val GITHUB_DEVELOPER = "atqiyu"
private const val GITHUB_REPOSITORY = "DouyinKit"
private const val GITHUB_BASE_URL = "https://github.com/$GITHUB_DEVELOPER/$GITHUB_REPOSITORY"
private const val GITHUB_REPOSITORY_URL = GITHUB_BASE_URL
private const val GITHUB_LICENSE_URL = "$GITHUB_BASE_URL/blob/main/LICENSE"
private const val GITHUB_DEVELOPER_URL = "https://github.com/$GITHUB_DEVELOPER"

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()

    fun openLink(url: String) {
        runCatching {
            uriHandler.openUri(url)
        }.onFailure {
            Toast.makeText(context, "无法打开链接，请稍后重试", Toast.LENGTH_SHORT).show()
        }
    }
    val settings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val downloadTasks by DownloadRegistry.tasks.collectAsStateWithLifecycle()
    var showClearHistory by remember { mutableStateOf(false) }
    var showClearWebView by remember { mutableStateOf(false) }
    var showClearTemp by remember { mutableStateOf(false) }
    val webViewVersion = remember {
        runCatching {
            WebViewCompat.getCurrentWebViewPackage(context)?.let {
                "${it.versionName} (${it.packageName})"
            }
        }.getOrNull() ?: "未知"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "解析、下载与本地存储偏好",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsCard(title = "下载默认值") {
                ChoiceRow(
                    title = "默认清晰度",
                    options = listOf(
                        "4K" to 2160,
                        "1080P" to 1080,
                        "720P" to 720,
                        "540P" to 540,
                    ),
                    selected = settings.defaultQualityHeight,
                    onSelect = { height ->
                        scope.launch { container.settingsRepository.setDefaultQualityHeight(height) }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitch(
                    title = "优先 H.264",
                    subtitle = "兼容性最好；需要更小体积时可关闭",
                    checked = settings.preferH264,
                    onCheckedChange = { enabled ->
                        scope.launch { container.settingsRepository.setPreferH264(enabled) }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitch(
                    title = "图集默认包含 LivePhoto",
                    subtitle = "保存静态图的同时保存动图 MP4",
                    checked = settings.includeLivePhotos,
                    onCheckedChange = { enabled ->
                        scope.launch { container.settingsRepository.setIncludeLivePhotos(enabled) }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitch(
                    title = "图集默认下载 BGM",
                    subtitle = "有直链时直接下载，否则尝试从视频提取",
                    checked = settings.includeMusic,
                    onCheckedChange = { enabled ->
                        scope.launch { container.settingsRepository.setIncludeMusic(enabled) }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitch(
                    title = "图集默认打包 ZIP",
                    subtitle = "额外保存到 Downloads/DouyinKit",
                    checked = settings.createZip,
                    onCheckedChange = { enabled ->
                        scope.launch { container.settingsRepository.setCreateZip(enabled) }
                    },
                )
            }
        }

        item {
            SettingsCard(title = "外观") {
                ChoiceRow(
                    title = "主题",
                    options = listOf(
                        "跟随系统" to ThemeMode.SYSTEM.ordinal,
                        "浅色" to ThemeMode.LIGHT.ordinal,
                        "深色" to ThemeMode.DARK.ordinal,
                    ),
                    selected = settings.themeMode.ordinal,
                    onSelect = { index ->
                        val mode = ThemeMode.entries[index]
                        scope.launch { container.settingsRepository.setThemeMode(mode) }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitch(
                    title = "动态取色",
                    subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "使用系统壁纸颜色（Android 12+）"
                    } else {
                        "需要 Android 12 及以上"
                    },
                    checked = settings.dynamicColor,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    onCheckedChange = { enabled ->
                        scope.launch { container.settingsRepository.setDynamicColor(enabled) }
                    },
                )
            }
        }

        item {
            SettingsCard(title = "识别") {
                SettingSwitch(
                    title = "剪贴板链接识别",
                    subtitle = "回到 App 时检测剪贴板中的抖音链接，只在本机读取",
                    checked = settings.clipboardDetection,
                    onCheckedChange = { enabled ->
                        scope.launch { container.settingsRepository.setClipboardDetection(enabled) }
                    },
                )
            }
        }

        item {
            SettingsCard(title = "隐私与存储") {
                ChoiceRow(
                    title = "解析历史保留",
                    options = listOf(
                        "30 天" to 30,
                        "90 天" to 90,
                        "365 天" to 365,
                        "永久" to 0,
                    ),
                    selected = settings.historyRetentionDays,
                    onSelect = { days ->
                        scope.launch { container.settingsRepository.setHistoryRetentionDays(days) }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ActionRow(
                    title = "清除解析历史",
                    subtitle = "不会删除已保存到相册的文件",
                    onClick = { showClearHistory = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ActionRow(
                    title = "清除 Cookie 与浏览器数据",
                    subtitle = "只清除本机 WebView 会话，用于重新验证",
                    onClick = { showClearWebView = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ActionRow(
                    title = "清理下载临时文件",
                    subtitle = "清理未完成下载的中间文件，不影响已保存作品",
                    onClick = { showClearTemp = true },
                )
            }
        }

        item {
            SettingsCard(title = "关于") {
                SettingTextRow("版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                SettingTextRow("开发者", "atqiyu")
                SettingTextRow("WebView", webViewVersion)
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LinkRow(
                    title = "GitHub 仓库",
                    subtitle = "github.com/atqiyu/DouyinKit",
                    onClick = { openLink(GITHUB_REPOSITORY_URL) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LinkRow(
                    title = "开源许可证",
                    subtitle = "MIT License · 查看完整许可证",
                    onClick = { openLink(GITHUB_LICENSE_URL) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LinkRow(
                    title = "开发者主页",
                    subtitle = "github.com/atqiyu",
                    onClick = { openLink(GITHUB_DEVELOPER_URL) },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "DouyinKit 仅用于个人学习与已授权内容的本地备份。解析依赖抖音公开页面，可能随页面改版失效；请遵守平台条款与版权规定，不要用于批量抓取或商业分发。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Copyright © 2026 atqiyu. 源代码以 MIT License 开源；作品内容版权归原作者及平台所有，不随代码许可证授予。本项目与抖音/字节跳动无隶属关系。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }
        }
    }

    if (showClearHistory) {
        ConfirmDialog(
            title = "清除解析历史",
            message = "将删除本机全部解析记录、收藏与标签，已下载文件不受影响。",
            onConfirm = {
                showClearHistory = false
                scope.launch {
                    container.historyRepository.clearAll()
                    Toast.makeText(context, "已清除解析历史", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showClearHistory = false },
        )
    }
    if (showClearWebView) {
        ConfirmDialog(
            title = "清除 Cookie 与浏览器数据",
            message = "下次解析可能需要重新完成安全验证。",
            onConfirm = {
                showClearWebView = false
                CookieManager.getInstance().removeAllCookies {
                    CookieManager.getInstance().flush()
                }
                WebStorage.getInstance().deleteAllData()
                Toast.makeText(context, "已清除浏览器数据", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showClearWebView = false },
        )
    }
    if (showClearTemp) {
        ConfirmDialog(
            title = "清理下载临时文件",
            message = "未完成的下载需要重新开始，已保存到相册的文件不受影响。",
            onConfirm = {
                showClearTemp = false
                val hasActiveDownload = downloadTasks.any {
                    it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED
                }
                if (hasActiveDownload) {
                    Toast.makeText(
                        context,
                        "有下载任务进行中，请先取消或等待完成",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val root = context.getExternalFilesDir(null)?.resolve("downloads")
                            root?.deleteRecursively()
                        }
                        DownloadRegistry.clearFinished()
                        Toast.makeText(context, "已清理临时文件", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showClearTemp = false },
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    options: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (label, value) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandPink.copy(alpha = 0.16f),
                        selectedLabelColor = BrandPink,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClick) {
            Text("清理")
        }
    }
}

@Composable
private fun LinkRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp).size(18.dp),
        )
    }
}

@Composable
private fun SettingTextRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
