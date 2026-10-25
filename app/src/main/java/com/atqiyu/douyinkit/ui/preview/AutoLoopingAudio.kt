package com.atqiyu.douyinkit.ui.preview

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.atqiyu.douyinkit.core.net.DouyinUserAgent

/**
 * Invisible looping BGM player used by image-post previews. It has no UI and is
 * driven entirely by [enabled]/[pause], so the surrounding preview controls when
 * the music plays without mixing with video or LivePhoto audio.
 */
@OptIn(UnstableApi::class)
@Composable
fun AutoLoopingAudio(
    url: String?,
    enabled: Boolean,
    pause: Boolean = false,
) {
    if (url.isNullOrBlank()) return
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentEnabled by rememberUpdatedState(enabled)
    val currentPause by rememberUpdatedState(pause)
    val isLocal = url.startsWith("content://") || url.startsWith("file://")

    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            repeatMode = Player.REPEAT_MODE_ONE
            val source = if (isLocal) {
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
            setMediaSource(source)
            prepare()
            playWhenReady = enabled
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(enabled, pause, player) {
        if (enabled && !pause) {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
            }
            player.play()
        } else {
            player.pause()
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (currentEnabled && !currentPause) player.play()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
