package app.roll.gallery.ui.collections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.roll.gallery.R
import app.roll.gallery.ui.locked.rememberMoveToLocked
import app.roll.gallery.ui.components.BarAction
import androidx.compose.material.icons.outlined.Lock
import app.roll.gallery.data.GridZoom
import app.roll.gallery.data.Library
import app.roll.gallery.data.Media
import app.roll.gallery.data.MediaSet
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.Box
import app.roll.gallery.data.GroupBy
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Schedule
import app.roll.gallery.data.itemsIn
import app.roll.gallery.ui.GalleryViewModel
import app.roll.gallery.ui.components.EmptyState
import app.roll.gallery.ui.components.SelectionBottomBar
import app.roll.gallery.ui.components.SelectionTopBar
import app.roll.gallery.ui.components.rememberRequestLauncher
import app.roll.gallery.ui.libraryActions
import app.roll.gallery.ui.photos.MediaGrid
import app.roll.gallery.ui.trashActions

/** The title for a set's own screen: the album's name, "Favorites" or "Videos". */
@Composable
fun MediaSet.title(library: Library): String = when (this) {
    MediaSet.All -> stringResource(R.string.tab_photos)
    MediaSet.Favorites -> stringResource(R.string.favorites)
    MediaSet.Videos -> stringResource(R.string.videos)
    MediaSet.Trash -> stringResource(R.string.trash)
    MediaSet.Locked -> stringResource(R.string.locked_folder)
    MediaSet.Screenshots -> stringResource(R.string.screenshots)
    MediaSet.RecentlyAdded -> stringResource(R.string.recently_added)
    is MediaSet.Album -> library.album(id).firstOrNull()?.albumName.orEmpty()
    is MediaSet.Search -> query
    is MediaSet.Memory -> when {
        key == "recent" -> stringResource(R.string.memory_recent)
        key.startsWith("day:") -> key.removePrefix("day:").let { day ->
            runCatching {
                java.time.LocalDate.parse(day).format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
            }.getOrDefault(day)
        }
        else -> key.removePrefix("year:").toIntOrNull()?.let { pluralStringResource(R.plurals.years_ago, it, it) }.orEmpty()
    }
    is MediaSet.Person -> stringResource(R.string.person)
}

/**
 * One album, or Favorites, Videos, Screenshots or Recently Added, as its own grid, with the same
 * selecting as the Photos tab. Recently Added is grouped by the day things arrived.
 */
@Composable
fun SetScreen(
    viewModel: GalleryViewModel,
    library: Library,
    set: MediaSet,
    onBack: () -> Unit,
    onOpen: (Media) -> Unit,
) {
    val items = remember(library, set) { library.itemsIn(set) }
    var zoom by rememberSaveable { mutableStateOf(GridZoom.Days) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val selected = items.filter { it.id in selectedIds }
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selectedIds = emptySet() }
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val actions = libraryActions(viewModel, library, selected, onDone = { selectedIds = emptySet() })
    val moveToLocked = rememberMoveToLocked(viewModel) { selectedIds = emptySet() }
    val lockMenu = BarAction(Icons.Outlined.Lock, stringResource(R.string.locked_move_in)) { moveToLocked(selected) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            if (selecting) {
                SelectionTopBar(selected.size, menu = listOf(lockMenu)) { selectedIds = emptySet() }
            } else {
                TopAppBar(
                    title = { Text(set.title(library)) },
                    navigationIcon = { BackButton(onBack) },
                    actions = { if (set is MediaSet.Album) AlbumMenu(viewModel, library, set.id) },
                    scrollBehavior = scroll,
                )
            }
        },
        bottomBar = { if (selecting) SelectionBottomBar(actions) },
    ) { padding ->
        if (items.isEmpty()) {
            val (icon, title, body) = when (set) {
                MediaSet.Favorites -> Triple(Icons.Outlined.FavoriteBorder, R.string.no_favorites_title, R.string.no_favorites_body)
                MediaSet.Videos -> Triple(Icons.Outlined.Videocam, R.string.no_videos_title, R.string.no_videos_body)
                MediaSet.Screenshots -> Triple(Icons.Outlined.Screenshot, R.string.no_screenshots_title, R.string.no_screenshots_body)
                MediaSet.RecentlyAdded -> Triple(Icons.Outlined.Schedule, R.string.no_recent_title, R.string.no_recent_body)
                else -> Triple(Icons.Outlined.PhotoAlbum, R.string.album_empty_title, R.string.album_empty_body)
            }
            EmptyState(icon, stringResource(title), stringResource(body), Modifier.padding(padding))
            return@Scaffold
        }
        MediaGrid(
            items = items,
            zoom = zoom,
            onZoomChange = { zoom = it },
            selection = selectedIds,
            onSelectionChange = { selectedIds = it },
            onOpen = onOpen,
            contentPadding = padding,
            groupBy = if (set == MediaSet.RecentlyAdded) GroupBy.Added else GroupBy.Taken,
        )
    }
}

