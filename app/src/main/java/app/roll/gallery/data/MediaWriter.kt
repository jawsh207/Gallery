package app.roll.gallery.data

import android.app.WallpaperManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns

/**
 * Changes that write files: copying and moving between albums, and setting a wallpaper.
 * Runs on a background thread; each call reports how many items failed, so one bad file
 * doesn't stop the rest.
 */
class MediaWriter(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    /**
     * Copies [items] into the folder [path] as new files owned by Gallery, keeping their names
     * (Android adds " (1)" on a clash) and when they were taken. Each copy stays hidden from
     * other apps ("pending") until it's fully written, so a half-copied file never shows up.
     */
    fun copy(items: List<Media>, path: String): Int = items.count { item ->
        runCatching {
            val collection = if (item.isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val values = ContentValues().apply {
                put(MediaColumns.DISPLAY_NAME, item.fileName)
                put(MediaColumns.MIME_TYPE, item.mimeType)
                put(MediaColumns.RELATIVE_PATH, path)
                put(MediaColumns.DATE_TAKEN, item.takenAt)
                put(MediaColumns.IS_PENDING, 1)
            }
            val target = resolver.insert(collection, values) ?: error("insert refused")
            try {
                resolver.openInputStream(item.uri).use { input ->
                    resolver.openOutputStream(target).use { output ->
                        requireNotNull(input).copyTo(requireNotNull(output))
                    }
                }
                resolver.update(target, ContentValues().apply { put(MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(target, null, null)
                throw e
            }
        }.isFailure
    }

    /**
     * Moves [items] into the folder [path] by changing where the media store keeps them; the
     * file itself moves and keeps its id, favorite and dates. Needs write access to each file
     * first (a write request), except for files Gallery made.
     */
    fun move(items: List<Media>, path: String): Int = items.count { item ->
        runCatching {
            val values = ContentValues().apply { put(MediaColumns.RELATIVE_PATH, path) }
            check(resolver.update(item.uri, values, null, null) == 1)
        }.isFailure
    }

    /**
     * Sets [media] as the wallpaper on the home screen, the lock screen, or both ([which] is a
     * combination of [WallpaperManager.FLAG_SYSTEM] and [WallpaperManager.FLAG_LOCK]). Android
     * crops it to fit the screen. Returns true if it worked.
     */
    fun setWallpaper(media: Media, which: Int): Boolean = runCatching {
        resolver.openInputStream(media.uri).use { input ->
            WallpaperManager.getInstance(context).setStream(requireNotNull(input), null, true, which)
        }
    }.isSuccess
}
