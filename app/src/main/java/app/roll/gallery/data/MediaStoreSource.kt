package app.roll.gallery.data

import android.content.ContentResolver
import android.content.IntentSender
import android.os.Bundle
import android.provider.BaseColumns
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns

/**
 * Reads photos and videos from Android's media store, and builds the requests that change
 * them (favorite, trash, restore, delete, write access for moving). Android doesn't let an app change other apps'
 * files directly: each change is a request the system confirms with the user, unless the
 * app has "Media management" access, in which case the system approves it without asking.
 */
class MediaStoreSource(private val resolver: ContentResolver) {

    /**
     * Every photo and video, trashed ones included, newest first. One query for both, so the
     * library and the trash always agree. Files still being written (pending) are left out.
     */
    fun load(): Library {
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "$MEDIA_TYPE = ? OR $MEDIA_TYPE = ?")
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(MEDIA_TYPE_IMAGE.toString(), MEDIA_TYPE_VIDEO.toString()),
            )
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        }
        val all = ArrayList<Media>()
        val zone = java.time.ZoneId.systemDefault()
        resolver.query(FILES, PROJECTION, args, null)?.use { c ->
            val id = c.getColumnIndexOrThrow(BaseColumns._ID)
            val type = c.getColumnIndexOrThrow(MEDIA_TYPE)
            val mime = c.getColumnIndexOrThrow(MediaColumns.MIME_TYPE)
            val taken = c.getColumnIndexOrThrow(MediaColumns.DATE_TAKEN)
            val modified = c.getColumnIndexOrThrow(MediaColumns.DATE_MODIFIED)
            val added = c.getColumnIndexOrThrow(MediaColumns.DATE_ADDED)
            val width = c.getColumnIndexOrThrow(MediaColumns.WIDTH)
            val height = c.getColumnIndexOrThrow(MediaColumns.HEIGHT)
            val orientation = c.getColumnIndexOrThrow(MediaColumns.ORIENTATION)
            val duration = c.getColumnIndexOrThrow(MediaColumns.DURATION)
            val bucketId = c.getColumnIndexOrThrow(MediaColumns.BUCKET_ID)
            val bucketName = c.getColumnIndexOrThrow(MediaColumns.BUCKET_DISPLAY_NAME)
            val path = c.getColumnIndexOrThrow(MediaColumns.RELATIVE_PATH)
            val name = c.getColumnIndexOrThrow(MediaColumns.DISPLAY_NAME)
            val size = c.getColumnIndexOrThrow(MediaColumns.SIZE)
            val favorite = c.getColumnIndexOrThrow(MediaColumns.IS_FAVORITE)
            val trashed = c.getColumnIndexOrThrow(MediaColumns.IS_TRASHED)
            val expires = c.getColumnIndexOrThrow(MediaColumns.DATE_EXPIRES)
            while (c.moveToNext()) {
                val rowId = c.getLong(id)
                val isVideo = c.getInt(type) == MEDIA_TYPE_VIDEO
                val modifiedSec = c.getLong(modified)
                // Width and height are stored as the sensor wrote them; a photo turned on its
                // side by its orientation tag shows the other way round.
                val sideways = c.getInt(orientation).let { it == 90 || it == 270 }
                val w = c.getInt(width)
                val h = c.getInt(height)
                val fileName = c.getString(name).orEmpty()
                all += Media(
                    id = rowId,
                    isVideo = isVideo,
                    mimeType = c.getString(mime) ?: if (isVideo) "video/*" else "image/*",
                    // The date inside the photo (which Android reads), else one in its name,
                    // else when the file last changed.
                    takenAt = c.getLong(taken).takeIf { it > 0 }
                        ?: dateFromName(fileName, zone)
                        ?: (modifiedSec * 1000),
                    modifiedAt = modifiedSec,
                    addedAt = c.getLong(added),
                    width = if (sideways) h else w,
                    height = if (sideways) w else h,
                    durationMs = if (isVideo) c.getLong(duration) else 0,
                    albumId = c.getLong(bucketId),
                    albumName = c.getString(bucketName).orEmpty(),
                    path = c.getString(path).orEmpty(),
                    fileName = fileName,
                    sizeBytes = c.getLong(size),
                    isFavorite = c.getInt(favorite) == 1,
                    isTrashed = c.getInt(trashed) == 1,
                    expiresAt = c.getLong(expires),
                )
            }
        }
        val sorted = all.sortedWith(NEWEST_FIRST)
        return Library(items = sorted.filter { !it.isTrashed }, trash = sorted.filter { it.isTrashed })
    }

    /** A request to favorite or unfavorite [items]. */
    fun favoriteRequest(items: List<Media>, favorite: Boolean): IntentSender =
        MediaStore.createFavoriteRequest(resolver, items.map { it.uri }, favorite).intentSender

    /** A request to move [items] to the trash, or back out of it. Android empties trash after 30 days. */
    fun trashRequest(items: List<Media>, trash: Boolean): IntentSender =
        MediaStore.createTrashRequest(resolver, items.map { it.uri }, trash).intentSender

    /** A request for write access to [items], so they can be moved to another folder. */
    fun writeRequest(items: List<Media>): IntentSender =
        MediaStore.createWriteRequest(resolver, items.map { it.uri }).intentSender

    /** A request to delete [items] for good. */
    fun deleteRequest(items: List<Media>): IntentSender =
        MediaStore.createDeleteRequest(resolver, items.map { it.uri }).intentSender

    private companion object {
        val FILES = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        const val MEDIA_TYPE = MediaStore.Files.FileColumns.MEDIA_TYPE
        const val MEDIA_TYPE_IMAGE = MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
        const val MEDIA_TYPE_VIDEO = MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
        val PROJECTION = arrayOf(
            BaseColumns._ID, MEDIA_TYPE, MediaColumns.MIME_TYPE, MediaColumns.DATE_TAKEN,
            MediaColumns.DATE_MODIFIED, MediaColumns.DATE_ADDED, MediaColumns.WIDTH, MediaColumns.HEIGHT,
            MediaColumns.ORIENTATION, MediaColumns.DURATION, MediaColumns.BUCKET_ID,
            MediaColumns.BUCKET_DISPLAY_NAME, MediaColumns.RELATIVE_PATH, MediaColumns.DISPLAY_NAME,
            MediaColumns.SIZE, MediaColumns.IS_FAVORITE, MediaColumns.IS_TRASHED,
            MediaColumns.DATE_EXPIRES,
        )
        val NEWEST_FIRST = compareByDescending<Media> { it.takenAt }.thenByDescending { it.id }
    }
}
