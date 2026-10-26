package com.atqiyu.douyinkit.ui.downloads

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.core.util.formatBytes
import com.atqiyu.douyinkit.core.util.formatCount
import com.atqiyu.douyinkit.core.util.formatCreateTime
import com.atqiyu.douyinkit.download.DownloadService
import com.atqiyu.douyinkit.download.DownloadStatus
import com.atqiyu.douyinkit.download.DownloadTask
import com.atqiyu.douyinkit.ui.preview.AudioPreviewDialog
import com.atqiyu.douyinkit.ui.preview.AutoLoopingAudio
import com.atqiyu.douyinkit.ui.preview.DouyinVideoPlayer
import com.atqiyu.douyinkit.ui.preview.InlineLoopingVideo
import com.atqiyu.douyinkit.ui.preview.PreviewSystemBars
import com.atqiyu.douyinkit.ui.theme.BrandCyan
import com.atqiyu.douyinkit.ui.theme.BrandPink
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorkPreviewDialog(
    group: DownloadGroup,
    onDismiss: () -> Unit,
    onRemoveGroup: (DownloadGroup) -> Unit,
    initialFileUri: String? = null,
) {
    val context = LocalContext.current
    var selectedUri by remember(group.awemeId) {
        mutableStateOf(initialFileUri ?: group.featuredMedia?.file?.uri?.toString())
    }
    val mediaUris = remember(group.mediaItems) {
        group.mediaItems.map { it.file.uri.toString() }
    }
    LaunchedEffect(group.awemeId, mediaUris, initialFileUri) {
        val requested = initialFileUri?.takeIf { it in mediaUris }
        if (requested != null) {
            selectedUri = requested
        } else if (selectedUri == null || selectedUri !in mediaUris) {
            selectedUri = group.featuredMedia?.file?.uri?.toString()
        }
    }

    val selected = group.mediaItems.firstOrNull {
        it.file.uri.toString() == selectedUri
    } ?: group.featuredMedia
    val imagePages = group.imagePages
    val initialImagePage = imagePages.indexOfFirst {
        it.image.file.uri.toString() == selectedUri
    }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialImagePage) { imagePages.size }
    LaunchedEffect(selected?.kind, initialImagePage, imagePages.size) {
        if (selected?.kind == GroupMediaKind.IMAGE && imagePages.isNotEmpty()) {
            pagerState.scrollToPage(initialImagePage.coerceIn(0, imagePages.lastIndex))
        }
    }
    val currentImagePage = imagePages.getOrNull(pagerState.currentPage)
    val displayItem = if (selected?.kind == GroupMediaKind.IMAGE) {
        currentImagePage?.image ?: selected
    } else {
        selected
    }
    var showDetails by remember(group.awemeId) {
        mutableStateOf(group.mediaItems.isEmpty())
    }
    var audioPreview by remember { mutableStateOf<GroupMediaItem?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    var infoVisible by remember(group.awemeId) { mutableStateOf(true) }
    var playingLiveUri by remember(group.awemeId) { mutableStateOf<String?>(null) }
    val topBarAlpha by animateFloatAsState(
        targetValue = if (infoVisible) 1f else 0.35f,
        animationSpec = tween(durationMillis = 220),
        label = "preview-top-bar-alpha",
    )
    val imageBgmItem = group.audioItems.firstOrNull()
    val imageBgmUrl = if (group.aweme.kind == AwemeKind.IMAGES) {
        imageBgmItem?.file?.uri?.toString()
            ?: group.aweme.music?.playUrls?.firstOrNull()
    } else {
        null
    }
    val imageBgmEnabled = group.aweme.kind == AwemeKind.IMAGES &&
        selected?.kind == GroupMediaKind.IMAGE &&
        audioPreview == null

    LaunchedEffect(pagerState.currentPage) {
        playingLiveUri = null
    }

    fun select(item: GroupMediaItem) {
        selectedUri = item.file.uri.toString()
        showDetails = false
    }

    Dialog(
        onDismissRequest = {
            if (showDetails) showDetails = false else onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        PreviewSystemBars()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            when {
                selected == null -> EmptyStage(group)
                selected.isPlayableVideo -> DouyinVideoPlayer(
                    url = selected.file.uri.toString(),
                    modifier = Modifier.fillMaxSize(),
                    pause = audioPreview != null,
                )

                selected.kind == GroupMediaKind.IMAGE && imagePages.isNotEmpty() -> {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { pageIndex ->
                        val page = imagePages[pageIndex]
                        val liveUri = page.live?.file?.uri?.toString()
                        val livePlaying = liveUri != null && playingLiveUri == liveUri
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .pointerInput(pageIndex, liveUri) {
                                    detectTapGestures {
                                        if (liveUri != null && playingLiveUri != liveUri) {
                                            playingLiveUri = liveUri
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (livePlaying) {
                                InlineLoopingVideo(
                                    url = liveUri.orEmpty(),
                                    modifier = Modifier.fillMaxSize(),
                                    pause = audioPreview != null,
                                    muteAudio = imageBgmUrl != null,
                                )
                            } else {
                                AsyncImage(
                                    model = page.image.file.uri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(0.3f),
                                    contentScale = ContentScale.Crop,
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.28f)),
                                )
                                AsyncImage(
                                    model = page.image.file.uri,
                                    contentDescription = page.image.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                }

                selected.kind == GroupMediaKind.AUDIO -> AudioStage(
                    group = group,
                    item = selected,
                    onPlay = { audioPreview = selected },
                )

                selected.kind == GroupMediaKind.ZIP -> ZipStage(item = selected)

                else -> EmptyStage(group)
            }

            AutoLoopingAudio(
                url = imageBgmUrl,
                enabled = imageBgmEnabled,
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(190.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent),
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭作品预览",
                        tint = Color.White,
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(topBarAlpha),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "作品预览",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (group.isActive) {
                                group.statusLine
                            } else {
                                "${group.fileCount} 个文件 · ${formatBytes(group.totalBytes)}"
                            },
                            color = Color.White.copy(alpha = 0.62f),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HeaderAction(
                        icon = Icons.Default.Info,
                        label = "作品与文件详情",
                        onClick = { showDetails = true },
                    )
                    HeaderAction(
                        icon = Icons.Default.Share,
                        label = "分享当前文件",
                        enabled = displayItem != null,
                        onClick = { displayItem?.let { shareSavedFile(context, it.file) } },
                    )
                    HeaderAction(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        label = "打开当前文件",
                        enabled = displayItem != null,
                        onClick = { displayItem?.let { openSavedFile(context, it.file) } },
                    )
                    HeaderAction(
                        icon = if (infoVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        label = if (infoVisible) "隐藏作品信息" else "显示作品信息",
                        onClick = { infoVisible = !infoVisible },
                    )
                }
            }

            val currentLive = if (selected?.kind == GroupMediaKind.IMAGE) {
                currentImagePage?.live
            } else {
                null
            }
            if (currentLive != null &&
                playingLiveUri != currentLive.file.uri.toString()
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 98.dp),
                    color = Color.Black.copy(alpha = 0.42f),
                    shape = CircleShape,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "点击播放动图",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            if (selected?.kind == GroupMediaKind.IMAGE && imagePages.size > 1) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 62.dp),
                    color = Color.Black.copy(alpha = 0.36f),
                    shape = CircleShape,
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${imagePages.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = infoVisible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                enter = fadeIn(tween(220)) + slideInVertically(tween(240)) { it / 14 },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 18 },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                            ),
                        )
                        .padding(top = 56.dp),
                ) {
                    WorkOverlayInfo(
                        group = group,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = if (selected?.isPlayableVideo == true) 36.dp else 18.dp,
                            ),
                    )
                }
            }

            AnimatedVisibility(
                visible = showDetails,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(140)),
                exit = fadeOut(tween(100)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { showDetails = false },
                )
            }

            AnimatedVisibility(
                visible = showDetails,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f),
                enter = slideInVertically(tween(240)) { it } + fadeIn(tween(160)),
                exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(100)),
            ) {
                DetailsSheet(
                    group = group,
                    selected = displayItem,
                    onClose = { showDetails = false },
                    onSelect = { select(it) },
                    onPreviewAudio = { audioPreview = it },
                    onRemove = { confirmRemove = true },
                )
            }

            if (confirmRemove) {
                AlertDialog(
                    onDismissRequest = { confirmRemove = false },
                    title = { Text("移除这个作品的记录") },
                    text = {
                        Text("只移除本机的下载记录，不会删除已经保存到相册的文件。")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                confirmRemove = false
                                onRemoveGroup(group)
                            },
                        ) {
                            Text("移除")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmRemove = false }) {
                            Text("取消")
                        }
                    },
                )
            }
        }

        audioPreview?.let { item ->
            AudioPreviewDialog(
                title = item.title,
                uri = item.file.uri.toString(),
                onDismiss = { audioPreview = null },
            )
        }
    }
}

