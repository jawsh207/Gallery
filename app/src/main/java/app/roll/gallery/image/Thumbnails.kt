package app.roll.gallery.image

import android.content.ContentResolver
import android.net.Uri
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
 * new thumbnail.
 */
data class Thumbnail(val uri: Uri, val modifiedAt: Long)

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

/** Loads a [Thumbnail] through Android's own thumbnail cache. Coil runs this off the main thread. */
class ThumbnailFetcher(
    private val resolver: ContentResolver,
    private val data: Thumbnail,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val side = thumbnailBucket(options.longestSide())
        val bitmap = resolver.loadThumbnail(data.uri, AndroidSize(side, side), null)
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
