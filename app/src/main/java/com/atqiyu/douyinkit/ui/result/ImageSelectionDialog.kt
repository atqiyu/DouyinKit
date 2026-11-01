package com.atqiyu.douyinkit.ui.result

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import com.atqiyu.douyinkit.core.model.ImageInfo
import com.atqiyu.douyinkit.data.prefs.AppSettings
import com.atqiyu.douyinkit.ui.theme.BrandPink

/**
 * Thumbnail-based image picker. Selection is expressed by tapping actual images,
 * never by a textual "第 N 张" checklist.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageSelectionDialog(
    images: List<ImageInfo>,
    initialSelected: Set<Int>,
    defaults: AppSettings,
    hasMusic: Boolean,
    onDismiss: () -> Unit,
    onDownload: (
        selected: Set<Int>,
        includeLivePhotos: Boolean,
        includeMusic: Boolean,
        createZip: Boolean,
    ) -> Unit,
) {
    val availableIndexes = remember(images) {
        images.indices.filter { images[it].hasDownloadSource }.toSet()
    }
    var selected by remember(images) {
        mutableStateOf(initialSelected.filter { it in availableIndexes }.toSet())
    }
    var includeLive by remember { mutableStateOf(defaults.includeLivePhotos) }
    var includeMusic by remember { mutableStateOf(defaults.includeMusic && hasMusic) }
    var createZip by remember { mutableStateOf(defaults.createZip) }
    val allSelected = availableIndexes.isNotEmpty() && selected.containsAll(availableIndexes)

    fun toggle(index: Int) {
        if (index !in availableIndexes) return
        selected = if (index in selected) selected - index else selected + index
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        LightDialogSystemBars()
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
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
                        .padding(start = 4.dp, end = 10.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭图片选择")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "选择图片",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "已选 ${selected.size} / ${availableIndexes.size} 张 · 点击图片或长按选择",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = {
                            selected = if (allSelected) emptySet() else availableIndexes
                        },
                        enabled = availableIndexes.isNotEmpty(),
                    ) {
                        Text(if (allSelected) "取消全选" else "全选")
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(
                        items = images,
                        key = { index, _ -> index },
                    ) { index, image ->
                        ImagePickTile(
                            image = image,
                            selected = index in selected,
                            available = index in availableIndexes,
                            onClick = { toggle(index) },
                            onLongClick = { toggle(index) },
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = includeLive,
                            onClick = { includeLive = !includeLive },
                            label = { Text("含动图") },
                        )
                        FilterChip(
                            selected = includeMusic,
                            onClick = { includeMusic = !includeMusic },
                            enabled = hasMusic,
                            label = { Text("下载 BGM") },
                        )
                        FilterChip(
                            selected = createZip,
                            onClick = { createZip = !createZip },
                            label = { Text("打包 ZIP") },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onDownload(
                                selected.toSet(),
                                includeLive,
                                includeMusic,
                                createZip,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selected.isNotEmpty(),
                    ) {
                        Text(
                            if (selected.isEmpty()) {
                                "请选择图片"
                            } else {
                                "下载已选（${selected.size} 张）"
                            },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "只下载选中的图片；对应的 LivePhoto 会随图片一起保存。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LightDialogSystemBars() {
    val view = LocalView.current
    val lightBackground = MaterialTheme.colorScheme.background.luminance() > 0.5f
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBackground
            isAppearanceLightNavigationBars = lightBackground
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImagePickTile(
    image: ImageInfo,
    selected: Boolean,
    available: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val url = image.urls.firstOrNull() ?: image.downloadUrls.firstOrNull()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(
                if (selected) {
                    Modifier.border(2.dp, BrandPink, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                enabled = available,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        if (!available) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
            )
            Text(
                text = "无下载地址",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (image.isLivePhoto) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
                shape = CircleShape,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp),
                    )
                    Text("动图", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, BrandPink.copy(alpha = 0.18f)),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(BrandPink, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已选择",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