/**
 * The trash: photos and videos moved there from any app, until Android deletes them for good
 * 30 days later. They can be put back or deleted now, one by one, by selection, or all at once.
 */
@Composable
fun TrashScreen(viewModel: GalleryViewModel, library: Library, onBack: () -> Unit, onOpen: (Media) -> Unit) {
    val items = library.trash
    var zoom by rememberSaveable { mutableStateOf(GridZoom.Days) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val selected = items.filter { it.id in selectedIds }
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selectedIds = emptySet() }
    val actions = trashActions(viewModel, selected) { selectedIds = emptySet() }
    val request = rememberRequestLauncher()
    var confirmEmpty by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionTopBar(selected.size) { selectedIds = emptySet() }
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.trash)) },
                    navigationIcon = { BackButton(onBack) },
                    actions = {
                        if (items.isNotEmpty()) {
                            TextButton(onClick = { confirmEmpty = true }) { Text(stringResource(R.string.empty_trash)) }
                        }
                    },
                )
            }
        },
        bottomBar = { if (selecting) SelectionBottomBar(actions) },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(
                Icons.Outlined.Delete,
                stringResource(R.string.trash_empty_title),
                stringResource(R.string.trash_empty_body),
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        MediaGrid(
            items = items,
            zoom = zoom,
            onZoomChange = { zoom = it },
            selection = selectedIds,
            onSelectionChange = { selectedIds = it },
            onOpen = onOpen,
            contentPadding = padding,
            header = {
                item(key = "intro", span = { GridItemSpan(maxLineSpan) }, contentType = "intro") {
                    Text(
                        stringResource(R.string.trash_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            },
        )
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.empty_trash_title)) },
            text = { Text(pluralStringResource(R.plurals.empty_trash_body, items.size, items.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    request(viewModel.deleteRequest(items))
                }) { Text(stringResource(R.string.delete_forever)) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * An album's menu: pin it to the top of the albums (or unpin it), and whether it shows in the
 * Photos grid. Its cover is chosen from the viewer ("Use as album cover").
 */
@Composable
private fun AlbumMenu(viewModel: GalleryViewModel, library: Library, albumId: Long) {
    var open by remember { mutableStateOf(false) }
    val pinned = albumId in library.albumPrefs.pinned
    val inPhotos = albumId !in library.albumPrefs.hidden
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (pinned) R.string.album_unpin else R.string.album_pin)) },
                leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                onClick = {
                    open = false
                    viewModel.setAlbumPinned(albumId, !pinned)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.album_in_photos)) },
                leadingIcon = { Icon(Icons.Outlined.Photo, contentDescription = null) },
                trailingIcon = { Checkbox(checked = inPhotos, onCheckedChange = null) },
                onClick = {
                    open = false
                    viewModel.setAlbumInPhotos(albumId, !inPhotos)
                },
            )
        }
    }
}

/** The usual back arrow for a top bar. */
@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
    }
}
