package app.roll.gallery.data

/**
 * The choices behind the home-screen widgets, kept apart from Android's widget plumbing so they
 * can be tested: which memory the Memories widget shows, and which photo a photo frame shows
 * now. The widgets themselves are in `widgets/`.
 */

/** Where a photo frame widget takes its photos from; chosen when the widget is placed. */
sealed interface FrameSource {
    /** Favorite photos (every photo while there are none). The default. */
    data object Favorites : FrameSource
    /** Every photo shown in the Photos tab. */
    data object All : FrameSource
    /** The photos of one album, by its folder id. */
    data class Album(val id: Long) : FrameSource
}

/** [FrameSource] as text, for keeping a widget's choice in its settings. */
fun FrameSource.encode(): String = when (this) {
    FrameSource.Favorites -> "favorites"
    FrameSource.All -> "all"
    is FrameSource.Album -> "album:$id"
}

/** The [FrameSource] saved as [text]; Favorites when there's nothing saved or it can't be read. */
fun decodeFrameSource(text: String?): FrameSource = when {
    text == "all" -> FrameSource.All
    text != null && text.startsWith("album:") -> text.removePrefix("album:").toLongOrNull()?.let { FrameSource.Album(it) } ?: FrameSource.Favorites
    else -> FrameSource.Favorites
}

/** The photos a frame picks from, and the set the viewer pages through when the frame is tapped. */
data class FramePhotos(val set: MediaSet, val items: List<Media>)

/**
 * The photos (never videos) a frame set to [source] picks from, newest first. Favorites with no
 * favorites yet, and an album that has gone or holds no photos, fall back to every photo in the
 * Photos tab, so a frame never sits empty while there are photos to show. Only the media store's
 * photos are looked at: the Locked folder and the trash never reach a widget.
 */
fun framePhotos(library: Library, source: FrameSource): FramePhotos {
    fun photos(list: List<Media>) = list.filter { !it.isVideo && !it.isTrashed }
    val chosen = when (source) {
        FrameSource.Favorites -> FramePhotos(MediaSet.Favorites, photos(library.favorites))
        FrameSource.All -> FramePhotos(MediaSet.All, photos(library.timeline))
        is FrameSource.Album -> FramePhotos(MediaSet.Album(source.id), photos(library.album(source.id)))
    }
    return if (chosen.items.isEmpty()) FramePhotos(MediaSet.All, photos(library.timeline)) else chosen
}

/** How often a photo frame moves on to another photo, in hours. Matches the widgets' update period. */
const val FRAME_CHANGE_HOURS = 3L

/** Which stretch of [FRAME_CHANGE_HOURS] [nowMillis] falls in; the frame keeps one photo per stretch. */
fun frameSlot(nowMillis: Long): Long = nowMillis / (FRAME_CHANGE_HOURS * 60 * 60 * 1000)

/**
 * The photo a frame shows during [slot]. Steps through [items] by a large prime, so the next
 * slots show photos from all over the set (not just the next one along) and every photo comes
 * up once before any repeats. [widgetId] starts each frame at a different place, so two frames
 * on the same set don't show the same photo.
 */
fun framePick(items: List<Media>, widgetId: Int, slot: Long): Media? {
    if (items.isEmpty()) return null
    val n = items.size.toLong()
    // Any step that shares no factor with n visits every photo; the prime only fails when it divides n.
    val step = if (n % FRAME_STEP == 0L) 1L else FRAME_STEP
    return items[Math.floorMod(slot * step + widgetId, n).toInt()]
}

private const val FRAME_STEP = 7919L

/**
 * The memory the Memories widget shows: On this day first (the most recent year), then Recent
 * highlights, then a busy day from the past year. Null when there are no memories today, and the
 * widget shows a favorite instead.
 */
fun widgetMemory(memories: List<Memory>): Memory? =
    memories.firstOrNull { !it.recent && it.day == null }
        ?: memories.firstOrNull { it.recent }
        ?: memories.firstOrNull()
