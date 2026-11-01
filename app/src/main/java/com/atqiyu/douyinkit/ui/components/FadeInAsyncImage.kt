package com.atqiyu.douyinkit.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * A cover image that fades in once its bitmap is ready. Layer-only animation avoids
 * remeasurement while scrolling and works for both network and local video frames.
 */
@Composable
fun FadeInAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onError: (() -> Unit)? = null,
) {
    if (model == null) return
    var loaded by remember(model) { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "coverAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (loaded) 1f else 1.012f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "coverScale",
    )
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            scaleX = scale
            scaleY = scale
        },
        contentScale = contentScale,
        onSuccess = { loaded = true },
        onError = { onError?.invoke() },
    )
}
