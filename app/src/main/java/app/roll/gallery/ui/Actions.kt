package app.roll.gallery.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.roll.gallery.R
import app.roll.gallery.data.Library
import app.roll.gallery.data.Media
import app.roll.gallery.ui.collections.AddToAlbumSheet
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.roll.gallery.ui.components.BarAction
import app.roll.gallery.ui.components.rememberRequestLauncher
import app.roll.gallery.ui.components.shareMedia

/**
 * The actions for photos selected in the library: share, favorite (or unfavorite, when every
 * selected photo already is one), add to album (move or copy; left out when [withAlbum] is
 * false) and move to trash. [onDone] runs once a change has been confirmed, to clear the
 * selection.
 */
@Composable
fun libraryActions(
    viewModel: GalleryViewModel,
    library: Library,
    selected: List<Media>,
    onDone: () -> Unit,
    withAlbum: Boolean = true,
): List<BarAction> {
    val context = LocalContext.current
    val request = rememberRequestLauncher { agreed -> if (agreed) onDone() }
    val allFavorite = selected.isNotEmpty() && selected.all { it.isFavorite }
    var adding by remember { mutableStateOf(false) }
    if (adding) {
        AddToAlbumSheet(
            viewModel, library, selected,
            onDismiss = { adding = false },
            onDone = {
                adding = false
                onDone()
            },
        )
    }
    val album = BarAction(Icons.Outlined.LibraryAdd, stringResource(R.string.add_to_album)) { adding = true }
    return listOfNotNull(
        BarAction(Icons.Outlined.Share, stringResource(R.string.share)) { context.shareMedia(selected) },
        BarAction(
            if (allFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
            stringResource(if (allFavorite) R.string.unfavorite else R.string.favorite),
        ) { request(viewModel.favoriteRequest(selected, favorite = !allFavorite)) },
        album.takeIf { withAlbum },
        BarAction(Icons.Outlined.Delete, stringResource(R.string.move_to_trash)) {
            request(viewModel.trashRequest(selected, trash = true))
        },
    )
}

/** The actions for photos selected in the trash: put them back, or delete them for good. */
@Composable
fun trashActions(viewModel: GalleryViewModel, selected: List<Media>, onDone: () -> Unit): List<BarAction> {
    val request = rememberRequestLauncher { agreed -> if (agreed) onDone() }
    return listOf(
        BarAction(Icons.Outlined.RestoreFromTrash, stringResource(R.string.restore)) {
            request(viewModel.trashRequest(selected, trash = false))
        },
        BarAction(Icons.Outlined.DeleteForever, stringResource(R.string.delete_forever)) {
            request(viewModel.deleteRequest(selected))
        },
    )
}
