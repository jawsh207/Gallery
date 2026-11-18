package app.roll.gallery.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** The frame of a made video: width and height in pixels. */
enum class VideoShape(val width: Int, val height: Int) {
    Landscape(1920, 1080),
    Portrait(1080, 1920),
    Square(1080, 1080),
}

/** One step of a made video: a photo shown for [photoMs], or a whole video clip. */
data class Shot(val media: Media, val photoMs: Long)

/**
 * Makes videos on the phone with Media3's Transformer (the open-source AndroidX media library),
 * encoding with the phone's own video encoder. Photos become stills of a set length and clips
 * play in full, one after another, each fitted into the frame ([fill] crops to fill it instead
 * of leaving black bars). Clips keep their sound, and [music] (any audio file) can play
 * underneath, repeating if it's shorter than the video and cut off at the end if longer.
 * [onProgress] gets 0 to 1 as it goes. Returns the finished file in the app's cache, or null if
 * it failed.
 */
@OptIn(UnstableApi::class)
suspend fun makeVideo(
    context: Context,
    shots: List<Shot>,
    shape: VideoShape,
    fill: Boolean,
    music: Uri?,
    onProgress: (Float) -> Unit,
): File? = withContext(Dispatchers.Main) {
    // Transformer must be started and watched from a thread with a Looper; the main one is.
    val output = File(context.cacheDir, "making/${System.nanoTime()}.mp4").apply { parentFile?.mkdirs() }
    val frame = Presentation.createForWidthAndHeight(
        shape.width, shape.height,
        if (fill) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT,
    )
    val items = shots.map { shot ->
        val media = shot.media
        val item = if (media.isVideo) {
            MediaItem.fromUri(media.uri)
        } else {
            MediaItem.Builder().setUri(media.uri).setImageDurationMs(shot.photoMs).build()
        }
        EditedMediaItem.Builder(item)
            .apply { if (!media.isVideo) setFrameRate(FRAME_RATE) }
            .setEffects(Effects(emptyList(), listOf(frame)))
            .build()
    }
    val mixesStillsAndClips = shots.any { it.media.isVideo } && shots.any { !it.media.isVideo }
    val pictures = EditedMediaItemSequence.Builder(items)
        // Stills have no sound, so a silent track is made for them when clips around them have one.
        .experimentalSetForceAudioTrack(mixesStillsAndClips || music != null)
        .build()
    val sequences = buildList {
        add(pictures)
        if (music != null) {
            val song = EditedMediaItem.Builder(MediaItem.fromUri(music)).setRemoveVideo(true).build()
            add(EditedMediaItemSequence.Builder(song).setIsLooping(true).build())
        }
    }
    val composition = Composition.Builder(sequences).build()
    runTransformer(context, composition, output, onProgress)
}

/**
 * Runs Transformer on [composition] into [output] (H.264 video, AAC sound) on the main thread,
 * which it needs, reporting 0 to 1 to [onProgress]. Returns [output], or null if it failed (the
 * file is then removed). Cancelling the caller stops it.
 */
@OptIn(UnstableApi::class)
internal suspend fun runTransformer(
    context: Context,
    composition: Composition,
    output: File,
    onProgress: (Float) -> Unit,
): File? = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        val progress = ProgressHolder()
        lateinit var transformer: Transformer
        val poll = object : Runnable {
            override fun run() {
                if (transformer.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(progress.progress / 100f)
                handler.postDelayed(this, 300)
            }
        }
        transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    handler.removeCallbacks(poll)
                    onProgress(1f)
                    if (continuation.isActive) continuation.resume(output)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    handler.removeCallbacks(poll)
                    output.delete()
                    if (continuation.isActive) continuation.resume(null)
                }
            })
            .build()
        continuation.invokeOnCancellation {
            handler.post {
                handler.removeCallbacks(poll)
                transformer.cancel()
                output.delete()
            }
        }
        transformer.start(composition, output.absolutePath)
        handler.post(poll)
    }
}

/** Frames per second for photos in a made video. */
private const val FRAME_RATE = 30
