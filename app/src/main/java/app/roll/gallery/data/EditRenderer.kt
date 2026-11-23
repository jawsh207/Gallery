package app.roll.gallery.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns [Edits] into pixels and files: a small preview for the editor, and the full-size result
 * when saving, either over the original or as a new copy beside it.
 */
class EditRenderer(private val context: Context) {
    private val resolver = context.contentResolver

    /** [media] decoded upright (its orientation tag applied) with its longer side at most [maxSide] pixels. */
    fun decode(media: Media, maxSide: Int): Bitmap {
        val source = ImageDecoder.createSource(resolver, media.uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Software pixels: they're drawn into a new bitmap, which hardware bitmaps can't be.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > maxSide) {
                val scale = maxSide.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            }
        }
    }

    /**
     * Every edit applied to [bitmap]: highlights, shadows and sharpening, turn and flip, crop,
     * colour, then the vignette and markup on top.
     */
    fun apply(bitmap: Bitmap, edits: Edits): Bitmap {
        val toned = applyTones(bitmap, edits.adjustments)
        val turned = straightenBitmap(turnBitmap(toned, edits.quarterTurns, edits.flipped), edits.straighten)
        val c = edits.crop
        val x = (c.left * turned.width).roundToInt().coerceIn(0, turned.width - 1)
        val y = (c.top * turned.height).roundToInt().coerceIn(0, turned.height - 1)
        val w = ((c.right - c.left) * turned.width).roundToInt().coerceIn(1, turned.width - x)
        val h = ((c.bottom - c.top) * turned.height).roundToInt().coerceIn(1, turned.height - y)
        val cropped = if (c.isFull) turned else Bitmap.createBitmap(turned, x, y, w, h)
        val matrix = colorMatrix(edits)
        val overlays = edits.adjustments.vignette > 0f || edits.markup.isNotEmpty()
        if (matrix.contentEquals(IDENTITY_MATRIX) && !overlays) return cropped
        val out = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            if (!matrix.contentEquals(IDENTITY_MATRIX)) colorFilter = ColorMatrixColorFilter(matrix)
        }
        val canvas = Canvas(out)
        canvas.drawBitmap(cropped, 0f, 0f, paint)
        drawVignette(canvas, out.width, out.height, edits.adjustments.vignette)
        drawMarkup(canvas, edits.markup, out.width, out.height)
        return out
    }

    /**
     * Saves [edits] of [media] at full size (up to [MAX_SAVE_SIDE] pixels). Over the original
     * when [asCopy] is false (the caller must already have write access, and the photo must be
     * a type [canOverwrite] accepts); otherwise as a new "-edited" file in the same album, or in
     * Pictures/Edited when Android won't put photos in that album's folder. The camera details
     * and dates are carried over. Returns where it was saved, or null if it failed.
     */
    fun save(media: Media, edits: Edits, asCopy: Boolean): Uri? = runCatching {
        val result = apply(decode(media, MAX_SAVE_SIDE), edits)
        val format = outputFormat(media.mimeType)
        val sourceExif = runCatching { resolver.openInputStream(media.uri)?.use { ExifInterface(it) } }.getOrNull()
        val target = if (asCopy) {
            val folder = if (canHold(media.path, isVideo = false)) media.path else EDITED_FOLDER
            val values = ContentValues().apply {
                put(MediaColumns.DISPLAY_NAME, editedName(media.fileName, format.extension))
                put(MediaColumns.MIME_TYPE, format.mimeType)
                put(MediaColumns.RELATIVE_PATH, folder)
                put(MediaColumns.DATE_TAKEN, media.takenAt)
                put(MediaColumns.IS_PENDING, 1)
            }
            resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: error("insert refused")
        } else {
            media.uri
        }
        try {
            // "wt" truncates, so a smaller result doesn't leave the old file's tail behind.
            resolver.openOutputStream(target, "wt").use { out ->
                check(result.compress(format.compress, 95, requireNotNull(out)))
            }
            if (format.mimeType == "image/jpeg" && sourceExif != null) copyExif(sourceExif, target)
            if (asCopy) {
                resolver.update(target, ContentValues().apply { put(MediaColumns.IS_PENDING, 0) }, null, null)
            } else if (media.path.isNotEmpty()) {
                // Has Android look the file over now, so its change time and size update at
                // once and the new version shows straight away.
                val file = java.io.File(android.os.Environment.getExternalStorageDirectory(), media.path + media.fileName)
                android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            }
        } catch (e: Exception) {
            if (asCopy) resolver.delete(target, null, null)
            throw e
        }
        target
    }.getOrNull()

    /** Copies the dates and camera details to the saved JPEG; the pixels are now upright, so orientation is reset. */
    private fun copyExif(source: ExifInterface, target: Uri) {
        resolver.openFileDescriptor(target, "rw")?.use { fd ->
            val exif = ExifInterface(fd.fileDescriptor)
            for (tag in KEPT_TAGS) source.getAttribute(tag)?.let { exif.setAttribute(tag, it) }
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            exif.saveAttributes()
        }
    }

    /** How a saved photo is written. */
    private data class Output(val compress: Bitmap.CompressFormat, val mimeType: String, val extension: String)

    /** PNG and WebP stay as they are; everything else is saved as JPEG. */
    private fun outputFormat(mimeType: String): Output = when (mimeType.lowercase()) {
        "image/png" -> Output(Bitmap.CompressFormat.PNG, "image/png", "png")
        "image/webp" -> Output(Bitmap.CompressFormat.WEBP_LOSSY, "image/webp", "webp")
        else -> Output(Bitmap.CompressFormat.JPEG, "image/jpeg", "jpg")
    }

    private companion object {
        const val MAX_SAVE_SIDE = 8192
        const val EDITED_FOLDER = "Pictures/Edited/"
        val KEPT_TAGS = listOf(
            ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH,
        )
    }
}

/**
 * Whether an edited photo can be saved over the original: only for JPEG, PNG and WebP, the
 * formats Android can write back. Others (HEIC, AVIF, GIF…) are saved as a JPEG copy instead.
 */
fun canOverwrite(mimeType: String): Boolean = mimeType.lowercase() in setOf("image/jpeg", "image/png", "image/webp")

/** The name for an edited copy: "IMG_1234.jpg" becomes "IMG_1234-edited.jpg" (with [extension]). */
fun editedName(fileName: String, extension: String): String {
    val base = fileName.substringBeforeLast('.', fileName).ifBlank { "photo" }
    return "$base-edited.$extension"
}

/**
 * [bitmap] tilted [degrees] clockwise about its centre and enlarged by [straightenScale], in a
 * bitmap the same size, so the edges stay filled.
 */
fun straightenBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    if (degrees == 0f) return bitmap
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    val scale = straightenScale(w, h, degrees)
    val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    Canvas(out).apply {
        rotate(degrees, w / 2, h / 2)
        scale(scale, scale, w / 2, h / 2)
        drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
    }
    return out
}

/** [bitmap] turned [quarterTurns] times clockwise and, when [flipped], mirrored left to right. */
fun turnBitmap(bitmap: Bitmap, quarterTurns: Int, flipped: Boolean): Bitmap {
    val turns = ((quarterTurns % 4) + 4) % 4
    if (turns == 0 && !flipped) return bitmap
    val matrix = Matrix().apply {
        postRotate(90f * turns)
        if (flipped) postScale(-1f, 1f)
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
