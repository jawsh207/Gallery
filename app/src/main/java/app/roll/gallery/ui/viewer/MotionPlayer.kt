package app.roll.gallery.ui.viewer

import android.content.Context
import android.net.Uri
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ContentDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import app.roll.gallery.data.MotionVideo

/**
 * Reads a slice of a file as if it were the whole file: the motion video tucked into the end of
 * a photo. Every read the player asks for is shifted by [offset], so the video plays straight
 * out of the photo without being copied anywhere.
 */
@OptIn(UnstableApi::class)
class SliceDataSource(private val upstream: DataSource, private val offset: Long, private val length: Long) : DataSource by upstream {
    override fun open(dataSpec: DataSpec): Long {
        val remaining = length - dataSpec.position
        val wanted = if (dataSpec.length == C.LENGTH_UNSET.toLong()) remaining else minOf(dataSpec.length, remaining)
        upstream.open(dataSpec.buildUpon().setPosition(offset + dataSpec.position).setLength(wanted).build())
        return wanted
    }
}

/** A player for [motion]'s video inside the photo at [uri], silent, as motion photos play. */
@OptIn(UnstableApi::class)
fun motionPlayer(context: Context, uri: Uri, motion: MotionVideo): ExoPlayer {
    val slices = DataSource.Factory { SliceDataSource(ContentDataSource(context), motion.offset, motion.length) }
    return ExoPlayer.Builder(context).build().apply {
        setMediaSource(ProgressiveMediaSource.Factory(slices).createMediaSource(MediaItem.fromUri(uri)))
        volume = 0f
        prepare()
    }
}

/**
 * Plays a motion photo's video once over the photo while [playing], then fades back to the
 * still and calls [onEnd]. The surface stays see-through until the first frame is drawn, so
 * the photo never flashes black.
 */
@Composable
fun MotionOverlay(uri: Uri, motion: MotionVideo, playing: Boolean, onEnd: () -> Unit, modifier: Modifier = Modifier) {
    if (!playing) return
    val context = LocalContext.current
    val ended by rememberUpdatedState(onEnd)
    val player = remember(uri, motion) { motionPlayer(context, uri, motion) }
    var shown by remember(player) { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                shown = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) ended()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                ended()
            }
        }
        player.addListener(listener)
        player.playWhenReady = true
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    // A TextureView rather than a SurfaceView: it can be see-through until the first frame and
    // zooms with the photo, where a SurfaceView would show black and ignore the zoom.
    Box(modifier.alpha(if (shown) 1f else 0f)) {
        AndroidView(
            factory = { TextureView(it).also { view -> player.setVideoTextureView(view) } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
