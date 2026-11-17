package app.roll.gallery.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Size

/**
 * Works out [media]'s [differenceHash] from Android's small cached thumbnail, so even a large
 * library is quick to go through: no full-size photo is decoded. Null if there's no thumbnail.
 */
fun hashPhoto(resolver: ContentResolver, media: Media): Long? = runCatching {
    val thumbnail = resolver.loadThumbnail(media.uri, Size(64, 64), null)
    val small = Bitmap.createScaledBitmap(thumbnail, 9, 8, true)
    val pixels = IntArray(72)
    small.getPixels(pixels, 0, 9, 0, 0, 9, 8)
    if (small !== thumbnail) small.recycle()
    thumbnail.recycle()
    differenceHash(
        IntArray(72) { i ->
            val c = pixels[i]
            (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
        },
    )
}.getOrNull()
