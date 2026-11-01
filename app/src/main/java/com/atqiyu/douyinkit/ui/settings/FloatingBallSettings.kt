package com.atqiyu.douyinkit.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.atqiyu.douyinkit.data.prefs.AppSettings
import com.atqiyu.douyinkit.data.prefs.FloatingMode
import com.atqiyu.douyinkit.data.prefs.FloatingPanelColor
import com.atqiyu.douyinkit.data.prefs.FloatingPanelStyle
import com.atqiyu.douyinkit.di.appContainer
import com.atqiyu.douyinkit.floating.FloatingAssistantBus
import com.atqiyu.douyinkit.floating.FloatingAssistantScheduler
import com.atqiyu.douyinkit.floating.FloatingAssistantService
import com.atqiyu.douyinkit.floating.FloatingPermissions
import com.atqiyu.douyinkit.floating.FloatingPhase
import com.atqiyu.douyinkit.floating.label
import com.atqiyu.douyinkit.ui.theme.BrandCyan
import com.atqiyu.douyinkit.ui.theme.BrandPink
import kotlinx.coroutines.launch

@Composable
fun FloatingBallSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by context.appContainer.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = null)
    val floating by FloatingAssistantBus.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    var permissions by remember { mutableStateOf(readFloatingPermissions(context)) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissions = readFloatingPermissions(context) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissions = readFloatingPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val current = settings ?: AppSettings()
    val enabled = current.floatingBallEnabled
    LaunchedEffect(
        enabled,
        permissions.overlay,
        floating.enabled,
        floating.overlayReady,
        lifecycleState,
    ) {
        if (enabled && permissions.overlay && (!floating.enabled || !floating.overlayReady) &&
            lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            runCatching { FloatingAssistantService.start(context) }
            FloatingAssistantScheduler.schedule(context)
        }
    }

    fun setEnabled(next: Boolean) {
        if (!next) {
            scope.launch {
                context.appContainer.settingsRepository.setFloatingBallEnabled(false)
                FloatingAssistantService.stop(context)
                FloatingAssistantScheduler.cancel(context)
                FloatingAssistantBus.setEnabled(false)
            }
            return
        }
        if (!permissions.overlay) {
            scope.launch {
                context.appContainer.settingsRepository.setFloatingBallEnabled(true)
            }
            FloatingPermissions.openOverlaySettings(context)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        scope.launch {
            context.appContainer.settingsRepository.setFloatingBallEnabled(true)
            runCatching { FloatingAssistantService.start(context) }
                .onFailure {
                    FloatingAssistantBus.update {
                        it.copy(message = "浮球启动失败：${it.message ?: "系统限制"}")
                    }
                }
            FloatingAssistantScheduler.schedule(context)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kit 浮球",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "在其他应用上显示浮球，快速解析抖音作品",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (floating.overlayReady && floating.enabled) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = BrandCyan,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            FloatingSwitchRow(
                title = "启用浮球",
                subtitle = when {
                    !enabled -> "开启后运行前台服务，并显示一条常驻通知"
                    !permissions.overlay -> "缺少悬浮窗权限，点右侧开关重新授权"
                    floating.enabled -> "运行中 · ${floating.foreground.label} · ${floating.phase.label}"
                    else -> "已开启，等待系统恢复服务"
                },
                checked = enabled,
                onCheckedChange = ::setEnabled,
            )
            if (enabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "模式",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, bottom = 5.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings?.floatingMode == FloatingMode.STANDARD,
                        onClick = {
                            scope.launch {
                                context.appContainer.settingsRepository.setFloatingMode(
                                    FloatingMode.STANDARD,
                                )
                                FloatingAssistantBus.setMode(FloatingMode.STANDARD)
                            }
                        },
                        label = { Text("轻享模式") },
                    )
                    FilterChip(
                        selected = settings?.floatingMode == FloatingMode.AUTO,
                        onClick = {
                            scope.launch {
                                context.appContainer.settingsRepository.setFloatingMode(
                                    FloatingMode.AUTO,
                                )
                                FloatingAssistantBus.setMode(FloatingMode.AUTO)
                            }
                        },
                        label = { Text("连播模式") },
                    )
                }
                Text(
                    text = if (settings?.floatingMode == FloatingMode.AUTO) {
                        "自动点击抖音“分享 → 分享链接”，按默认值解析并下载；多个作品自动排队。"
                    } else {
                        "点浮球读取剪贴板链接，确认后回到 DouyinKit 解析。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Text(
                    text = "浮球外观",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FloatingSliderRow(
                    title = "浮球大小",
                    valueText = "${current.floatingOrbSizeDp} dp",
                    value = current.floatingOrbSizeDp.toFloat(),
                    valueRange = 38f..72f,
                    steps = 8,
                    onValueChange = { value ->
                        scope.launch {
                            context.appContainer.settingsRepository.setFloatingOrbSize(value.toInt())
                        }
                    },
                )
                FloatingSliderRow(
                    title = "浮球透明度",
                    valueText = "${(current.floatingOrbAlpha * 100).toInt()}%",
                    value = current.floatingOrbAlpha,
                    valueRange = 0.35f..1f,
                    steps = 8,
                    onValueChange = { value ->
                        scope.launch {
                            context.appContainer.settingsRepository.setFloatingOrbAlpha(value)
                        }
                    },
                )
                Text(
                    text = "浮窗背景",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        FloatingPanelStyle.GLASS to "毛玻璃",
                        FloatingPanelStyle.CLEAR to "半透明",
                        FloatingPanelStyle.SOLID to "纯色",
                    ).forEach { (style, label) ->
                        FilterChip(
                            selected = current.floatingPanelStyle == style,
                            onClick = {
                                scope.launch {
                                    context.appContainer.settingsRepository
                                        .setFloatingPanelStyle(style)
                                }
                            },
                            label = { Text(label) },
                        )
                    }
                }
                FloatingSliderRow(
                    title = "浮窗不透明度",
                    valueText = "${(current.floatingPanelOpacity * 100).toInt()}%",
                    value = current.floatingPanelOpacity,
                    valueRange = 0.2f..1f,
                    steps = 8,
                    onValueChange = { value ->
                        scope.launch {
                            context.appContainer.settingsRepository.setFloatingPanelOpacity(value)
                        }
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("背景色调", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "下载进度也会显示在浮球外圈",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FloatingPanelColor.entries.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(color.argb))
                                    .border(
                                        width = if (current.floatingPanelColor == color) {
                                            2.dp
                                        } else {
                                            1.dp
                                        },
                                        color = if (current.floatingPanelColor == color) {
                                            BrandPink
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        shape = CircleShape,
                                    )
                                    .clickable {
                                        scope.launch {
                                            context.appContainer.settingsRepository
                                                .setFloatingPanelColor(color)
                                        }
                                    },
                            )
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val blurSupported = remember {
                        context.getSystemService(WindowManager::class.java)
                            .isCrossWindowBlurEnabled
                    }
                    FloatingSliderRow(
                        title = "毛玻璃模糊",
                        valueText = if (blurSupported) {
                            if (current.floatingPanelStyle == FloatingPanelStyle.GLASS) {
                                "${current.floatingPanelBlur}"
                            } else {
                                "仅毛玻璃模式"
                            }
                        } else {
                            "系统未启用跨窗口模糊"
                        },
                        value = current.floatingPanelBlur.toFloat(),
                        valueRange = 0f..25f,
                        steps = 8,
                        enabled = blurSupported &&
                            current.floatingPanelStyle == FloatingPanelStyle.GLASS,
                        onValueChange = { value ->
                            scope.launch {
                                context.appContainer.settingsRepository
                                    .setFloatingPanelBlur(value.toInt())
                            }
                        },
                    )
                } else {
                    Text(
                        "毛玻璃模糊需要 Android 12 及以上；旧系统自动使用半透明背景。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
                FloatingSwitchRow(
                    title = "浮窗显示剪贴板内容",
                    subtitle = "关闭后只保留解析状态、进度和队列提示",
                    checked = current.floatingShowClipboard,
                    onCheckedChange = { checked ->
                        scope.launch {
                            context.appContainer.settingsRepository
                                .setFloatingShowClipboard(checked)
                        }
                    },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                FloatingSwitchRow(
                    title = "连播自动下载",
                    subtitle = "沿用下载默认值：视频默认清晰度；图集按 LivePhoto/BGM/ZIP 开关；不含封面",
                    checked = settings?.floatingAutoDownload == true,
                    enabled = settings?.floatingMode == FloatingMode.AUTO,
                    onCheckedChange = { checked ->
                        scope.launch {
                            context.appContainer.settingsRepository.setFloatingAutoDownload(checked)
                            FloatingAssistantBus.setAutoDownload(checked)
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PermissionRow(
                    title = "悬浮窗权限",
                    granted = permissions.overlay,
                    action = "去授权",
                    onClick = { FloatingPermissions.openOverlaySettings(context) },
                )
                PermissionRow(
                    title = "无障碍服务",
                    granted = permissions.accessibility,
                    action = "去开启",
                    subtitle = "连播模式需要；用于识别抖音前台并点击分享按钮，不上传页面内容",
                    onClick = { FloatingPermissions.openAccessibilitySettings(context) },
                )
                PermissionRow(
                    title = "通知权限",
                    granted = permissions.notifications,
                    action = "去设置",
                    subtitle = "前台服务需要常驻通知作为凭证",
                    onClick = { FloatingPermissions.openNotificationSettings(context) },
                )
                PermissionRow(
                    title = "电池优化",
                    granted = permissions.batteryOptimized,
                    action = "去设置",
                    subtitle = "忽略电池优化可降低浮球被系统回收的概率",
                    onClick = { FloatingPermissions.requestIgnoreBatteryOptimizations(context) },
                )
                if (settings?.floatingMode == FloatingMode.AUTO && !permissions.accessibility) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "未开启无障碍服务，连播模式无法自动点击分享",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "仅用于个人设备和已授权内容。抖音页面或风控变化时可能失效；不提供验证码破解。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            )
        }
    }
}

private data class FloatingPermissionState(
    val overlay: Boolean,
    val accessibility: Boolean,
    val notifications: Boolean,
    val batteryOptimized: Boolean,
)

private fun readFloatingPermissions(context: android.content.Context): FloatingPermissionState =
    FloatingPermissionState(
        overlay = FloatingPermissions.canDrawOverlays(context),
        accessibility = FloatingPermissions.isAccessibilityEnabled(context),
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        batteryOptimized = FloatingPermissions.isIgnoringBatteryOptimizations(context),
    )

@Composable
private fun FloatingSliderRow(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
        )
    }
}

@Composable
private fun FloatingSwitchRow(

    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    action: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = if (granted) BrandCyan else BrandPink,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = if (granted) "已授权" else "未授权",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (granted) BrandCyan else BrandPink,
                    modifier = Modifier.padding(start = 3.dp),
                )
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onClick) { Text(action) }
    }
}

private val FloatingPhase.label: String
    get() = when (this) {
        FloatingPhase.IDLE -> "待命"
        FloatingPhase.WAITING -> "等待"
        FloatingPhase.PARSING -> "解析中"
        FloatingPhase.VERIFYING -> "待验证"
        FloatingPhase.DOWNLOADING -> "下载中"
        FloatingPhase.DONE -> "已完成"
        FloatingPhase.ERROR -> "出错"
    }
