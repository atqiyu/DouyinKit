package com.atqiyu.douyinkit.ui.preview

import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.atqiyu.douyinkit.core.net.DouyinUserAgent
import com.atqiyu.douyinkit.core.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * A full-screen player without ExoPlayer's default controller. A single tap toggles playback,
 * matching the interaction of the Douyin feed: a small play hint when paused and a thin,
 * draggable progress line at the bottom.
 */
@OptIn(UnstableApi::class)
@Composable
fun DouyinVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    pause: Boolean = false,
) {
    val context = LocalContext.current
    val isLocal = url.startsWith("content://") || url.startsWith("file://")
    val lifecycleOwner = LocalLifecycleOwner.current
    var isPlaying by remember(url) { mutableStateOf(true) }
    var isBuffering by remember(url) { mutableStateOf(true) }
    var position by remember(url) { mutableLongStateOf(0L) }
    var duration by remember(url) { mutableLongStateOf(0L) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var dragFraction by remember(url) { mutableStateOf<Float?>(null) }
    val currentPause by rememberUpdatedState(pause)
    var wasPlayingBeforePause by remember(url) { mutableStateOf(true) }
    var wasPlayingBeforeScrub by remember(url) { mutableStateOf(true) }
    var wasPlayingBeforeExternalPause by remember(url) { mutableStateOf(false) }

    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            repeatMode = Player.REPEAT_MODE_ONE
            val mediaSource = if (isLocal) {
                ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))
                    .createMediaSource(MediaItem.fromUri(url))
            } else {
                val dataSourceFactory = DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(
                        mapOf(
                            "Referer" to DouyinUserAgent.REFERER,
                            "User-Agent" to DouyinUserAgent.DESKTOP_FALLBACK,
                        ),
                    )
                    .setAllowCrossProtocolRedirects(true)
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(url))
            }
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
            addListener(
                object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = playbackState == Player.STATE_BUFFERING
                        if (playbackState == Player.STATE_READY) {
                            duration = this@apply.duration.coerceAtLeast(0L)
                        }
                    }

                    override fun onPlayerError(playbackException: PlaybackException) {
                        isBuffering = false
                        val message = playbackException.message.orEmpty()
                        error = when {
                            message.contains("decoder", ignoreCase = true) ||
                                message.contains("codec", ignoreCase = true) ->
                                "当前设备无法解码该视频，建议下载 H.264 版本"

                            isLocal -> "文件无法播放，可能已损坏"
                            else -> "视频加载失败，链接可能已过期"
                        }
                    }
                },
            )
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    wasPlayingBeforePause = player.isPlaying
                    player.pause()
                }

                Lifecycle.Event.ON_RESUME -> {
                    if (wasPlayingBeforePause &&
                        !currentPause &&
                        player.playbackState != Player.STATE_ENDED
                    ) {
                        player.play()
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(pause, player) {
        if (pause) {
            if (player.isPlaying) {
                wasPlayingBeforeExternalPause = true
                player.pause()
            }
        } else if (wasPlayingBeforeExternalPause) {
            wasPlayingBeforeExternalPause = false
            player.play()
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            if (dragFraction == null) {
                position = player.currentPosition.coerceAtLeast(0L)
            }
            duration = player.duration.coerceAtLeast(0L)
            delay(120)
        }
    }

    fun togglePlayback() {
        if (error != null) return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
            }
            player.play()
        }
    }

    Box(
        modifier = modifier.background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    keepScreenOn = true
                    setBackgroundColor(AndroidColor.BLACK)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { view -> view.player = player },
            modifier = Modifier.matchParentSize(),
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(player, error) {
                    detectTapGestures { togglePlayback() }
                },
        )

        if (isBuffering && error == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(34.dp),
                color = Color.White,
                strokeWidth = 2.5.dp,
            )
        }

        AnimatedVisibility(
            visible = !isPlaying && !isBuffering && error == null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(140)) + scaleIn(tween(180), initialScale = 0.8f),
            exit = fadeOut(tween(100)) + scaleOut(tween(120), targetScale = 0.85f),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Text(
                    text = "点击播放",
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        error?.let { message ->
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            )
        }

        VideoProgressLine(
            position = position,
            duration = duration,
            dragFraction = dragFraction,
            onScrub = { fraction ->
                if (dragFraction == null) {
                    wasPlayingBeforeScrub = player.isPlaying
                }
                dragFraction = fraction
                position = (duration * fraction).toLong()
            },
            onSeek = { fraction ->
                dragFraction = null
                if (duration > 0) {
                    player.seekTo((duration * fraction).toLong())
                }
                if (wasPlayingBeforeScrub) {
                    player.play()
                } else {
                    player.pause()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun VideoProgressLine(
    position: Long,
    duration: Long,
    dragFraction: Float?,
    onScrub: (Float) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rawFraction = dragFraction ?: if (duration > 0) {
        (position.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedFraction by animateFloatAsState(
        targetValue = rawFraction,
        animationSpec = if (dragFraction != null) {
            snap()
        } else {
            tween(durationMillis = 150, easing = LinearEasing)
        },
        label = "video-progress",
    )

    Box(
        modifier = modifier
            .height(30.dp)
            .pointerInput(duration) {
                if (duration <= 0) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun update(x: Float): Float = (x / size.width.toFloat()).coerceIn(0f, 1f)
                    onScrub(update(down.position.x))
                    var lastFraction = update(down.position.x)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        lastFraction = update(change.position.x)
                        onScrub(lastFraction)
                        if (!change.pressed) {
                            change.consume()
                            onSeek(lastFraction)
                            break
                        }
                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
        ) {
            val trackY = size.height - 2.dp.toPx()
            drawLine(
                color = Color.White.copy(alpha = 0.28f),
                start = Offset(0f, trackY),
                end = Offset(size.width, trackY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White,
                start = Offset(0f, trackY),
                end = Offset(size.width * animatedFraction, trackY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        if (dragFraction != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val current = (duration * animatedFraction).toLong()
                Text(
                    text = "${formatDuration(current)} / ${formatDuration(duration)}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
