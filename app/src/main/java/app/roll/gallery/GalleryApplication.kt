package app.roll.gallery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import app.roll.gallery.image.ThumbnailFetcher
import app.roll.gallery.image.ThumbnailKeyer

/**
 * The app process. Its only job is to set up the image loader every screen shares, taught to
 * load grid thumbnails from Android's thumbnail cache (see [ThumbnailFetcher]).
 */
class GalleryApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(ThumbnailKeyer())
                add(ThumbnailFetcher.Factory(contentResolver))
            }
            .build()
}
