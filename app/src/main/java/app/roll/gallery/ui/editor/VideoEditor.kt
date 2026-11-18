package app.roll.gallery.ui.editor

import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import app.roll.gallery.R
import app.roll.gallery.data.Media
import app.roll.gallery.data.VIDEO_SPEEDS
import app.roll.gallery.data.VideoCrop
import app.roll.gallery.data.VideoEdits
import app.roll.gallery.data.centreCropNdc
import app.roll.gallery.data.editedLengthMs
import app.roll.gallery.data.formatDuration
import app.roll.gallery.ui.GalleryViewModel
import app.roll.gallery.ui.theme.GalleryTheme
import kotlinx.coroutines.delay

/**
 * The video editor: trim (drag the two ends), turn, cut to a shape, change the speed, take out
 * the sound, and save the frame on screen as a photo. The video plays the kept part on a loop
 * as you go, turned and with the cut-off parts dimmed. Save makes a new copy beside the
 * original (the original isn't changed), with a progress bar and Cancel.
 */
@Composable
fun VideoEditorScreen(viewModel: GalleryViewModel, media: Media, onClose: () -> Unit) {
    val context = LocalContext.current
    var edits by remember(media.id) { mutableStateOf(VideoEdits()) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var confirmLeave by remember { mutableStateOf(false) }
    val making by viewModel.making.collectAsStateWithLifecycle()
    val duration = media.durationMs.coerceAtLeast(1)
    val end = edits.endMs ?: duration

    val player = remember(media.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(media.uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(playing) { player.playWhenReady = playing }
    LaunchedEffect(edits.speed) { player.setPlaybackSpeed(edits.speed) }
    LaunchedEffect(edits.muted) { player.volume = if (edits.muted) 0f else 1f }
    // Plays only the kept part, round and round.
    LaunchedEffect(player, edits.startMs, end) {
        if (player.currentPosition !in edits.startMs..end) player.seekTo(edits.startMs)
        while (true) {
            position = player.currentPosition
            if (position >= end) player.seekTo(edits.startMs)
            delay(50)
        }
    }

    val leave: () -> Unit = { if (edits.isUnchanged) onClose() else confirmLeave = true }
    BackHandler(enabled = making == null, onBack = leave)
    BackHandler(enabled = making != null) { viewModel.cancelMaking() }

    GalleryTheme(darkTheme = true) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = leave) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel), tint = Color.White) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { viewModel.saveVideoEdits(media, edits) { if (it) onClose() } },
                    enabled = !edits.isUnchanged && making == null,
                    modifier = Modifier.padding(end = 8.dp),
                ) { Text(stringResource(R.string.save_copy)) }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                val turns = ((edits.quarterTurns % 4) + 4) % 4
                val sideways = turns % 2 == 1
                val videoRatio = media.width.toFloat().coerceAtLeast(1f) / media.height.toFloat().coerceAtLeast(1f)
                val shownRatio = if (sideways) 1f / videoRatio else videoRatio
                // The video's box, sized as it shows after turning; the view inside is turned to fit it.
                val boxRatio = shownRatio
                Box(
                    Modifier.aspectRatio(boxRatio, matchHeightConstraintsFirst = boxRatio < maxWidth / maxHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val w = if (sideways) maxHeight else maxWidth
                        val h = if (sideways) maxWidth else maxHeight
                        AndroidView(
                            factory = { TextureView(it).also { view -> player.setVideoTextureView(view) } },
                            modifier = Modifier
                                .size(w, h)
                                .graphicsLayer { rotationZ = 90f * turns },
                        )
                    }
                    CropShade(shownRatio, edits.crop)
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { playing = !playing }) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (playing) R.string.pause else R.string.play),
                        tint = Color.White,
                    )
                }
                Text(
                    "${formatDuration(position - edits.startMs)} / ${formatDuration(editedLengthMs(duration, edits))}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { viewModel.saveFrame(media, player.currentPosition) }) {
                    Icon(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.video_save_frame), tint = Color.White)
                }
                IconButton(onClick = { edits = edits.copy(quarterTurns = (edits.quarterTurns + 1) % 4) }) {
                    Icon(Icons.Outlined.Rotate90DegreesCw, stringResource(R.string.edit_rotate), tint = Color.White)
                }
            }
            RangeSlider(
                value = edits.startMs.toFloat()..end.toFloat(),
                onValueChange = { range ->
                    val start = range.start.toLong()
                    val stop = range.endInclusive.toLong().coerceAtLeast(start + MIN_LENGTH_MS).coerceAtMost(duration)
                    edits = edits.copy(startMs = start.coerceAtMost(stop - MIN_LENGTH_MS).coerceAtLeast(0), endMs = if (stop >= duration) null else stop)
                },
                onValueChangeFinished = { player.seekTo(edits.startMs) },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Column(
                Modifier.fillMaxWidth().height(150.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ChipRow(VideoCrop.entries, edits.crop, { stringResource(cropName(it)) }) { edits = edits.copy(crop = it) }
                ChipRow(VIDEO_SPEEDS, edits.speed, { stringResource(R.string.video_speed, it.toString().removeSuffix(".0")) }) { edits = edits.copy(speed = it) }
                Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.video_mute), color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = edits.muted, onCheckedChange = { edits = edits.copy(muted = it) })
                }
            }
        }

        making?.let { progress ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.video_saving)) },
                text = { LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()) },
                confirmButton = {},
                dismissButton = { TextButton(onClick = viewModel::cancelMaking) { Text(stringResource(R.string.cancel)) } },
            )
        }
        if (confirmLeave) {
            AlertDialog(
                onDismissRequest = { confirmLeave = false },
                title = { Text(stringResource(R.string.edit_discard_title)) },
                text = { Text(stringResource(R.string.edit_discard_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmLeave = false
                        onClose()
                    }) { Text(stringResource(R.string.discard)) }
                },
                dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.keep_editing)) } },
            )
        }
    }
}