@Composable
private fun HeaderAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = if (enabled) 0.32f else 0.16f))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun WorkOverlayInfo(
    group: DownloadGroup,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(group.awemeId) { mutableStateOf(false) }
    val aweme = group.aweme
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val avatar = aweme.author.avatarUrls.firstOrNull()
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                if (avatar != null) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = aweme.author.displayName.take(1),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Text(
                text = "@${aweme.author.displayName}",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                color = if (aweme.kind == AwemeKind.IMAGES) {
                    BrandCyan.copy(alpha = 0.22f)
                } else {
                    BrandPink.copy(alpha = 0.22f)
                },
                shape = CircleShape,
            ) {
                Text(
                    text = if (aweme.kind == AwemeKind.IMAGES) "图文" else "视频",
                    color = if (aweme.kind == AwemeKind.IMAGES) BrandCyan else BrandPink,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }

        if (aweme.desc.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = aweme.desc,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) 8 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatChip(Icons.Default.FavoriteBorder, formatCount(aweme.stats.diggCount))
            StatChip(Icons.Default.ChatBubbleOutline, formatCount(aweme.stats.commentCount))
            StatChip(Icons.Default.Share, formatCount(aweme.stats.shareCount))
            StatChip(Icons.Default.StarBorder, formatCount(aweme.stats.collectCount))
        }

        aweme.music?.let { music ->
            Spacer(Modifier.height(7.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = buildString {
                        append(music.displayTitle)
                        if (music.author.isNotBlank()) append(" · ${music.author}")
                    },
                    color = Color.White.copy(alpha = 0.76f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatChip(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.74f),
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.76f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun EmptyStage(group: DownloadGroup) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        group.coverModel?.let { cover ->
            AsyncImage(
                model = cover,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.3f),
                contentScale = ContentScale.Crop,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (group.isActive) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp)
                Text(
                    text = group.statusLine,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                group.progressFraction?.let { fraction ->
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .width(180.dp)
                            .height(4.dp),
                        color = BrandPink,
                        trackColor = Color.White.copy(alpha = 0.2f),
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(42.dp),
                )
                Text(
                    text = "没有可预览的本地文件",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AudioStage(
    group: DownloadGroup,
    item: GroupMediaItem,
    onPlay: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        group.coverModel?.let { cover ->
            AsyncImage(
                model = cover,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.38f),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
                ),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .background(
                        Brush.linearGradient(listOf(BrandPink, BrandCyan)),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(58.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.music?.displayTitle ?: item.title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.music?.author?.takeIf { it.isNotBlank() }?.let { author ->
                Text(
                    text = author,
                    color = Color.White.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = item.detailSummary,
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onPlay,
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("试听 BGM")
            }
        }
    }
}

@Composable
private fun ZipStage(item: GroupMediaItem) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            }
            Text(
                text = item.file.displayName,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.detailSummary,
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DetailsSheet(
    group: DownloadGroup,
    selected: GroupMediaItem?,
    onClose: () -> Unit,
    onSelect: (GroupMediaItem) -> Unit,
    onPreviewAudio: (GroupMediaItem) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val aweme = group.aweme
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121216),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.22f), CircleShape),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "作品信息与下载详情",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "关闭详情", tint = Color.White)
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "work") {
                    WorkInfoCard(group)
                }

                if (group.mediaItems.isNotEmpty()) {
                    item(key = "media_title") {
                        SectionTitle("已下载内容 · ${group.mediaItems.size}")
                    }
                    items(
                        items = group.mediaItems,
                        key = { "media_${it.file.uri}" },
                    ) { item ->
                        MediaRow(
                            item = item,
                            selected = selected?.file?.uri == item.file.uri,
                            onClick = { onSelect(item) },
                            onPreviewAudio = { onPreviewAudio(item) },
                        )
                    }
                }

                if (group.activeTasks.isNotEmpty()) {
                    item(key = "active_title") {
                        SectionTitle("正在下载 · ${group.activeTasks.size}")
                    }
                    items(
                        items = group.activeTasks,
                        key = { "active_${it.id}" },
                    ) { task ->
                        ActiveTaskRow(task)
                    }
                }

                if (group.failedTasks.isNotEmpty()) {
                    item(key = "failed_title") {
                        SectionTitle("失败任务 · ${group.failedTasks.size}")
                    }
                    items(
                        items = group.failedTasks,
                        key = { "failed_${it.id}" },
                    ) { task ->
                        FailedTaskRow(task)
                    }
                }

                if (group.isActive) {
                    item(key = "remove_hint") {
                        Text(
                            text = "有下载任务正在进行，请先在任务卡片中取消，再移除记录。",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }

                item(key = "remove") {
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    TextButton(
                        onClick = onRemove,
                        enabled = !group.isActive,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("移除这个作品的下载记录")
                    }
                    Text(
                        text = "只移除记录，不会删除已经保存到相册的文件。",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.42f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkInfoCard(group: DownloadGroup) {
    val aweme = group.aweme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .width(84.dp)
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                group.coverModel?.let { cover ->
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = aweme.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${aweme.author.displayName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = group.summaryLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.5f),
                )
                Text(
                    text = group.statusLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (group.isActive) BrandPink else Color.White.copy(alpha = 0.66f),
                )
            }
        }

        if (aweme.desc.isNotBlank()) {
            Text(
                text = aweme.desc,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
        InfoLine("作品类型", if (aweme.kind == AwemeKind.IMAGES) "图文" else "视频")
        InfoLine("作品 ID", aweme.awemeId)
        val published = formatCreateTime(aweme.createTimeSeconds)
        if (published.isNotBlank()) InfoLine("发布时间", published)
        InfoLine("解析时间", formatDateTime(aweme.parsedAtMillis))
        if (aweme.canonicalUrl.isNotBlank()) {
            InfoLine("作品链接", aweme.canonicalUrl, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatChip(Icons.Default.FavoriteBorder, "赞 ${formatCount(aweme.stats.diggCount)}")
            StatChip(Icons.Default.ChatBubbleOutline, "评 ${formatCount(aweme.stats.commentCount)}")
            StatChip(Icons.Default.Share, "分享 ${formatCount(aweme.stats.shareCount)}")
            StatChip(Icons.Default.StarBorder, "藏 ${formatCount(aweme.stats.collectCount)}")
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, maxLines: Int = 2) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.42f),
            modifier = Modifier.width(58.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.78f),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun MediaRow(
    item: GroupMediaItem,
    selected: Boolean,
    onClick: () -> Unit,
    onPreviewAudio: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) BrandPink.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.035f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(item.kind.color.copy(alpha = 0.16f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.kind.icon,
                contentDescription = null,
                tint = item.kind.color,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "预览中",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandPink,
                    )
                }
            }
            Text(
                text = item.file.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.46f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.detailSummary,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.57f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.kind == GroupMediaKind.AUDIO) {
            IconButton(onClick = onPreviewAudio) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "试听",
                    tint = Color.White.copy(alpha = 0.75f),
                )
            }
        }
        IconButton(onClick = { openSavedFile(context, item.file) }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "打开文件",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(19.dp),
            )
        }
        IconButton(onClick = { shareSavedFile(context, item.file) }) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "分享文件",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun ActiveTaskRow(task: DownloadTask) {
    val context = LocalContext.current
    val fraction = task.progress.total?.takeIf { it > 0 }?.let {
        (task.progress.bytes.toFloat() / it).coerceIn(0f, 1f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.035f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.typeLabel(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
                Text(
                    text = task.message.ifBlank { "下载中" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
            TextButton(onClick = { DownloadService.cancel(context, task.id) }) {
                Text("取消")
            }
        }
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = BrandPink,
                trackColor = Color.White.copy(alpha = 0.12f),
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = BrandPink,
                trackColor = Color.White.copy(alpha = 0.12f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = buildString {
                    val total = task.progress.total
                    if (total != null && total > 0) {
                        append("${formatBytes(task.progress.displayBytes)} / ${formatBytes(total)}")
                    } else {
                        append(formatBytes(task.progress.displayBytes))
                    }
                    if (task.progress.itemsTotal > 1) {
                        append(" · ${task.progress.itemsDone}/${task.progress.itemsTotal}")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.45f),
            )
            Spacer(Modifier.weight(1f))
            if (task.progress.speedBps > 0) {
                Text(
                    text = "${task.progress.speedBps / 1024} KB/s",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
private fun FailedTaskRow(task: DownloadTask) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.035f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.typeLabel(),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
            Text(
                text = task.message.ifBlank { "下载失败" },
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFFB4AB),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (task.status == DownloadStatus.FAILED) {
            TextButton(
                onClick = {
                    runCatching {
                        DownloadService.enqueue(context, task.request)
                    }.onFailure {
                        Toast.makeText(context, it.message ?: "重试失败", Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Text("重试")
            }
        } else {
            Text(
                text = "需重新解析",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFFD27A),
            )
        }
    }
}

internal val GroupMediaKind.icon: ImageVector
    get() = when (this) {
        GroupMediaKind.VIDEO -> Icons.Default.Movie
        GroupMediaKind.IMAGE -> Icons.Default.Image
        GroupMediaKind.LIVE -> Icons.Default.PlayCircle
        GroupMediaKind.AUDIO -> Icons.Default.MusicNote
        GroupMediaKind.ZIP -> Icons.Default.Archive
    }

internal val GroupMediaKind.color: Color
    get() = when (this) {
        GroupMediaKind.VIDEO -> BrandPink
        GroupMediaKind.IMAGE -> BrandCyan
        GroupMediaKind.LIVE -> BrandPink
        GroupMediaKind.AUDIO -> Color(0xFFFFD27A)
        GroupMediaKind.ZIP -> Color.White.copy(alpha = 0.75f)
    }

private fun formatDateTime(millis: Long): String {
    if (millis <= 0) return "未知"
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))
    }.getOrDefault("未知")
}
