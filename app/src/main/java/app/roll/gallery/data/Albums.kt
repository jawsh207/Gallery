package app.roll.gallery.data

/** A folder of photos, shown as an album: its newest item is the cover. */
data class Album(val id: Long, val name: String, val cover: Media, val count: Int, val path: String)

/**
 * Turns [items] (newest first) into albums, one per folder. The camera folder comes first and
 * screenshots second, as on most phones; the rest are ordered by their newest photo.
 */
fun buildAlbums(items: List<Media>): List<Album> {
    val byFolder = LinkedHashMap<Long, MutableList<Media>>()
    for (item in items) byFolder.getOrPut(item.albumId) { ArrayList() } += item
    val albums = byFolder.map { (id, media) ->
        val cover = media.first()
        Album(id = id, name = cover.albumName.ifBlank { cover.path.trimEnd('/').substringAfterLast('/') }, cover = cover, count = media.size, path = cover.path)
    }
    return albums.sortedWith(compareBy<Album> { albumRank(it.path) }.thenByDescending { it.cover.takenAt })
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
