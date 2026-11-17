package app.roll.gallery.ui.locked

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.roll.gallery.R
import app.roll.gallery.data.GridZoom
import app.roll.gallery.data.Library
import app.roll.gallery.data.Media
import app.roll.gallery.ui.GalleryViewModel
import app.roll.gallery.ui.collections.BackButton
import app.roll.gallery.ui.components.BarAction
import app.roll.gallery.ui.components.EmptyState
import app.roll.gallery.ui.components.SelectionBottomBar
import app.roll.gallery.ui.components.SelectionTopBar
import app.roll.gallery.ui.photos.MediaGrid

/**
 * The Locked folder. Without a screen lock on the phone it explains that one is needed; until
 * unlocked it asks for the screen lock; once open it's a grid like any album, whose items can
 * be moved back out or deleted for good. Screenshots are blocked while it's on screen, and it
 * locks again whenever the app leaves the screen.
 */
@Composable
fun LockedScreen(viewModel: GalleryViewModel, library: Library, onBack: () -> Unit, onOpen: (Media) -> Unit) {
    val context = LocalContext.current
    val unlocked by viewModel.unlocked.collectAsStateWithLifecycle()
    SecureWindow()
    if (!context.hasScreenLock()) {
        Gate(onBack) {
            EmptyState(
                Icons.Outlined.Lock,
                stringResource(R.string.locked_folder),
                stringResource(R.string.locked_needs_screen_lock),
                Modifier.padding(it),
            ) {
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }) {
                    Text(stringResource(R.string.locked_set_screen_lock))
                }
            }
        }
        return
    }
    if (!unlocked) {
        LaunchedEffect(Unit) { context.askScreenLock(viewModel::unlock) }
        Gate(onBack) {
            EmptyState(
                Icons.Outlined.Lock,
                stringResource(R.string.locked_folder),
                stringResource(R.string.locked_intro),
                Modifier.padding(it),
            ) {
                Button(onClick = { context.askScreenLock(viewModel::unlock) }) { Text(stringResource(R.string.locked_unlock)) }
            }
        }
        return
    }

    val items = library.locked
    var zoom by rememberSaveable { mutableStateOf(GridZoom.Days) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val selected = items.filter { it.id in selectedIds }
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selectedIds = emptySet() }
    var confirmDelete by remember { mutableStateOf<List<Media>?>(null) }
    val actions = listOf(
        BarAction(Icons.Outlined.LockOpen, stringResource(R.string.locked_move_out)) {
            viewModel.unlockItems(selected)
            selectedIds = emptySet()
        },
        BarAction(Icons.Outlined.Delete, stringResource(R.string.delete_forever)) { confirmDelete = selected },
    )

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionTopBar(selected.size) { selectedIds = emptySet() }
            } else {
                TopAppBar(title = { Text(stringResource(R.string.locked_folder)) }, navigationIcon = { BackButton(onBack) })
            }
        },
        bottomBar = { if (selecting) SelectionBottomBar(actions) },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(
                Icons.Outlined.Lock,
                stringResource(R.string.locked_empty_title),
                stringResource(R.string.locked_empty_body),
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
                        stringResource(R.string.locked_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            },
        )
    }

    confirmDelete?.let { doomed ->
        DeleteLockedDialog(
            count = doomed.size,
            onConfirm = {
                confirmDelete = null
                viewModel.deleteLocked(doomed)
                selectedIds = emptySet()
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

/** A plain screen with a back arrow and the Locked folder's name, around [content]. */
@Composable
private fun Gate(onBack: () -> Unit, content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.locked_folder)) }, navigationIcon = { BackButton(onBack) }) },
        content = content,
    )
}

/** Asks before deleting locked items, which can't be restored from anywhere. */
@Composable
fun DeleteLockedDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.locked_delete_title, count, count)) },
        text = { Text(stringResource(R.string.locked_delete_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete_forever)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
