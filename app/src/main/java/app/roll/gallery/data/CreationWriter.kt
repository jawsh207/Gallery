package app.roll.gallery.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import java.io.File
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Draws and saves what the Create tab makes. Collages go to Pictures/Gallery and videos to
 * Movies/Gallery, as new files named after when they were made; the photos used aren't changed.
 */
class CreationWriter(private val context: Context) {
    private val resolver = context.contentResolver
    private val renderer = EditRenderer(context)

    /**
     * Draws a collage of [photos], one per [cells] entry, in a picture [shape] with its longer
     * side [longSide] pixels. [gap] and [corners] are fractions (0 to 1) of the most spacing and
     * rounding offered; [background] shows in the gaps. Each photo fills its cell, cut to the
     * cell's shape around its middle. Photos are loaded one at a time to keep memory down.
     */
    fun drawCollage(
        photos: List<Media>,
        cells: List<Cell>,
        shape: CollageShape,
        gap: Float,
        corners: Float,
        background: Int,
        longSide: Int = COLLAGE_SIDE,
    ): Bitmap {
        val width = if (shape.ratio >= 1f) longSide else (longSide * shape.ratio).roundToInt()
        val height = if (shape.ratio >= 1f) (longSide / shape.ratio).roundToInt() else longSide
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(background)
        val gapPx = gap * MAX_GAP * longSide
        val radius = corners * MAX_CORNER * longSide
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        photos.zip(cells).forEach { (photo, cell) ->
            val r = cellRect(cell, width.toFloat(), height.toFloat(), gapPx)
            val target = RectF(r[0], r[1], r[2], r[3])
            // Loaded just big enough for its cell, so nine photos don't each arrive full size.
            val bitmap = renderer.decode(photo, (maxOf(target.width(), target.height()) * 1.5f).roundToInt().coerceAtLeast(64))
            val crop = centreCrop(bitmap.width, bitmap.height, target.width(), target.height())
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(target, radius, radius, Path.Direction.CW) })
            canvas.drawBitmap(
                bitmap,
                android.graphics.Rect(crop[0].roundToInt(), crop[1].roundToInt(), crop[2].roundToInt(), crop[3].roundToInt()),
                target,
                paint,
            )
            canvas.restore()
            bitmap.recycle()
        }
        return out
    }

    /** Saves [bitmap] as a new JPEG in Pictures/Gallery named after [prefix] and now. Returns where, or null. */
    fun saveImage(bitmap: Bitmap, prefix: String): Uri? = save(
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), PICTURES, prefix, "jpg", "image/jpeg",
    ) { out -> check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) }

    /** Saves the finished video [file] as a new MP4 in Movies/Gallery named after [prefix] and now. Returns where, or null. */
    fun saveVideo(file: File, prefix: String): Uri? = save(
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), MOVIES, prefix, "mp4", "video/mp4",
    ) { out -> file.inputStream().use { it.copyTo(out) } }

    private fun save(collection: Uri, folder: String, prefix: String, extension: String, mimeType: String, write: (java.io.OutputStream) -> Unit): Uri? {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(MediaColumns.DISPLAY_NAME, creationName(prefix, extension, now, ZoneId.systemDefault()))
            put(MediaColumns.MIME_TYPE, mimeType)
            put(MediaColumns.RELATIVE_PATH, folder)
            put(MediaColumns.DATE_TAKEN, now)
            put(MediaColumns.IS_PENDING, 1)
        }
        val target = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        return try {
            resolver.openOutputStream(target, "w").use { out -> write(requireNotNull(out)) }
            resolver.publish(target, now)
            target
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            null
        }
    }

    companion object {
        /** The longer side of a saved collage, in pixels. */
        const val COLLAGE_SIDE = 3000
        /** The widest gap offered, as a share of the longer side. */
        const val MAX_GAP = 0.03f
        /** The roundest corners offered, as a share of the longer side. */
        const val MAX_CORNER = 0.05f
        private const val PICTURES = "Pictures/Gallery/"
        private const val MOVIES = "Movies/Gallery/"
    }
}
