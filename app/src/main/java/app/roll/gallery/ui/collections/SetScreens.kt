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
import app.roll.gallery.data.GridZoom
import app.roll.gallery.data.Library
import app.roll.gallery.data.Media
import app.roll.gallery.data.MediaSet
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
    is MediaSet.Album -> library.album(id).firstOrNull()?.albumName.orEmpty()
    is MediaSet.Search -> query
    is MediaSet.Memory -> pluralStringResource(R.plurals.years_ago, yearsAgo, yearsAgo)
}

/** One album, Favorites or Videos as its own grid, with the same selecting as the Photos tab. */
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

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            if (selecting) {
                SelectionTopBar(selected.size) { selectedIds = emptySet() }
            } else {
                TopAppBar(
                    title = { Text(set.title(library)) },
                    navigationIcon = { BackButton(onBack) },
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

/** The usual back arrow for a top bar. */
@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
    }
}
