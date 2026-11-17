package app.roll.gallery.ui.viewer

import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import app.roll.gallery.R
import app.roll.gallery.data.Media
import app.roll.gallery.data.formatDuration
import app.roll.gallery.image.Thumbnail
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.delay

/**
 * One photo, fitted to the screen. The grid's thumbnail shows at once while the full photo
 * loads over it, at up to twice the screen's size so zooming in stays sharp without holding
 * a 50-megapixel bitmap in memory.
 */
@Composable
fun PhotoPage(media: Media, zoom: ZoomState, gestures: Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val longSide = with(density) { maxOf(configuration.screenWidthDp, configuration.screenHeightDp).dp.roundToPx() }
    val target = (longSide * 2).coerceAtMost(4096)
    val request = remember(media.uri, media.modifiedAt, target) {
        ImageRequest.Builder(context).data(media.uri).size(target, target).build()
    }
    var loaded by remember(media.id) { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { zoom.container = it }
            .then(gestures)
            .graphicsLayer {
                scaleX = zoom.scale
                scaleY = zoom.scale
                translationX = zoom.offset.x
                translationY = zoom.offset.y
            },
    ) {
        if (!loaded) {
            AsyncImage(
                model = Thumbnail(media.uri, media.modifiedAt),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        AsyncImage(
            model = request,
            contentDescription = stringResource(R.string.photo),
            contentScale = ContentScale.Fit,
            onSuccess = { loaded = true },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * One video. While it's the page on screen ([active]) it plays, with a play/pause button and a
 * seek bar that show and hide with the rest of the viewer's controls ([controlsVisible]).
 * The player is released as soon as the page is swiped away, and pauses when the app leaves
 * the screen. Until the first frame is drawn, the thumbnail covers the video surface.
 */
@Composable
fun VideoPage(media: Media, active: Boolean, controlsVisible: Boolean, gestures: Modifier) {
    val context = LocalContext.current
    val player = remember(active, media.id) {
        if (!active) return@remember null
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(media.uri))
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(player) { onDispose { player?.release() } }

    var playing by remember(player) { mutableStateOf(false) }
    var ended by remember(player) { mutableStateOf(false) }
    var firstFrame by remember(player) { mutableStateOf(false) }
    var position by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(media.durationMs) }
    var aspect by remember(media.id) {
        mutableFloatStateOf(if (media.width > 0 && media.height > 0) media.width.toFloat() / media.height else 16f / 9f)
    }
    var seeking by remember(player) { mutableStateOf<Float?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                ended = playbackState == Player.STATE_ENDED
                player?.duration?.takeIf { it > 0 }?.let { duration = it }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onRenderedFirstFrame() {
                firstFrame = true
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    // Pauses when the app goes to the background.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) player?.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(player, playing) {
        while (player != null) {
            position = player.currentPosition
            if (!playing) break
            delay(200)
        }
    }

    Box(Modifier.fillMaxSize().then(gestures), contentAlignment = Alignment.Center) {
        Box(Modifier.aspectRatio(aspect)) {
            if (player != null) {
                key(player) {
                    AndroidView(
                        factory = { SurfaceView(it).also { view -> player.setVideoSurfaceView(view) } },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (!firstFrame) {
                AsyncImage(
                    model = Thumbnail(media.uri, media.modifiedAt),
                    contentDescription = stringResource(R.string.video),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (player != null && controlsVisible) {
            FilledTonalIconButton(
                onClick = {
                    when {
                        playing -> player.pause()
                        ended -> {
                            player.seekTo(0)
                            player.play()
                        }
                        else -> player.play()
                    }
                },
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                    modifier = Modifier.size(36.dp),
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 88.dp),
            ) {
                val shown = seeking ?: position.toFloat()
                Slider(
                    value = shown.coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                    onValueChange = { seeking = it },
                    onValueChangeFinished = {
                        seeking?.let { player.seekTo(it.toLong()); position = it.toLong() }
                        seeking = null
                    },
                    valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                )
                Row {
                    Text(formatDuration(shown.toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Box(Modifier.weight(1f))
                    Text(formatDuration(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
