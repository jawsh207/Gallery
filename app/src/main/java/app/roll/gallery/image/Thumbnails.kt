package app.roll.gallery.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import app.roll.gallery.data.Media
import java.io.File
import android.util.Size as AndroidSize
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse

/**
 * A grid thumbnail of a photo or video. Loading one asks Android for the thumbnail it already
 * keeps for the file, which is much faster than decoding the full file and works for videos
 * without a video decoder. [modifiedAt] is part of the cache key, so an edited file gets a
 * new thumbnail. Files in the Locked folder aren't in Android's catalogue, so for those
 * ([uri] is a file address) the thumbnail is made from the file, which is where [isVideo] matters.
 */
data class Thumbnail(val uri: Uri, val modifiedAt: Long, val isVideo: Boolean = false)

/** The grid thumbnail for this item. */
fun Media.thumbnail(): Thumbnail = Thumbnail(uri, modifiedAt, isVideo)

/**
 * The thumbnail size to ask for: the next of 256, 512 or 1024 pixels up from what's being
 * drawn. Rounding up means changing the grid's zoom reuses cached thumbnails instead of
 * loading new ones for every few pixels of difference.
 */
fun thumbnailBucket(px: Int): Int = when {
    px <= 256 -> 256
    px <= 512 -> 512
    else -> 1024
}

/** Lets Coil keep [Thumbnail]s in its memory cache, keyed by file, change time and size. */
class ThumbnailKeyer : Keyer<Thumbnail> {
    override fun key(data: Thumbnail, options: Options): String =
        "thumb:${data.uri}:${data.modifiedAt}:${thumbnailBucket(options.longestSide())}"
}

/** Loads a [Thumbnail] through Android's own thumbnail cache (or from the file, for locked items). Coil runs this off the main thread. */
class ThumbnailFetcher(
    private val resolver: ContentResolver,
    private val data: Thumbnail,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val side = thumbnailBucket(options.longestSide())
        val size = AndroidSize(side, side)
        val bitmap = if (data.uri.scheme == ContentResolver.SCHEME_FILE) {
            val file = File(requireNotNull(data.uri.path))
            if (data.isVideo) ThumbnailUtils.createVideoThumbnail(file, size, null) else ThumbnailUtils.createImageThumbnail(file, size, null)
        } else {
            resolver.loadThumbnail(data.uri, size, null)
        }
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    /** Hands Coil a [ThumbnailFetcher] for each [Thumbnail] it's asked to draw. */
    class Factory(private val resolver: ContentResolver) : Fetcher.Factory<Thumbnail> {
        override fun create(data: Thumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbnailFetcher(resolver, data, options)
    }
}

/** The longer side Coil wants the image drawn at, or 512 when the layout doesn't say. */
private fun Options.longestSide(): Int = maxOf(size.width.pxOrElse { 512 }, size.height.pxOrElse { 512 })

/**
 * A round person picture: the part of photo [uri] around a face whose box is given as fractions
 * of the photo ([left], [top], [width], [height]), from Android's thumbnail of the photo.
 */
data class FaceCrop(val uri: Uri, val modifiedAt: Long, val left: Float, val top: Float, val width: Float, val height: Float)

/** Lets Coil keep [FaceCrop]s in its memory cache. */
class FaceCropKeyer : Keyer<FaceCrop> {
    override fun key(data: FaceCrop, options: Options): String =
        "face:${data.uri}:${data.modifiedAt}:${data.left},${data.top},${data.width},${data.height}"
}

/** Cuts a [FaceCrop] out of the photo's thumbnail: a square around the face with some room around it. */
class FaceCropFetcher(private val resolver: ContentResolver, private val data: FaceCrop) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val thumb = resolver.loadThumbnail(data.uri, AndroidSize(512, 512), null)
        val cx = (data.left + data.width / 2) * thumb.width
        val cy = (data.top + data.height / 2) * thumb.height
        val side = (maxOf(data.width * thumb.width, data.height * thumb.height) * 1.8f)
            .coerceAtMost(minOf(thumb.width, thumb.height).toFloat()).coerceAtLeast(1f)
        val x = (cx - side / 2).coerceIn(0f, thumb.width - side).toInt()
        val y = (cy - side / 2).coerceIn(0f, thumb.height - side).toInt()
        val crop = Bitmap.createBitmap(thumb, x, y, side.toInt().coerceAtMost(thumb.width - x), side.toInt().coerceAtMost(thumb.height - y))
        return ImageFetchResult(image = crop.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    /** Hands Coil a [FaceCropFetcher] for each [FaceCrop]. */
    class Factory(private val resolver: ContentResolver) : Fetcher.Factory<FaceCrop> {
        override fun create(data: FaceCrop, options: Options, imageLoader: ImageLoader): Fetcher = FaceCropFetcher(resolver, data)
    }
}
