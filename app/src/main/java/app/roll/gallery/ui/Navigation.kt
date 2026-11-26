package app.roll.gallery.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import app.roll.gallery.data.MediaSet
import app.roll.gallery.data.Creation
import java.net.URLDecoder
import java.net.URLEncoder

/** The app's screens. Home holds the bottom tabs; the others cover them. */
sealed interface Screen {
    data object Home : Screen
    /** A grid of one set: an album, Favorites, Videos, Screenshots or Recently Added. */
    data class Grid(val set: MediaSet) : Screen
    data object Trash : Screen
    data object Search : Screen
    data object Settings : Screen
    data object Duplicates : Screen
    data object Locked : Screen
    /** One person's photos, by the person's key. */
    data class Person(val key: String) : Screen
    /** Choosing which folders show in the Photos grid. */
    data object PhotoFolders : Screen

    /** The editor, for the photo with this id; opened on its looks when [moods]. */
    data class Editor(val id: Long, val moods: Boolean = false) : Screen
    /** Choosing the photos (and, for a video, clips) for something to make on the Create tab. */
    data class Choose(val kind: Creation) : Screen
    /** Making [kind] from the chosen photos and videos, by id, in order. */
    data class Make(val kind: Creation, val ids: List<Long>) : Screen
    /** Choosing the photos for a new album called [name]. */
    data class NewAlbum(val name: String) : Screen
    /** One photo or video full screen, paging through the rest of [set]; as a slideshow when [slideshow]. */
    data class Viewer(val set: MediaSet, val id: Long, val slideshow: Boolean = false) : Screen
}

/** [MediaSet] as text, for saving the back stack. */
fun MediaSet.encode(): String = when (this) {
    MediaSet.All -> "all"
    MediaSet.Favorites -> "favorites"
    MediaSet.Videos -> "videos"
    MediaSet.Trash -> "trash"
    MediaSet.Locked -> "locked"
    MediaSet.Screenshots -> "screenshots"
    MediaSet.RecentlyAdded -> "recent"
    is MediaSet.Album -> "album:$id"
    // Encoded, so a query can't contain the "|" that separates the saved parts.
    is MediaSet.Search -> "search:" + URLEncoder.encode(query, "UTF-8")
    is MediaSet.Memory -> "memory:$key"
    is MediaSet.Thing -> "thing:$name"
    is MediaSet.Person -> "person:" + URLEncoder.encode(key, "UTF-8")
}

/** The [MediaSet] [text] was saved as, or null if it can't be read. */
fun decodeMediaSet(text: String): MediaSet? = when {
    text == "all" -> MediaSet.All
    text == "favorites" -> MediaSet.Favorites
    text == "videos" -> MediaSet.Videos
    text == "trash" -> MediaSet.Trash
    text == "locked" -> MediaSet.Locked
    text == "screenshots" -> MediaSet.Screenshots
    text == "recent" -> MediaSet.RecentlyAdded
    text.startsWith("album:") -> text.removePrefix("album:").toLongOrNull()?.let { MediaSet.Album(it) }
    text.startsWith("person:") -> MediaSet.Person(URLDecoder.decode(text.removePrefix("person:"), "UTF-8"))
    text.startsWith("memory:") -> MediaSet.Memory(text.removePrefix("memory:"))
    text.startsWith("thing:") -> MediaSet.Thing(text.removePrefix("thing:"))
    text.startsWith("search:") -> MediaSet.Search(URLDecoder.decode(text.removePrefix("search:"), "UTF-8"))
    else -> null
}

/** [Screen] as text, for saving the back stack and for widgets and shortcuts that open one. */
fun Screen.encode(): String = when (this) {
    Screen.Home -> "home"
    Screen.Trash -> "trash"
    Screen.Search -> "search"
    Screen.Settings -> "settings"
    Screen.PhotoFolders -> "folders"
    Screen.Duplicates -> "duplicates"
    Screen.Locked -> "lockedfolder"
    is Screen.Person -> "person|" + URLEncoder.encode(key, "UTF-8")
    is Screen.Editor -> "editor|$id|$moods"
    is Screen.Choose -> "choose|${kind.name}"
    is Screen.Make -> "make|${kind.name}|${ids.joinToString(",")}"
    is Screen.NewAlbum -> "newalbum|" + URLEncoder.encode(name, "UTF-8")
    is Screen.Grid -> "grid|${set.encode()}"
    is Screen.Viewer -> "viewer|$id|${set.encode()}|$slideshow"
}

/** The [Screen] [text] was saved as (see [Screen.encode]), or null if it can't be read. */
fun decodeScreen(text: String): Screen? {
    val parts = text.split('|')
    return when (parts[0]) {
        "home" -> Screen.Home
        "trash" -> Screen.Trash
        "search" -> Screen.Search
        "settings" -> Screen.Settings
        "folders" -> Screen.PhotoFolders
        "duplicates" -> Screen.Duplicates
        "lockedfolder" -> Screen.Locked
        "person" -> parts.getOrNull(1)?.let { Screen.Person(URLDecoder.decode(it, "UTF-8")) }
        "editor" -> parts.getOrNull(1)?.toLongOrNull()?.let { Screen.Editor(it, moods = parts.getOrNull(2) == "true") }
        "choose" -> parts.getOrNull(1)?.let { name -> Creation.entries.firstOrNull { it.name == name } }?.let { Screen.Choose(it) }
        "make" -> {
            val kind = parts.getOrNull(1)?.let { name -> Creation.entries.firstOrNull { it.name == name } }
            val ids = parts.getOrNull(2)?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty()
            if (kind != null && ids.isNotEmpty()) Screen.Make(kind, ids) else null
        }
        "newalbum" -> parts.getOrNull(1)?.let { Screen.NewAlbum(URLDecoder.decode(it, "UTF-8")) }
        "grid" -> parts.getOrNull(1)?.let(::decodeMediaSet)?.let { Screen.Grid(it) }
        "viewer" -> {
            val id = parts.getOrNull(1)?.toLongOrNull()
            val set = parts.getOrNull(2)?.let(::decodeMediaSet)
            if (id != null && set != null) Screen.Viewer(set, id, slideshow = parts.getOrNull(3) == "true") else null
        }
        else -> null
    }
}

/** The back stack, kept across rotation and process death. Always starts with Home. */
@Composable
fun rememberBackStack(): SnapshotStateList<Screen> = rememberSaveable(
    saver = listSaver(
        save = { stack -> stack.map { it.encode() } },
        restore = { saved -> saved.mapNotNull(::decodeScreen).ifEmpty { listOf(Screen.Home) }.toMutableStateList() },
    ),
) { listOf<Screen>(Screen.Home).toMutableStateList() }
