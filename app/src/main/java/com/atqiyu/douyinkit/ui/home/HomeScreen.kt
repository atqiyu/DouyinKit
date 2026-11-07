package com.atqiyu.douyinkit.ui.home

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.atqiyu.douyinkit.R
import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.di.appContainer
import com.atqiyu.douyinkit.ui.components.AppMark
import com.atqiyu.douyinkit.ui.theme.BrandCyan
import com.atqiyu.douyinkit.ui.theme.BrandPink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SAMPLE_SHARE_TEXT =
    "（示例）请替换为抖音真实分享链接：https://v.douyin.com/EXAMPLE123/"

@Composable
fun HomeScreen(
    initialText: String = "",
    onParse: (String) -> Unit = {},
    onOpenRecent: (Aweme) -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    clipboardLink: String? = null,
    onDismissClipboard: () -> Unit = {},
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val historyRepository = context.applicationContext.appContainer.historyRepository
    val recentEntities by historyRepository.observeRecent(6)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val recentAwemes = remember(recentEntities) {
        recentEntities.mapNotNull { historyRepository.decode(it) }
    }
    var lastShownClipboardLink by remember { mutableStateOf<String?>(null) }
    val bannerLink = clipboardLink ?: lastShownClipboardLink
    LaunchedEffect(clipboardLink) {
        clipboardLink?.let { lastShownClipboardLink = it }
    }
    LaunchedEffect(initialText) {
        if (initialText.isNotBlank()) {
            input = initialText
        }
    }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(70)
        entered = true
    }
    val heroAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "home-hero-alpha",
    )
    val heroShift by animateFloatAsState(
        targetValue = if (entered) 0f else 18f,
        animationSpec = tween(460, easing = FastOutSlowInEasing),
        label = "home-hero-shift",
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = contentWindowInsets,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            HeroHeader(
                modifier = Modifier.graphicsLayer {
                    alpha = heroAlpha
                    translationY = heroShift
                },
            )
            Spacer(Modifier.height(10.dp))
            FeatureCards(visible = entered)
            Spacer(Modifier.height(10.dp))

            AnimatedVisibility(
                visible = clipboardLink != null,
                enter = fadeIn(tween(160)) +
                    expandVertically(tween(220), expandFrom = Alignment.Top),
                exit = fadeOut(tween(120)) +
                    shrinkVertically(tween(180), shrinkTowards = Alignment.Top),
            ) {
                Column {
                    bannerLink?.let { link ->
                        ClipboardBanner(
                            link = link,
                            onParseNow = {
                                input = link
                                onDismissClipboard()
                                onParse(link)
                            },
                            onDismiss = onDismissClipboard,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            InputCard(
                value = input,
                onValueChange = { input = it },
                onClear = { input = "" },
                onPaste = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val pasted = clipboard.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                    if (pasted.isNullOrBlank()) {
                        scope.launch { snackbarHostState.showSnackbar("剪贴板里没有文本") }
                    } else {
                        input = pasted
                    }
                },
                onSample = {
                    input = SAMPLE_SHARE_TEXT
                    scope.launch {
                        snackbarHostState.showSnackbar("示例链接无法解析，请替换为真实分享链接")
                    }
                },
            )

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (input.isBlank()) {
                        scope.launch { snackbarHostState.showSnackbar("先粘贴一个抖音链接") }
                    } else {
                        onParse(input)
                    }
                },
                enabled = input.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.parse), style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(28.dp))
            RecentSection(
                recent = recentAwemes,
                onOpenRecent = onOpenRecent,
                onViewAll = onOpenLibrary,
            )
            Spacer(Modifier.height(24.dp))
            Disclaimer()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ClipboardBanner(
    link: String,
    onParseNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "检测到抖音链接",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = link,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onParseNow) {
                Text("解析")
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "关闭")
            }
        }
    }
}

@Composable
private fun HeroHeader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        BrandPink.copy(alpha = 0.12f),
                        BrandCyan.copy(alpha = 0.09f),
                        MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ),
            )
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppMark(size = 46.dp, glow = true)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                    shape = CircleShape,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(BrandCyan, CircleShape),
                        )
                        Text(
                            text = "纯本地",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeatureCards(visible: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FeatureTile(
            icon = Icons.Default.PlayCircle,
            label = "视频",
            caption = "多清晰度",
            accent = BrandPink,
            index = 0,
            visible = visible,
            modifier = Modifier.weight(1f),
        )
        FeatureTile(
            icon = Icons.Default.PhotoLibrary,
            label = "图集",
            caption = "图片动图",
            accent = BrandCyan,
            index = 1,
            visible = visible,
            modifier = Modifier.weight(1f),
        )
        FeatureTile(
            icon = Icons.Default.Download,
            label = "无水印",
            caption = "原画保存",
            accent = MaterialTheme.colorScheme.secondary,
            index = 2,
            visible = visible,
            modifier = Modifier.weight(1f),
        )
        FeatureTile(
            icon = Icons.Default.MusicNote,
            label = "BGM",
            caption = "音频提取",
            accent = MaterialTheme.colorScheme.tertiary,
            index = 3,
            visible = visible,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FeatureTile(
    icon: ImageVector,
    label: String,
    caption: String,
    accent: androidx.compose.ui.graphics.Color,
    index: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(index * 55L)
            shown = true
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "feature-alpha-$index",
    )
    val shift by animateFloatAsState(
        targetValue = if (shown) 0f else 16f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "feature-shift-$index",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                translationY = shift
            }
            .height(80.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.09f))
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.16f),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(accent.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun InputCard(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onPaste: () -> Unit,
    onSample: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "分享链接",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSample) {
                    Text("填入示例", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringResource(R.string.input_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                minLines = 3,
                maxLines = 8,
                shape = MaterialTheme.shapes.large,
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (value.isNotEmpty()) {
                            IconButton(
                                onClick = onClear,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "清空输入",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        IconButton(
                            onClick = onPaste,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = stringResource(R.string.paste),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun RecentSection(
    recent: List<Aweme>,
    onOpenRecent: (Aweme) -> Unit,
    onViewAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.recent),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onViewAll) {
            Text("查看全部", style = MaterialTheme.typography.labelMedium)
        }
    }
    Spacer(Modifier.height(8.dp))

    if (recent.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = stringResource(R.string.recent_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "解析记录只保存在本机",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    )
                }
            }
        }
    } else {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(recent, key = { it.awemeId }) { aweme ->
                RecentCard(aweme = aweme, onClick = { onOpenRecent(aweme) })
            }
        }
    }
}

@Composable
private fun RecentCard(
    aweme: Aweme,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(154.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                val cover = aweme.primaryCoverUrl
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = aweme.displayTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = if (aweme.kind == AwemeKind.IMAGES) {
                            Icons.Default.PhotoLibrary
                        } else {
                            Icons.Default.PlayCircle
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                    color = if (aweme.kind == AwemeKind.IMAGES) BrandCyan else BrandPink,
                    contentColor = if (aweme.kind == AwemeKind.IMAGES) {
                        MaterialTheme.colorScheme.onSecondary
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                    shape = CircleShape,
                ) {
                    Text(
                        text = if (aweme.kind == AwemeKind.IMAGES) "图文" else "视频",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = aweme.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = aweme.author.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Disclaimer() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .padding(top = 2.dp)
                .size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.local_disclaimer),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        )
    }
}
