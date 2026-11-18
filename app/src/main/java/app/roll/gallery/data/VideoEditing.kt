package app.roll.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import java.io.File

/** The frame shapes a video can be cut to; [ratio] is width ÷ height, null to keep its own. */
enum class VideoCrop(val ratio: Float?) { Original(null), Square(1f), Wide(16f / 9f), Tall(9f / 16f), FourThree(4f / 3f) }

/** The speeds offered for a video. */
val VIDEO_SPEEDS = listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 4f)

/**
 * The changes to a video: keep [startMs] to [endMs] (null for the end), turn it clockwise by
 * [quarterTurns], cut it to [crop]'s shape around the middle, take out the sound ([muted]), and
 * play it at [speed].
 */
data class VideoEdits(
    val startMs: Long = 0,
    val endMs: Long? = null,
    val quarterTurns: Int = 0,
    val crop: VideoCrop = VideoCrop.Original,
    val muted: Boolean = false,
    val speed: Float = 1f,
) {
    /** True when saving would change nothing. */
    val isUnchanged: Boolean
        get() = startMs == 0L && endMs == null && quarterTurns % 4 == 0 && crop == VideoCrop.Original && !muted && speed == 1f
}

/** How long a [durationMs] video lasts after [edits]: trimmed, then sped up or slowed down. */
fun editedLengthMs(durationMs: Long, edits: VideoEdits): Long {
    val end = (edits.endMs ?: durationMs).coerceAtMost(durationMs)
    return ((end - edits.startMs).coerceAtLeast(0) / edits.speed).toLong()
}

/**
 * The centred piece of a [width] × [height] frame with shape [ratio], as Media3's crop wants it:
 * left, right, bottom and top from -1 to 1. The whole frame when [ratio] is null.
 */
fun centreCropNdc(width: Int, height: Int, ratio: Float?): FloatArray {
    if (ratio == null || width <= 0 || height <= 0) return floatArrayOf(-1f, 1f, -1f, 1f)
    val frame = width.toFloat() / height
    return if (frame > ratio) {
        val half = ratio / frame
        floatArrayOf(-half, half, -1f, 1f)
    } else {
        val half = frame / ratio
        floatArrayOf(-1f, 1f, -half, half)
    }
}

/**
 * Saves [edits] of the video [media] as a new MP4 in the app's cache, with Media3's Transformer:
 * trimmed, turned, cropped (after turning, so the shape is of the turned video), sped up or
 * slowed down (the sound with it, keeping its pitch), and with or without sound. [onProgress]
 * gets 0 to 1. Returns the file, or null if it failed.
 */
@OptIn(UnstableApi::class)
suspend fun exportVideo(context: Context, media: Media, edits: VideoEdits, onProgress: (Float) -> Unit): File? {
    val clip = MediaItem.ClippingConfiguration.Builder()
        .setStartPositionMs(edits.startMs)
        .apply { edits.endMs?.let { setEndPositionMs(it) } }
        .build()
    val item = MediaItem.Builder().setUri(media.uri).setClippingConfiguration(clip).build()
    val turns = ((edits.quarterTurns % 4) + 4) % 4
    val (w, h) = if (turns % 2 == 1) media.height to media.width else media.width to media.height
    val video = ArrayList<Effect>()
    val audio = ArrayList<AudioProcessor>()
    // Media3 turns anticlockwise for positive degrees.
    if (turns != 0) video += ScaleAndRotateTransformation.Builder().setRotationDegrees(-90f * turns).build()
    if (edits.crop.ratio != null) {
        val (l, r, b, t) = centreCropNdc(w, h, edits.crop.ratio).toList()
        video += Crop(l, r, b, t)
    }
    if (edits.speed != 1f) {
        if (edits.muted) {
            video += SpeedChangeEffect(edits.speed)
        } else {
            // An Android Pair, so its parts are read by name.
            val linked = Effects.createExperimentalSpeedChangingEffect(constantSpeed(edits.speed))
            audio += linked.first
            video += linked.second
        }
    }
    val edited = EditedMediaItem.Builder(item)
        .setRemoveAudio(edits.muted)
        .setEffects(Effects(audio, video))
        .build()
    val composition = Composition.Builder(EditedMediaItemSequence.Builder(edited).build()).build()
    val output = File(context.cacheDir, "making/${System.nanoTime()}.mp4").apply { parentFile?.mkdirs() }
    return runTransformer(context, composition, output, onProgress)
}

/** The same [speed] throughout. */
@OptIn(UnstableApi::class)
private fun constantSpeed(speed: Float) = object : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = speed

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = androidx.media3.common.C.TIME_UNSET
}

/** The frame of [media] (a video) at [positionMs], at full size, or null if it can't be read. */
fun videoFrame(context: Context, media: Media, positionMs: Long): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, media.uri)
        retriever.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
    } catch (e: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}
