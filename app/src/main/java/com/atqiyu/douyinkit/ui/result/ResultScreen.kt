package com.atqiyu.douyinkit.ui.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.core.model.ParseSource
import com.atqiyu.douyinkit.core.model.VideoGear
import com.atqiyu.douyinkit.core.util.formatBytes
import com.atqiyu.douyinkit.core.util.formatCount
import com.atqiyu.douyinkit.core.util.formatCreateTime
import com.atqiyu.douyinkit.core.util.formatDuration
import com.atqiyu.douyinkit.parser.ParseBatchResult
import com.atqiyu.douyinkit.ui.theme.BrandCyan
import com.atqiyu.douyinkit.ui.theme.BrandPink

@Composable
fun ResultScreen(
    result: ParseBatchResult,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "解析结果",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (result.results.size > 1) {
                                "${result.results.size} 个作品"
                            } else {
                                "本地解析"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(result.results, key = { it.awemeId }) { aweme ->
                ResultCard(aweme)
            }
            if (result.failures.isNotEmpty()) {
                item {
                    FailureSummary(result)
                }
            }
        }
    }
}

@Composable
private fun ResultCard(aweme: Aweme) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (aweme.limited) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "兼容模式：当前结果可能包含带水印地址，且缺少部分清晰度信息",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Row {
                Box(
                    modifier = Modifier
                        .width(118.dp)
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    val cover = aweme.primaryCoverUrl
                    if (cover != null) {
                        AsyncImage(
                            model = cover,
                            contentDescription = aweme.displayTitle,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
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
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Badge(
                            text = if (aweme.kind == AwemeKind.IMAGES) "图文" else "视频",
                            color = if (aweme.kind == AwemeKind.IMAGES) BrandCyan else BrandPink,
                        )
                        Badge(
                            text = when (aweme.parseSource) {
                                ParseSource.DETAIL -> "详情接口"
                                ParseSource.PAGE_MODEL -> "页面模型"
                                ParseSource.MIXED -> "详情 + 页面"
                                ParseSource.SSR -> "SSR"
                                ParseSource.SHARE_FALLBACK -> "兼容模式"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = aweme.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = aweme.author.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat("赞", aweme.stats.diggCount)
                        Stat("评", aweme.stats.commentCount)
                        Stat("藏", aweme.stats.collectCount)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            when (aweme.kind) {
                AwemeKind.VIDEO -> VideoSection(aweme)
                AwemeKind.IMAGES -> ImageSection(aweme)
            }

            aweme.music?.let { music ->
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = BrandCyan,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = music.displayTitle,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${music.author.ifBlank { "未知作者" }} · ${
                                formatDuration(music.durationMs)
                            } · ${
                                if (music.hasDirectAudio) "有音频直链" else "可从视频提取"
                            }",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            val createdText = formatCreateTime(aweme.createTimeSeconds)
            Text(
                text = "作品 ID ${aweme.awemeId}" +
                    if (createdText.isNotBlank()) " · $createdText" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun VideoSection(aweme: Aweme) {
    val video = aweme.video ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "清晰度",
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "${video.gears.size} 档 · ${formatDuration(video.durationMs)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(8.dp))
    if (video.gears.isEmpty()) {
        Text(
            text = "暂无视频档位",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        video.gears.sortedWith(
            compareByDescending<VideoGear> { it.height ?: 0 }.thenByDescending { it.bitrateBps ?: 0 },
        ).take(6).forEach { gear ->
            GearRow(gear)
            Spacer(Modifier.height(6.dp))
        }
        if (video.gears.size > 6) {
            Text(
                text = "另有 ${video.gears.size - 6} 个档位",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GearRow(gear: VideoGear) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = gear.resolutionLabel,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(58.dp),
        )
        Text(
            text = gear.codecLabel,
            style = MaterialTheme.typography.labelMedium,
            color = if (gear.codec == com.atqiyu.douyinkit.core.model.VideoCodec.H265) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.width(52.dp),
        )
        Text(
            text = gear.fps?.takeIf { it > 0 }?.let { "${it}fps" } ?: "-",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(46.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = formatBytes(gear.sizeBytes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImageSection(aweme: Aweme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "图集",
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "${aweme.images.size} 张 · ${aweme.images.count { it.isLivePhoto }} 个动图",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(aweme.images, key = { it.index }) { image ->
            Box(
                modifier = Modifier
                    .size(width = 92.dp, height = 120.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                val url = image.urls.firstOrNull()
                if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = "第 ${image.index + 1} 张",
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.76f),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                if (image.isLivePhoto) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp),
                        color = BrandPink,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                    ) {
                        Text(
                            text = "动图",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Long?) {
    Text(
        text = "$label ${formatCount(value)}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = CircleShape,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun FailureSummary(result: ParseBatchResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "有 ${result.failures.size} 个链接解析失败",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(6.dp))
            result.failures.take(3).forEach { failure ->
                Text(
                    text = failure.message,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
