package com.atqiyu.douyinkit.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atqiyu.douyinkit.R
import com.atqiyu.douyinkit.ui.theme.BrandCyan
import com.atqiyu.douyinkit.ui.theme.BrandPink
import com.atqiyu.douyinkit.ui.theme.Ink

/** The launcher artwork inside a dark rounded tile, reused by Home and onboarding. */
@Composable
fun AppMark(
    size: Dp = 48.dp,
    glow: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "app-mark-glow")
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "app-mark-glow-alpha",
    )
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (glow) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.45f)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                BrandPink.copy(alpha = glowAlpha),
                                BrandCyan.copy(alpha = glowAlpha * 0.55f),
                                Color.Transparent,
                            ),
                        ),
                        CircleShape,
                    ),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(size * 0.3f))
                .background(
                    Brush.linearGradient(
                        listOf(Ink, Color(0xFF1A1A22)),
                    ),
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(size * 0.3f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "DouyinKit 图标",
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.4f),
            )
        }
    }
}
