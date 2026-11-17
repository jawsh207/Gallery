package app.roll.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.roll.gallery.R
import app.roll.gallery.data.GridZoom
import app.roll.gallery.data.Library
import app.roll.gallery.data.Media
import app.roll.gallery.data.MediaSet
import app.roll.gallery.ui.access.AccessScreen
import app.roll.gallery.ui.access.PHOTO_PERMISSIONS
import app.roll.gallery.ui.access.PhotoAccess
import app.roll.gallery.ui.access.photoAccess
import app.roll.gallery.ui.collections.CollectionsTab
import app.roll.gallery.ui.collections.SetScreen
import app.roll.gallery.ui.collections.TrashScreen
import app.roll.gallery.ui.components.BottomBar
import app.roll.gallery.ui.components.SelectionBottomBar
import app.roll.gallery.ui.components.SelectionTopBar
import app.roll.gallery.ui.photos.PhotosTab
import app.roll.gallery.ui.viewer.ViewerScreen
import kotlinx.coroutines.launch

/** The two bottom tabs, in order. Photos is home. */
enum class Tab(@param:StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    Photos(R.string.tab_photos, Icons.Outlined.Photo, Icons.Filled.Photo),
    Collections(R.string.tab_collections, Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary),
}

/**
 * The whole app: asks for photo access, then shows the screen on top of the back stack.
 * Access is checked again each time the app comes back, since it can be changed in Android's
 * settings while the app is away.
 */
@Composable
fun GalleryApp(viewModel: GalleryViewModel = viewModel()) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(context.photoAccess()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = context.photoAccess()
        viewModel.onAccessChanged()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = context.photoAccess()
        if (now != access) {
            access = now
            viewModel.onAccessChanged()
        }
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (access == PhotoAccess.None && !asked) {
            asked = true
            launcher.launch(PHOTO_PERMISSIONS)
        }
    }
    if (access == PhotoAccess.None) {
        AccessScreen(onAllow = { launcher.launch(PHOTO_PERMISSIONS) })
        return
    }

    val library by viewModel.library.collectAsStateWithLifecycle()
    val loaded = library
    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        return
    }

    val stack = rememberBackStack()
    val open: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1, onBack = back)

    when (val screen = stack.last()) {
        Screen.Home -> Home(
            viewModel, loaded,
            partialAccess = access == PhotoAccess.Partial,
            onAllowAll = { launcher.launch(PHOTO_PERMISSIONS) },
            open = open,
        )
        is Screen.Grid -> SetScreen(
            viewModel, loaded, screen.set, onBack = back,
            onOpen = { open(Screen.Viewer(screen.set, it.id)) },
        )
        Screen.Trash -> TrashScreen(viewModel, loaded, onBack = back, onOpen = { open(Screen.Viewer(MediaSet.Trash, it.id)) })
        is Screen.Viewer -> ViewerScreen(
            viewModel, loaded, screen.set, screen.id,
            onCurrentChange = { id -> if (stack.lastOrNull() == screen) stack[stack.lastIndex] = screen.copy(id = id) },
            onBack = back,
        )
    }
}

/**
 * The bottom tabs. Long-pressing a photo on the Photos tab selects it; while selecting, the top
 * bar counts the selection and the bottom tabs give way to its actions. Back from Collections
 * returns to Photos.
 */
@Composable
private fun Home(
    viewModel: GalleryViewModel,
    library: Library,
    partialAccess: Boolean,
    onAllowAll: () -> Unit,
    open: (Screen) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Photos) }
    var zoom by rememberSaveable { mutableStateOf(GridZoom.Days) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val selected = library.items.filter { it.id in selectedIds }
    val selecting = selected.isNotEmpty() && tab == Tab.Photos
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    BackHandler(enabled = selecting) { selectedIds = emptySet() }
    BackHandler(enabled = !selecting && tab != Tab.Photos) { tab = Tab.Photos }
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val actions = libraryActions(viewModel, selected) { selectedIds = emptySet() }
    val openMedia: (Media) -> Unit = { open(Screen.Viewer(MediaSet.All, it.id)) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            if (selecting) {
                SelectionTopBar(selected.size) { selectedIds = emptySet() }
            } else {
                TopAppBar(title = { Text(stringResource(tab.label)) }, scrollBehavior = scroll)
            }
        },
        bottomBar = {
            if (selecting) {
                SelectionBottomBar(actions)
            } else {
                BottomBar {
                    for (entry in Tab.entries) {
                        ShortNavigationBarItem(
                            selected = tab == entry,
                            onClick = {
                                if (tab == entry && entry == Tab.Photos) {
                                    // Tapping Photos again goes back to the newest photos.
                                    scope.launch { gridState.animateScrollToItem(0) }
                                }
                                tab = entry
                            },
                            icon = { Icon(if (tab == entry) entry.selectedIcon else entry.icon, contentDescription = null) },
                            label = { Text(stringResource(entry.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { shown ->
            when (shown) {
                Tab.Photos -> PhotosTab(
                    items = library.items,
                    partialAccess = partialAccess,
                    onAllowAll = onAllowAll,
                    zoom = zoom,
                    onZoomChange = { zoom = it },
                    selection = selectedIds,
                    onSelectionChange = { selectedIds = it },
                    onOpen = openMedia,
                    gridState = gridState,
                    contentPadding = padding,
                )
                Tab.Collections -> CollectionsTab(
                    library = library,
                    onOpenSet = { open(Screen.Grid(it)) },
                    onOpenTrash = { open(Screen.Trash) },
                    contentPadding = padding,
                )
            }
        }
    }
}
