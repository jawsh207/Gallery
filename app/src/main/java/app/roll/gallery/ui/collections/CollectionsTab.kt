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
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import app.roll.gallery.ui.components.SEGMENT_GAP
import app.roll.gallery.ui.components.ListRow
import app.roll.gallery.ui.components.segmentShape
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
import app.roll.gallery.data.Library
import app.roll.gallery.data.MediaSet
import app.roll.gallery.data.buildAlbums
import app.roll.gallery.image.thumbnail
import app.roll.gallery.ui.components.EmptyState
import coil3.compose.AsyncImage

/**
 * The Collections tab: shortcuts to Favorites and Trash, [people] (Gallery Plus), the duplicate finder and the
 * Locked folder, then every folder with photos
 * as an album, camera and screenshots first, with a button to make a new one ([onNewAlbum]
 * gets its name once it's been checked).
 */
@Composable
fun CollectionsTab(
    library: Library,
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
        item(key = "shortcuts", span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Shortcut(Icons.Outlined.FavoriteBorder, stringResource(R.string.favorites), Modifier.weight(1f)) { onOpenSet(MediaSet.Favorites) }
                Shortcut(Icons.Outlined.Delete, stringResource(R.string.trash), Modifier.weight(1f), onClick = onOpenTrash)
            }
        }
        if (people.isNotEmpty()) {
            item(key = "people", span = { GridItemSpan(maxLineSpan) }) {
                PeopleRow(people, photosById, onOpenPerson)
            }
        }
        item(key = "tools", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP)) {
                ListRow(
                    icon = Icons.Outlined.ContentCopy,
                    title = stringResource(R.string.duplicates),
                    summary = stringResource(R.string.duplicates_summary),
                    shape = segmentShape(0, 2),
                    onClick = onOpenDuplicates,
                )
                ListRow(
                    icon = Icons.Outlined.Lock,
                    title = stringResource(R.string.locked_folder),
                    summary = stringResource(R.string.locked_summary),
                    shape = segmentShape(1, 2),
                    onClick = onOpenLocked,
                )
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

/** A tonal button with an icon over a label, for the shortcuts row. */
@Composable
private fun Shortcut(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(Modifier.padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
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
