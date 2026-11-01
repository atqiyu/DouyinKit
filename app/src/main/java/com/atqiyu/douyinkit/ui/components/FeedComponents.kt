package com.atqiyu.douyinkit.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atqiyu.douyinkit.ui.theme.BrandCyan
import com.atqiyu.douyinkit.ui.theme.BrandPink
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun FeedSectionHeader(
    title: String,
    subtitle: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .background(
                    Brush.verticalGradient(listOf(BrandPink, BrandCyan)),
                    CircleShape,
                ),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            text = "$count 个",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A slim gradient scroll rail with a floating percentage badge. It fades out
 * shortly after scrolling stops instead of permanently drawing a black bar.
 */
/**
 * Reads scroll state inside its own recomposition scope. Keeping these reads out
 * of the screen composable prevents every scroll frame from invalidating the
 * whole Library or Downloads screen.
 */
@Composable
fun ScrollProgressOverlay(
    progressProvider: () -> Float,
    canScrollProvider: () -> Boolean,
    isScrollInProgressProvider: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val progress by remember {
        derivedStateOf { progressProvider().coerceIn(0f, 1f) }
    }
    val canScroll by remember { derivedStateOf { canScrollProvider() } }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { isScrollInProgressProvider() }.collect { scrolling ->
            if (scrolling) {
                visible = true
            } else {
                delay(900)
                if (!isScrollInProgressProvider()) visible = false
            }
        }
    }
    if (canScroll) {
        ScrollProgressRail(
            progress = progress,
            visible = visible,
            modifier = modifier,
        )
    }
}

@Composable
fun ScrollProgressRail(
    progress: Float,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(120, easing = LinearEasing),
        label = "scroll-progress-rail",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(220),
        label = "scroll-progress-alpha",
    )
    Column(
        modifier = modifier
            .alpha(alpha)
            .width(42.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.38f),
            shape = CircleShape,
        ) {
            Text(
                text = "${(animatedProgress * 100).roundToInt()}%",
                color = Color.White.copy(alpha = 0.88f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Canvas(
            modifier = Modifier
                .width(5.dp)
                .height(108.dp),
        ) {
            val radius = CornerRadius(size.width / 2f, size.width / 2f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.2f),
                size = size,
                cornerRadius = radius,
            )
            val thumbHeight = 34.dp.toPx().coerceAtMost(size.height)
            val thumbTop = (size.height - thumbHeight) * animatedProgress
            drawCircle(
                color = BrandPink.copy(alpha = 0.3f),
                radius = 9.dp.toPx(),
                center = Offset(size.width / 2f, thumbTop + thumbHeight / 2f),
            )
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(BrandPink, BrandCyan)),
                topLeft = Offset(0f, thumbTop),
                size = Size(size.width, thumbHeight),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
fun FeedDatePickerDialog(
    initialDate: LocalDate?,
    onDismiss: () -> Unit,
    onSelect: (LocalDate?) -> Unit,
) {
    var visibleMonth by remember(initialDate) {
        mutableStateOf(initialDate?.let(YearMonth::from) ?: YearMonth.now())
    }
    var selected by remember(initialDate) { mutableStateOf(initialDate) }
    val today = remember { LocalDate.now() }
    val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
    val leading = visibleMonth.atDay(1).dayOfWeek.value - 1
    val cells: List<LocalDate?> = buildList {
        repeat(leading) { add(null) }
        repeat(visibleMonth.lengthOfMonth()) { add(visibleMonth.atDay(it + 1)) }
        while (size % 7 != 0) add(null)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择下载日期") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) }) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "上个月",
                        )
                    }
                    Text(
                        text = "${visibleMonth.year}年${visibleMonth.monthValue}月",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "下个月",
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekdays.forEach { label ->
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(
                        items = cells,
                        key = { index, _ -> index },
                    ) { _, date ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .padding(1.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (date != null) {
                                val isSelected = date == selected
                                val isToday = date == today
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .then(
                                            when {
                                                isSelected -> Modifier.background(BrandPink)
                                                isToday -> Modifier.border(
                                                    width = 1.dp,
                                                    color = BrandPink.copy(alpha = 0.55f),
                                                    shape = CircleShape,
                                                )

                                                else -> Modifier
                                            },
                                        )
                                        .clickable { selected = date },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) {
                                            Color.White
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        fontWeight = if (isSelected || isToday) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Normal
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = selected?.let {
                        "已选 ${it.year}年${it.monthValue}月${it.dayOfMonth}日"
                    } ?: "选择日期后点“确定”；“清除”会取消日期筛选。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let(onSelect) },
                enabled = selected != null,
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSelect(null) }) {
                    Text("清除")
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        },
    )
}