/** The shortest a trimmed video can be. */
private const val MIN_LENGTH_MS = 500L

/** A row of chips to pick one of [options]. */
@Composable
private fun <T> ChipRow(options: List<T>, selected: T, name: @Composable (T) -> String, onPick: (T) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { option ->
            FilterChip(selected = option == selected, onClick = { onPick(option) }, label = { Text(name(option)) })
        }
    }
}

/** The name of a video shape. */
private fun cropName(crop: VideoCrop): Int = when (crop) {
    VideoCrop.Original -> R.string.edit_aspect_original
    VideoCrop.Square -> R.string.shape_square
    VideoCrop.Wide -> R.string.video_crop_wide
    VideoCrop.Tall -> R.string.video_crop_tall
    VideoCrop.FourThree -> R.string.video_crop_four_three
}

/** Dims what [crop] cuts off a video whose shape (after turning) is [ratio]. */
@Composable
private fun CropShade(ratio: Float, crop: VideoCrop) {
    if (crop.ratio == null) return
    Canvas(Modifier.fillMaxSize()) {
        // Same maths as saving, in Media3's -1 to 1 frame.
        val (l, r, b, t) = centreCropNdc((ratio * 1000).toInt(), 1000, crop.ratio).toList()
        val left = (l + 1) / 2 * size.width
        val right = (r + 1) / 2 * size.width
        val top = (1 - t) / 2 * size.height
        val bottom = (1 - b) / 2 * size.height
        val shade = Color.Black.copy(alpha = 0.6f)
        drawRect(shade, Offset.Zero, Size(size.width, top))
        drawRect(shade, Offset(0f, bottom), Size(size.width, size.height - bottom))
        drawRect(shade, Offset(0f, top), Size(left, bottom - top))
        drawRect(shade, Offset(right, top), Size(size.width - right, bottom - top))
    }
}
