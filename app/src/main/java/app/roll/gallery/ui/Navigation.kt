package app.roll.gallery.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import app.roll.gallery.data.MediaSet
import java.net.URLDecoder
import java.net.URLEncoder

/** The app's screens. Home holds the bottom tabs; the others cover them. */
sealed interface Screen {
    data object Home : Screen
    /** A grid of one set: an album, Favorites or Videos. */
    data class Grid(val set: MediaSet) : Screen
    data object Trash : Screen
    data object Search : Screen
    data object Settings : Screen
    /** Choosing the photos for a new album called [name]. */
    data class NewAlbum(val name: String) : Screen
    /** One photo or video full screen, paging through the rest of [set]. */
    data class Viewer(val set: MediaSet, val id: Long) : Screen
}

/** [MediaSet] as text, for saving the back stack. */
fun MediaSet.encode(): String = when (this) {
    MediaSet.All -> "all"
    MediaSet.Favorites -> "favorites"
    MediaSet.Videos -> "videos"
    MediaSet.Trash -> "trash"
    is MediaSet.Album -> "album:$id"
    // Encoded, so a query can't contain the "|" that separates the saved parts.
    is MediaSet.Search -> "search:" + URLEncoder.encode(query, "UTF-8")
}

/** The [MediaSet] [text] was saved as, or null if it can't be read. */
fun decodeMediaSet(text: String): MediaSet? = when {
    text == "all" -> MediaSet.All
    text == "favorites" -> MediaSet.Favorites
    text == "videos" -> MediaSet.Videos
    text == "trash" -> MediaSet.Trash
    text.startsWith("album:") -> text.removePrefix("album:").toLongOrNull()?.let { MediaSet.Album(it) }
    text.startsWith("search:") -> MediaSet.Search(URLDecoder.decode(text.removePrefix("search:"), "UTF-8"))
    else -> null
}

private fun Screen.encode(): String = when (this) {
    Screen.Home -> "home"
    Screen.Trash -> "trash"
    Screen.Search -> "search"
    Screen.Settings -> "settings"
    is Screen.NewAlbum -> "newalbum|" + URLEncoder.encode(name, "UTF-8")
    is Screen.Grid -> "grid|${set.encode()}"
    is Screen.Viewer -> "viewer|$id|${set.encode()}"
}

private fun decode(text: String): Screen? {
    val parts = text.split('|')
    return when (parts[0]) {
        "home" -> Screen.Home
        "trash" -> Screen.Trash
        "search" -> Screen.Search
        "settings" -> Screen.Settings
        "newalbum" -> parts.getOrNull(1)?.let { Screen.NewAlbum(URLDecoder.decode(it, "UTF-8")) }
        "grid" -> parts.getOrNull(1)?.let(::decodeMediaSet)?.let { Screen.Grid(it) }
        "viewer" -> {
            val id = parts.getOrNull(1)?.toLongOrNull()
            val set = parts.getOrNull(2)?.let(::decodeMediaSet)
            if (id != null && set != null) Screen.Viewer(set, id) else null
        }
        else -> null
    }
}

/** The back stack, kept across rotation and process death. Always starts with Home. */
@Composable
fun rememberBackStack(): SnapshotStateList<Screen> = rememberSaveable(
    saver = listSaver(
        save = { stack -> stack.map { it.encode() } },
        restore = { saved -> saved.mapNotNull(::decode).ifEmpty { listOf(Screen.Home) }.toMutableStateList() },
    ),
) { listOf<Screen>(Screen.Home).toMutableStateList() }
