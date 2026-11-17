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
import app.roll.gallery.data.Media
import app.roll.gallery.ui.components.BarAction
import app.roll.gallery.ui.components.rememberRequestLauncher
import app.roll.gallery.ui.components.shareMedia

/**
 * The actions for photos selected in the library: share, favorite (or unfavorite, when every
 * selected photo already is one) and move to trash. [onDone] runs once a change has been
 * confirmed, to clear the selection.
 */
@Composable
fun libraryActions(viewModel: GalleryViewModel, selected: List<Media>, onDone: () -> Unit): List<BarAction> {
    val context = LocalContext.current
    val request = rememberRequestLauncher { agreed -> if (agreed) onDone() }
    val allFavorite = selected.isNotEmpty() && selected.all { it.isFavorite }
    return listOf(
        BarAction(Icons.Outlined.Share, stringResource(R.string.share)) { context.shareMedia(selected) },
        BarAction(
            if (allFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
            stringResource(if (allFavorite) R.string.unfavorite else R.string.favorite),
        ) { request(viewModel.favoriteRequest(selected, favorite = !allFavorite)) },
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
