package app.roll.gallery.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * The app's view of one photo or video, as Android's shared media store describes it.
 * The app keeps no database of its own: every [Media] is read fresh from the store.
 */
data class Media(
    /** The store's row id; unique across photos and videos. */
    val id: Long,
    val isVideo: Boolean,
    val mimeType: String,
    /** When it was taken, in milliseconds since 1970; the file's change time if unknown. */
    val takenAt: Long,
    /** When the file last changed, in seconds; part of the thumbnail cache key. */
    val modifiedAt: Long,
    val width: Int,
    val height: Int,
    /** Video length in milliseconds; 0 for photos. */
    val durationMs: Long,
    /** The folder it sits in, which the app shows as an album. */
    val albumId: Long,
    val albumName: String,
    /** The folder path inside shared storage, e.g. "DCIM/Camera/". */
    val path: String,
    val fileName: String,
    val sizeBytes: Long,
    val isFavorite: Boolean,
    val isTrashed: Boolean,
    /** For trashed items: when Android deletes it for good, in seconds; 0 otherwise. */
    val expiresAt: Long,
    /** Set for a file another app handed over (it isn't in the media store); null otherwise. */
    val externalUri: Uri? = null,
) {
    /** Where to open the file, e.g. content://media/external/images/media/42. */
    val uri: Uri
        get() = externalUri ?: ContentUris.withAppendedId(
            if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            id,
        )
}

/**
 * Everything Gallery can show: the media store's items, split into the library and the trash,
 * plus the Locked folder's [locked] items (kept apart, and only shown once unlocked).
 */
data class Library(val items: List<Media>, val trash: List<Media>, val locked: List<Media> = emptyList()) {
    /** One album's items, newest first. */
    fun album(albumId: Long): List<Media> = items.filter { it.albumId == albumId }

    /** Favorited items, newest first. */
    val favorites: List<Media> get() = items.filter { it.isFavorite }

    /** Videos only, newest first. */
    val videos: List<Media> get() = items.filter { it.isVideo }
}

/** The places a set of photos can come from, so the viewer can page through the same set. */
sealed interface MediaSet {
    /** Every photo and video, as on the Photos tab. */
    data object All : MediaSet
    data object Favorites : MediaSet
    data object Videos : MediaSet
    data object Trash : MediaSet
    data object Locked : MediaSet
    data class Album(val id: Long) : MediaSet
    /** Today's "On this day" photos from [yearsAgo] years ago (see [memoriesFor]). */
    data class Memory(val yearsAgo: Int) : MediaSet
    /** The photos of one person, by the person's key (Gallery Plus). */
    data class Person(val key: String) : MediaSet
    /** The results of a search for [query] (see [searchMedia]). */
    data class Search(val query: String) : MediaSet
}

/**
 * The items of [set], newest first. [words], [locale], [zone] and [photoText] are only used by
 * searches, to read the query the way the search screen did; [personPhotos] gives a person's
 * photos by their key.
 */
fun Library.itemsIn(
    set: MediaSet,
    words: SearchWords = SearchWords.ENGLISH,
    locale: Locale = Locale.getDefault(),
    zone: ZoneId = ZoneId.systemDefault(),
    photoText: (Long) -> String? = { null },
    personPhotos: (String) -> Set<Long> = { emptySet() },
): List<Media> = when (set) {
    MediaSet.All -> items
    MediaSet.Favorites -> favorites
    MediaSet.Videos -> videos
    MediaSet.Trash -> trash
    MediaSet.Locked -> locked
    is MediaSet.Album -> album(set.id)
    is MediaSet.Search -> searchMedia(items, set.query, words, locale, zone, photoText)
    is MediaSet.Person -> personPhotos(set.key).let { ids -> items.filter { it.id in ids } }
    is MediaSet.Memory -> memoriesFor(items, LocalDate.now(zone), zone).firstOrNull { it.yearsAgo == set.yearsAgo }?.items.orEmpty()
}
