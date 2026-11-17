package app.roll.gallery.ui.pick

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.roll.gallery.R
import app.roll.gallery.data.GridZoom
import app.roll.gallery.data.Library
import app.roll.gallery.data.Media
import app.roll.gallery.intents.ExternalRequest
import app.roll.gallery.ui.components.EmptyState

/**
 * Shown when another app asks to choose photos or videos ("Choose from Gallery"): the library,
 * narrowed to the kinds it asked for, in the usual grid. With one allowed, a tap hands it back;
 * with several, taps (or a long-press drag) select and Done hands them all back. [onDone] gets
 * the chosen items, or null when cancelled. [title] replaces the usual heading until something
 * is selected (the new album's name, when choosing photos for it).
 */
@Composable
fun PickScreen(library: Library, request: ExternalRequest.Pick, onDone: (List<Media>?) -> Unit, title: String? = null) {
    val items = remember(library.items, request) { library.items.filter { request.kinds.accepts(it) } }
    var zoom by rememberSaveable { mutableStateOf(GridZoom.Days) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val selected = items.filter { it.id in selectedIds }
    val heading = when {
        title != null && selected.isEmpty() -> title
        request.multiple -> if (selected.isEmpty()) stringResource(R.string.pick_many) else pluralStringResource(R.plurals.selected_count, selected.size, selected.size)
        request.kinds.videos && !request.kinds.images -> stringResource(R.string.pick_video)
        else -> stringResource(R.string.pick_one)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(heading) },
                navigationIcon = {
                    IconButton(onClick = { onDone(null) }) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) }
                },
                actions = {
                    if (request.multiple) {
                        Button(
                            onClick = { onDone(selected) },
                            enabled = selected.isNotEmpty(),
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text(stringResource(R.string.done)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(
                Icons.Outlined.PhotoLibrary,
                stringResource(R.string.no_photos_title),
                stringResource(R.string.no_photos_body),
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        app.roll.gallery.ui.photos.MediaGrid(
            items = items,
            zoom = zoom,
            onZoomChange = { zoom = it },
            selection = if (request.multiple) selectedIds else emptySet(),
            onSelectionChange = { if (request.multiple) selectedIds = it },
            onOpen = { media ->
                if (request.multiple) selectedIds = selectedIds + media.id else onDone(listOf(media))
            },
            contentPadding = padding,
        )
    }
}
