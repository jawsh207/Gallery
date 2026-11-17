package app.roll.gallery.ui.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roll.gallery.R
import app.roll.gallery.data.AlbumNameProblem
import app.roll.gallery.data.Library
import app.roll.gallery.data.Media
import app.roll.gallery.data.albumNameProblem
import app.roll.gallery.data.buildAlbums
import app.roll.gallery.data.canHold
import app.roll.gallery.data.newAlbumPath
import app.roll.gallery.image.Thumbnail
import app.roll.gallery.intents.ExternalRequest
import app.roll.gallery.data.PickKinds
import app.roll.gallery.ui.GalleryViewModel
import app.roll.gallery.ui.components.rememberRequestLauncher
import app.roll.gallery.ui.pick.PickScreen
import coil3.compose.AsyncImage

/**
 * Something that moves or copies photos into a folder, asking for write access first when
 * moving (Android requires it for files Gallery didn't make). [onDone] runs once the work has
 * been started, or straight away for a copy.
 */
@Composable
fun rememberAlbumTransfer(viewModel: GalleryViewModel, onDone: () -> Unit): (List<Media>, String, Boolean) -> Unit {
    var pending by remember { mutableStateOf<Pair<List<Media>, String>?>(null) }
    val request = rememberRequestLauncher { agreed ->
        val (items, path) = pending ?: return@rememberRequestLauncher
        pending = null
        if (agreed) viewModel.move(items, path)
        onDone()
    }
    return { items, path, copy ->
        if (copy) {
            viewModel.copy(items, path)
            onDone()
        } else {
            pending = items to path
            request(viewModel.writeRequest(items))
        }
    }
}

/**
 * The "Add to album" sheet: move or copy [items] into an existing album or a new one. Albums
 * Android won't let these files into (anything outside DCIM, Pictures or, for videos, Movies)
 * are greyed out, as is the album they're all already in when moving.
 */
@Composable
fun AddToAlbumSheet(viewModel: GalleryViewModel, library: Library, items: List<Media>, onDismiss: () -> Unit, onDone: () -> Unit) {
    var copy by rememberSaveable { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    val transfer = rememberAlbumTransfer(viewModel, onDone)
    val albums = remember(library.items) { buildAlbums(library.items) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                pluralStringResource(R.plurals.add_to_album_title, items.size, items.size),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                SegmentedButton(selected = !copy, onClick = { copy = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                    Text(stringResource(R.string.move))
                }
                SegmentedButton(selected = copy, onClick = { copy = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                    Text(stringResource(R.string.copy))
                }
            }
            Text(
                stringResource(if (copy) R.string.copy_explained else R.string.move_explained),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            LazyColumn(Modifier.padding(top = 8.dp)) {
                item(key = "new") {
                    AlbumChoice(
                        cover = null,
                        name = stringResource(R.string.new_album),
                        enabled = true,
                        onClick = { naming = true },
                    )
                }
                items(albums, key = { it.id }) { album ->
                    val allowed = items.all { canHold(album.path, it.isVideo) } &&
                        (copy || !items.all { it.albumId == album.id })
                    AlbumChoice(
                        cover = album.cover,
                        name = album.name,
                        enabled = allowed,
                        onClick = { transfer(items, album.path, copy) },
                    )
                }
            }
        }
    }
    if (naming) {
        NewAlbumDialog(
            existing = albums.map { it.name },
            onConfirm = { name ->
                naming = false
                transfer(items, newAlbumPath(name), copy)
            },
            onDismiss = { naming = false },
        )
    }
}

/** One row of the sheet: an album's cover and name, or a plus for a new album. */
@Composable
private fun AlbumChoice(cover: Media?, name: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = RoundedCornerShape(12.dp)
        if (cover != null) {
            AsyncImage(
                model = Thumbnail(cover.uri, cover.modifiedAt),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(shape),
            )
        } else {
            Surface(shape = shape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, contentDescription = null) }
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Asks for a new album's name, saying why a name can't be used (empty, a "/", or taken). */
@Composable
fun NewAlbumDialog(existing: List<String>, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val problem = albumNameProblem(name, existing)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_album)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.album_name)) },
                isError = problem == AlbumNameProblem.Slash || problem == AlbumNameProblem.Taken,
                supportingText = when (problem) {
                    AlbumNameProblem.Slash -> ({ Text(stringResource(R.string.album_name_slash)) })
                    AlbumNameProblem.Taken -> ({ Text(stringResource(R.string.album_name_taken)) })
                    else -> null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = problem == null) { Text(stringResource(R.string.create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Making a new album from the Collections tab: choose its photos from the library, then
 * whether to move or copy them into it. An album is a folder, so it only exists once
 * something is in it.
 */
@Composable
fun NewAlbumScreen(viewModel: GalleryViewModel, library: Library, name: String, onDone: () -> Unit) {
    var chosen by remember { mutableStateOf<List<Media>?>(null) }
    val transfer = rememberAlbumTransfer(viewModel, onDone)
    PickScreen(
        library = library,
        request = ExternalRequest.Pick(PickKinds(images = true, videos = true), multiple = true),
        title = name,
        onDone = { picked -> if (picked.isNullOrEmpty()) onDone() else chosen = picked },
    )
    chosen?.let { items ->
        AlertDialog(
            onDismissRequest = { chosen = null },
            title = { Text(pluralStringResource(R.plurals.add_to_album_title, items.size, items.size)) },
            text = { Text(stringResource(R.string.move_or_copy_body)) },
            confirmButton = {
                TextButton(onClick = {
                    chosen = null
                    transfer(items, newAlbumPath(name), false)
                }) { Text(stringResource(R.string.move)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    chosen = null
                    transfer(items, newAlbumPath(name), true)
                }) { Text(stringResource(R.string.copy)) }
            },
        )
    }
}
