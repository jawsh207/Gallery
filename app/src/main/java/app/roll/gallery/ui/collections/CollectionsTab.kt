package app.roll.gallery.ui.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Add
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roll.gallery.R
import app.roll.gallery.recognition.Person
import app.roll.gallery.ui.people.PeopleRow
import app.roll.gallery.data.Album
import app.roll.gallery.data.CollectionTile
import app.roll.gallery.data.moveTile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SwapVert
import app.roll.gallery.data.Library
import app.roll.gallery.data.MediaSet
import app.roll.gallery.data.buildAlbums
import app.roll.gallery.image.thumbnail
import app.roll.gallery.ui.components.EmptyState
import coil3.compose.AsyncImage

/**
 * The Collections tab: [people] (Gallery Plus), then tiles for Favorites, Videos, Screenshots,
 * Recently Added, the duplicate finder, the Locked folder and Trash in the [order] chosen (with
 * a Reorder button, [onReorder] getting the new order), then every folder with photos as an
 * album, camera and screenshots first, with a button to make a new one ([onNewAlbum] gets its
 * name once it's been checked).
 */
@Composable
fun CollectionsTab(
    library: Library,
    order: List<CollectionTile>,
    onReorder: (List<CollectionTile>) -> Unit,
    onOpenSet: (MediaSet) -> Unit,
    onOpenTrash: () -> Unit,
    onNewAlbum: (String) -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenLocked: () -> Unit,
    contentPadding: PaddingValues,
    people: List<Person> = emptyList(),
    onOpenPerson: (Person) -> Unit = {},
) {
    val albums = remember(library.items) { buildAlbums(library.items) }
    val photosById = remember(library.items) { library.items.associateBy { it.id } }
    var naming by remember { mutableStateOf(false) }
    var reordering by remember { mutableStateOf(false) }
    if (naming) {
        NewAlbumDialog(
            existing = albums.map { it.name },
            onConfirm = { name ->
                naming = false
                onNewAlbum(name)
            },
            onDismiss = { naming = false },
        )
    }
    if (reordering) {
        ReorderDialog(
            order,
            onConfirm = {
                reordering = false
                onReorder(it)
            },
            onDismiss = { reordering = false },
        )
    }
    val openTile: (CollectionTile) -> Unit = { tile ->
        when (tile) {
            CollectionTile.Favorites -> onOpenSet(MediaSet.Favorites)
            CollectionTile.Videos -> onOpenSet(MediaSet.Videos)
            CollectionTile.Screenshots -> onOpenSet(MediaSet.Screenshots)
            CollectionTile.RecentlyAdded -> onOpenSet(MediaSet.RecentlyAdded)
            CollectionTile.Duplicates -> onOpenDuplicates()
            CollectionTile.Locked -> onOpenLocked()
            CollectionTile.Trash -> onOpenTrash()
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (people.isNotEmpty()) {
            item(key = "people", span = { GridItemSpan(maxLineSpan) }) {
                PeopleRow(people, photosById, onOpenPerson)
            }
        }
        item(key = "tiles", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (pair in order.chunked(2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (tile in pair) {
                            Tile(tile, Modifier.weight(1f)) { openTile(tile) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                OutlinedButton(
                    onClick = { reordering = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Rounded.SwapVert, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.reorder))
                }
            }
        }
        item(key = "albums", span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.albums),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 4.dp).weight(1f),
                )
                FilledTonalButton(onClick = { naming = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.new_album))
                }
            }
        }
        if (albums.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    Icons.Outlined.PhotoAlbum,
                    stringResource(R.string.no_albums_title),
                    stringResource(R.string.no_albums_body),
                    Modifier.height(320.dp),
                )
            }
        }
        items(albums, key = { it.id }) { album ->
            AlbumCard(album) { onOpenSet(MediaSet.Album(album.id)) }
        }
    }
}

/** A tile's icon and name. */
private fun tileLook(tile: CollectionTile): Pair<ImageVector, Int> = when (tile) {
    CollectionTile.Favorites -> Icons.Outlined.FavoriteBorder to R.string.favorites
    CollectionTile.Videos -> Icons.Outlined.PlayCircle to R.string.videos
    CollectionTile.Screenshots -> Icons.Outlined.Screenshot to R.string.screenshots
    CollectionTile.RecentlyAdded -> Icons.Outlined.Schedule to R.string.recently_added
    CollectionTile.Duplicates -> Icons.Outlined.ContentCopy to R.string.duplicates
    CollectionTile.Locked -> Icons.Outlined.Lock to R.string.locked_folder
    CollectionTile.Trash -> Icons.Outlined.Delete to R.string.trash
}

/** One of the Collections tab's tiles: an icon and a name on a tonal background. */
@Composable
private fun Tile(tile: CollectionTile, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (icon, label) = tileLook(tile)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(label), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Changing the tiles' order: each tile with buttons to move it up or down (buttons rather than
 * dragging, so it works the same with a screen reader). [onConfirm] gets the new order.
 */
@Composable
private fun ReorderDialog(
    order: List<CollectionTile>,
    onConfirm: (List<CollectionTile>) -> Unit,
    onDismiss: () -> Unit,
) {
    var tiles by remember { mutableStateOf(order) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reorder_title)) },
        text = {
            Column {
                tiles.forEachIndexed { index, tile ->
                    val (icon, label) = tileLook(tile)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(label), Modifier.weight(1f))
                        IconButton(onClick = { tiles = moveTile(tiles, index, -1) }, enabled = index > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.move_up))
                        }
                        IconButton(onClick = { tiles = moveTile(tiles, index, 1) }, enabled = index < tiles.lastIndex) {
                            Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.move_down))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(tiles) }) { Text(stringResource(R.string.done)) } },
        dismissButton = {
            TextButton(onClick = { tiles = CollectionTile.entries }) { Text(stringResource(R.string.reorder_reset)) }
        },
    )
}

/** An album's cover (its newest photo), name and how many items it holds. */
@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        AsyncImage(
            model = album.cover.thumbnail(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp)),
        )
        Text(
            album.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp),
        )
        Text(
            pluralStringResource(R.plurals.item_count, album.count, album.count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
    }
}
