package app.roll.gallery.data

/**
 * A folder of photos, shown as an album: its cover is the newest item unless one was chosen.
 * [pinned] albums come first; [inPhotos] is false for folders kept out of the Photos grid.
 */
data class Album(
    val id: Long,
    val name: String,
    val cover: Media,
    val count: Int,
    val path: String,
    val pinned: Boolean = false,
    val inPhotos: Boolean = true,
)

/** How albums are ordered after the pinned ones. */
enum class AlbumSort {
    /** Camera, then screenshots, then the rest by their newest photo. */
    Recent,

    /** By name, A to Z. */
    Name,
}

/**
 * The choices made about albums, kept in Gallery's settings: folders kept out of the Photos grid
 * ([hidden]), albums pinned to the top in this order ([pinned]), chosen covers (album id to photo
 * id, [covers]) and the [sort] order.
 */
data class AlbumPrefs(
    val hidden: Set<Long> = emptySet(),
    val pinned: List<Long> = emptyList(),
    val covers: Map<Long, Long> = emptyMap(),
    val sort: AlbumSort = AlbumSort.Recent,
)

/**
 * Turns [items] (newest first) into albums, one per folder, following [prefs]: pinned albums
 * first, in the order they were pinned, then the rest by [AlbumPrefs.sort]. Ordered by recent,
 * the camera folder comes first and screenshots second, as on most phones, then the rest by
 * their newest photo. A chosen cover is used while it's still in the album.
 */
fun buildAlbums(items: List<Media>, prefs: AlbumPrefs = AlbumPrefs()): List<Album> {
    val byFolder = LinkedHashMap<Long, MutableList<Media>>()
    for (item in items) byFolder.getOrPut(item.albumId) { ArrayList() } += item
    val albums = byFolder.map { (id, media) ->
        val newest = media.first()
        val chosen = prefs.covers[id]?.let { coverId -> media.firstOrNull { it.id == coverId } }
        Album(
            id = id,
            name = newest.albumName.ifBlank { newest.path.trimEnd('/').substringAfterLast('/') },
            cover = chosen ?: newest,
            count = media.size,
            path = newest.path,
            pinned = id in prefs.pinned,
            inPhotos = id !in prefs.hidden,
        )
    }
    val rest = when (prefs.sort) {
        AlbumSort.Recent -> compareBy<Album> { albumRank(it.path) }.thenByDescending { byFolder.getValue(it.id).first().takenAt }
        AlbumSort.Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }
    val (pinned, others) = albums.partition { it.pinned }
    return pinned.sortedBy { prefs.pinned.indexOf(it.id) } + others.sortedWith(rest)
}

/**
 * Whether [path] is a screenshots folder: Android's own (Pictures/Screenshots or
 * DCIM/Screenshots) or one of the same name elsewhere, as some phones and apps use.
 */
fun isScreenshotFolder(path: String): Boolean =
    path.lowercase().trimEnd('/').substringAfterLast('/') in setOf("screenshots", "screen recordings", "screenrecords")

/** 0 for the camera folder, 1 for screenshots, 2 for everything else. */
fun albumRank(path: String): Int {
    val p = path.lowercase().trimEnd('/')
    return when (p) {
        "dcim/camera" -> 0
        "pictures/screenshots", "dcim/screenshots" -> 1
        else -> 2
    }
}
