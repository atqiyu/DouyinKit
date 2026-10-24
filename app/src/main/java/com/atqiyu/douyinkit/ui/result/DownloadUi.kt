package com.atqiyu.douyinkit.ui.result

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.VideoCodec
import com.atqiyu.douyinkit.core.model.VideoGear
import com.atqiyu.douyinkit.core.util.formatBytes
import com.atqiyu.douyinkit.data.prefs.AppSettings
import com.atqiyu.douyinkit.di.appContainer
import com.atqiyu.douyinkit.download.DownloadKind
import com.atqiyu.douyinkit.download.DownloadRequest
import com.atqiyu.douyinkit.download.DownloadService
import com.atqiyu.douyinkit.download.VideoGearSelector

@Composable
fun rememberDownloadRequester(onMessage: (String) -> Unit): (DownloadRequest) -> Unit {
    val context = LocalContext.current
    val currentMessage by rememberUpdatedState(onMessage)
    var pendingRequest by remember { mutableStateOf<DownloadRequest?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val request = pendingRequest ?: return@rememberLauncherForActivityResult
        pendingRequest = null
        val storageDenied = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            results[Manifest.permission.WRITE_EXTERNAL_STORAGE] == false
        if (storageDenied) {
            currentMessage("需要存储权限才能保存到相册")
        } else {
            startDownload(context, request, currentMessage)
        }
    }

    return remember(context) {
        { request ->
            val missing = buildList {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (missing.isEmpty()) {
                startDownload(context, request, currentMessage)
            } else {
                pendingRequest = request
                permissionLauncher.launch(missing.toTypedArray())
            }
        }
    }
}

private fun startDownload(
    context: android.content.Context,
    request: DownloadRequest,
    onMessage: (String) -> Unit,
) {
    try {
        DownloadService.enqueue(context, request)
        onMessage("已加入下载队列")
    } catch (error: Exception) {
        onMessage("无法开始下载：${error.message ?: "未知错误"}")
    }
}

@Composable
fun ResultActions(
    aweme: Aweme,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val settings by context.appContainer.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val enqueue = rememberDownloadRequester(onMessage)
    var showQualitySheet by remember { mutableStateOf(false) }
    var showImageOptions by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        when (aweme.kind) {
            com.atqiyu.douyinkit.core.model.AwemeKind.VIDEO -> {
                Button(
                    onClick = { showQualitySheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = aweme.video?.gears?.any { it.hasDownloadSource } == true,
                ) {
                    Text("选择清晰度并下载")
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (aweme.music != null) {
                        OutlinedButton(
                            onClick = {
                                enqueue(DownloadRequest(aweme = aweme, kind = DownloadKind.MUSIC))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("下载 BGM")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            enqueue(DownloadRequest(aweme = aweme, kind = DownloadKind.COVER))
                        },
                        modifier = Modifier.weight(1f),
                        enabled = aweme.primaryCoverUrl != null,
                    ) {
                        Text("保存封面")
                    }
                }
            }

            com.atqiyu.douyinkit.core.model.AwemeKind.IMAGES -> {
                Button(
                    onClick = { showImageOptions = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = aweme.images.any { it.hasDownloadSource },
                ) {
                    Text("下载全部图片（${aweme.images.size}）")
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (aweme.music != null) {
                        OutlinedButton(
                            onClick = {
                                enqueue(DownloadRequest(aweme = aweme, kind = DownloadKind.MUSIC))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("下载 BGM")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            enqueue(DownloadRequest(aweme = aweme, kind = DownloadKind.COVER))
                        },
                        modifier = Modifier.weight(1f),
                        enabled = aweme.primaryCoverUrl != null,
                    ) {
                        Text("保存封面")
                    }
                }
            }
        }
    }

    if (showQualitySheet) {
        VideoQualitySheet(
            aweme = aweme,
            preferredHeight = settings.defaultQualityHeight,
            preferH264 = settings.preferH264,
            onDismiss = { showQualitySheet = false },
            onDownload = { gear ->
                showQualitySheet = false
                enqueue(DownloadRequest(aweme = aweme, kind = DownloadKind.VIDEO, gear = gear))
            },
        )
    }

    if (showImageOptions) {
        ImageDownloadOptionsDialog(
            aweme = aweme,
            defaults = settings,
            onDismiss = { showImageOptions = false },
            onStart = { includeLive, includeMusic, createZip ->
                showImageOptions = false
                enqueue(
                    DownloadRequest(
                        aweme = aweme,
                        kind = DownloadKind.IMAGES,
                        includeLivePhotos = includeLive,
                        includeMusic = includeMusic,
                        createZip = createZip,
                    ),
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoQualitySheet(
    aweme: Aweme,
    preferredHeight: Int,
    preferH264: Boolean,
    onDismiss: () -> Unit,
    onDownload: (VideoGear) -> Unit,
) {
    val gears = remember(aweme) {
        aweme.video?.gears
            .orEmpty()
            .filter { it.hasDownloadSource }
            .sortedWith(
                compareByDescending<VideoGear> { it.height ?: 0 }
                    .thenByDescending { it.bitrateBps ?: 0 },
            )
    }
    var selected by remember(aweme, preferredHeight, preferH264) {
        mutableStateOf(
            VideoGearSelector.select(
                aweme = aweme,
                preferH264 = preferH264,
                preferredHeight = preferredHeight,
            ),
        )
    }
    val hasH265 = gears.any { it.codec == VideoCodec.H265 && it == selected }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = "选择清晰度",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "优先无水印直链；H.264 兼容性最好",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (gears.isEmpty()) {
                Text(
                    text = "没有可用的视频档位",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 430.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(gears, key = { "${it.name}_${it.index}" }) { gear ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = gear }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected == gear,
                                onClick = { selected = gear },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${gear.resolutionLabel} · ${gear.codecLabel}" +
                                        if (gear.fps != null && gear.fps > 0) " · ${gear.fps}fps" else "",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = buildString {
                                        append(formatBytes(gear.sizeBytes))
                                        gear.bitrateBps?.takeIf { it > 0 }?.let {
                                            append(" · ${it / 1000} kbps")
                                        }
                                        if (gear.urls.isEmpty() && gear.playApi == null) {
                                            append(" · 无地址")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            if (hasH265) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "H.265 体积更小，但部分设备相册无法播放；需要兼容性请选 H.264。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { selected?.let(onDownload) },
                modifier = Modifier.fillMaxWidth(),
                enabled = selected != null,
            ) {
                Text("开始下载")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ImageDownloadOptionsDialog(
    aweme: Aweme,
    defaults: AppSettings,
    onDismiss: () -> Unit,
    onStart: (includeLive: Boolean, includeMusic: Boolean, createZip: Boolean) -> Unit,
) {
    var includeLive by remember { mutableStateOf(defaults.includeLivePhotos) }
    var includeMusic by remember {
        mutableStateOf(defaults.includeMusic && aweme.music != null)
    }
    var createZip by remember { mutableStateOf(defaults.createZip) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("下载图集") },
        text = {
            Column {
                Text(
                    text = "${aweme.images.size} 张图片，其中 ${aweme.images.count { it.isLivePhoto }} 个 LivePhoto",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OptionRow(
                    checked = includeLive,
                    onCheckedChange = { includeLive = it },
                    title = "包含 LivePhoto 动图",
                    subtitle = "同时保存静态图与动图 MP4",
                )
                OptionRow(
                    checked = includeMusic,
                    onCheckedChange = { includeMusic = it },
                    title = "同时下载 BGM",
                    subtitle = if (aweme.music != null) "保存到 Music/DouyinKit" else "该作品没有 BGM",
                    enabled = aweme.music != null,
                )
                OptionRow(
                    checked = createZip,
                    onCheckedChange = { createZip = it },
                    title = "打包为 ZIP",
                    subtitle = "保存到 Downloads/DouyinKit，包含 info.json",
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onStart(includeLive, includeMusic, createZip) },
            ) {
                Text("开始下载")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun OptionRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
