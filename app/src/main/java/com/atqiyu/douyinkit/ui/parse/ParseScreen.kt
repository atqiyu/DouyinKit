package com.atqiyu.douyinkit.ui.parse

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.atqiyu.douyinkit.parser.DouyinWebViewEngine
import com.atqiyu.douyinkit.parser.ParseFailure
import com.atqiyu.douyinkit.parser.ParseStage
import com.atqiyu.douyinkit.parser.ParseUiState
import com.atqiyu.douyinkit.ui.theme.BrandCyan
import com.atqiyu.douyinkit.ui.theme.BrandPink

@Composable
fun ParseScreen(
    state: ParseUiState,
    engine: DouyinWebViewEngine,
    onCancel: () -> Unit,
    onShowWebView: () -> Unit,
    onHideWebView: () -> Unit,
    onReload: () -> Unit,
    onRetry: () -> Unit,
) {
    BackHandler(onBack = onCancel)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AndroidView(
            factory = { engine.webView },
            modifier = Modifier.fillMaxSize(),
        )

        if (state.showWebView) {
            WebViewTopBar(
                state = state,
                onHide = onHideWebView,
                onReload = onReload,
                onCancel = onCancel,
            )
            if (state.needsVerification) {
                VerificationHint(
                    onHide = onHideWebView,
                    onReload = onReload,
                )
            }
        } else {
            ParseProgressOverlay(
                state = state,
                onShowWebView = onShowWebView,
                onCancel = onCancel,
            )
        }

        if (!state.running &&
            !state.showWebView &&
            (state.stage == ParseStage.FAILED || state.stage == ParseStage.CANCELLED)
        ) {
            ParseFailureOverlay(
                state = state,
                onRetry = onRetry,
                onShowWebView = onShowWebView,
                onClose = onCancel,
            )
        }
    }
}

@Composable
private fun ParseProgressOverlay(
    state: ParseUiState,
    onShowWebView: () -> Unit,
    onCancel: () -> Unit,
) {
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    val fraction = if (state.total == 0) {
        0f
    } else {
        ((state.currentIndex + (state.pageProgress / 100f)) / state.total).coerceIn(0f, 1f)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "解析作品",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "取消")
                }
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .background(
                            brush = Brush.linearGradient(listOf(BrandPink.copy(alpha = 0.18f), BrandCyan.copy(alpha = 0.18f))),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.stage == ParseStage.VERIFYING) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(38.dp),
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(54.dp),
                            strokeWidth = 4.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                Text(
                    text = state.stage.label,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.message.ifBlank { "准备中…" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(20.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (state.total > 1) {
                            "第 ${state.currentNumber}/${state.total} 个"
                        } else {
                            "本地解析，不上传链接"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatElapsed(state.elapsedMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.results.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "已完成 ${state.results.size} 个作品",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "首次解析通常需要 10–20 秒。若页面要求安全验证，展开页面完成后会自动继续。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onShowWebView,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("显示页面")
                        }
                        Button(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Text("取消")
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = { showDiagnostics = !showDiagnostics },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("解析诊断", style = MaterialTheme.typography.labelMedium)
            }
            AnimatedVisibility(visible = showDiagnostics) {
                DiagnosticsCard(state)
            }
        }
    }
}

@Composable
private fun WebViewTopBar(
    state: ParseUiState,
    onHide: () -> Unit,
    onReload: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onHide) {
                Icon(Icons.Default.VisibilityOff, contentDescription = "收起页面")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.needsVerification) "请完成页面验证" else state.stage.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Text(
                    text = state.message.ifBlank { "完成后会自动继续" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onReload) {
                Icon(Icons.Default.Refresh, contentDescription = "重新加载")
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "取消")
            }
        }
    }
}

@Composable
private fun BoxScope.VerificationHint(
    onHide: () -> Unit,
    onReload: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(14.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        tonalElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "需要安全验证",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "请在页面中完成滑块或验证码。验证成功后会自动继续解析，不需要关闭页面。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onHide,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Text("收起页面")
                }
                OutlinedButton(
                    onClick = onReload,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("重新加载")
                }
            }
        }
    }
}

@Composable
private fun ParseFailureOverlay(
    state: ParseUiState,
    onRetry: () -> Unit,
    onShowWebView: () -> Unit,
    onClose: () -> Unit,
) {
    val failure = state.failures.lastOrNull()
    val needsVerification = failure?.code == com.atqiyu.douyinkit.core.model.ErrorCode.NEEDS_VERIFICATION

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (state.stage == ParseStage.CANCELLED) Icons.Default.Close else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = if (state.stage == ParseStage.CANCELLED) "已取消" else "解析失败",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = failure?.message ?: state.message.ifBlank { "请稍后重试" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.failures.size > 1) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "共 ${state.failures.size} 个链接解析失败",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.failures.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                FailureList(state.failures)
            }

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (needsVerification) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(if (needsVerification) "验证后重试" else "重试")
                }
                OutlinedButton(
                    onClick = onShowWebView,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("打开页面")
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClose) {
                Text("关闭")
            }
        }
    }
}

@Composable
private fun FailureList(failures: List<ParseFailure>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            failures.take(4).forEachIndexed { index, failure ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                Text(
                    text = failure.input.take(80),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = failure.message,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(state: ParseUiState) {
    val page = state.pageState
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "WebView 诊断",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = buildString {
                    appendLine("stage=${state.stage.name} progress=${state.pageProgress}")
                    if (page == null) {
                        append("page=未读取")
                    } else {
                        appendLine("page=${page.href.ifBlank { "-" }}")
                        appendLine("hook=${page.hook} root=${page.hasRoot} body=${page.bodyLength}")
                        appendLine("bridge=${page.bridge.ifBlank { "-" }}")
                        appendLine("detailIds=${page.detailIds.joinToString(",")}")
                        append("bodyText=${page.text.take(300).replace('\n', ' ')}")
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}分${seconds}秒" else "${seconds}秒"
}
