package app.roll.gallery.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import app.roll.gallery.intents.ExternalRequest
import app.roll.gallery.intents.externalMedia
import app.roll.gallery.intents.parseRequest
import app.roll.gallery.ui.pick.PickScreen
import app.roll.gallery.ui.viewer.ExternalViewer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.unit.dp
import app.roll.gallery.ui.components.SearchPill
import app.roll.gallery.ui.components.AskForNotifications
import app.roll.gallery.ui.search.SearchScreen
import app.roll.gallery.ui.settings.SettingsScreen
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Videocam
import app.roll.gallery.ui.components.EmptyState
import app.roll.gallery.ui.photos.MediaGrid
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.roll.gallery.R
import app.roll.gallery.ui.locked.rememberMoveToLocked
import app.roll.gallery.ui.components.BarAction
import androidx.compose.material.icons.outlined.Lock
import app.roll.gallery.data.GridZoom
import app.roll.gallery.data.Library
import app.roll.gallery.data.Media
import app.roll.gallery.data.MediaSet
import app.roll.gallery.data.memoriesFor
import java.time.LocalDate
import java.time.ZoneId
import app.roll.gallery.ui.access.AccessScreen
import app.roll.gallery.ui.access.PHOTO_PERMISSIONS
import app.roll.gallery.ui.access.PhotoAccess
import app.roll.gallery.ui.access.photoAccess
import app.roll.gallery.ui.collections.CollectionsTab
import app.roll.gallery.ui.collections.NewAlbumScreen
import app.roll.gallery.ui.collections.DuplicatesScreen
import app.roll.gallery.ui.locked.LockedScreen
import app.roll.gallery.ui.people.PersonScreen
import app.roll.gallery.ui.editor.EditorScreen
import app.roll.gallery.ui.collections.SetScreen
import app.roll.gallery.ui.collections.TrashScreen
import app.roll.gallery.ui.components.BottomBar
import app.roll.gallery.ui.components.SelectionBottomBar
import app.roll.gallery.ui.components.SelectionTopBar
import app.roll.gallery.ui.photos.PhotosTab
import app.roll.gallery.ui.viewer.ViewerScreen
import app.roll.gallery.ui.components.rememberRequestLauncher
import app.roll.gallery.data.Creation
import app.roll.gallery.ui.create.AnimationScreen
import app.roll.gallery.ui.create.ChooseScreen
import app.roll.gallery.ui.create.CollageScreen
import app.roll.gallery.ui.create.CreateTab
import app.roll.gallery.ui.create.VideoMakerScreen
import app.roll.gallery.ui.settings.FoldersScreen
import app.roll.gallery.ui.editor.VideoEditorScreen
import app.roll.gallery.data.buildStacks
import app.roll.gallery.data.withoutStacked
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.outlined.Brush
import kotlinx.coroutines.launch

/** The bottom tabs, in order. Photos is home. */
enum class Tab(@param:StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    Photos(R.string.tab_photos, Icons.Outlined.Photo, Icons.Filled.Photo),
    Videos(R.string.videos, Icons.Outlined.VideoLibrary, Icons.Filled.VideoLibrary),
    Collections(R.string.tab_collections, Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary),
    Create(R.string.tab_create, Icons.Outlined.Brush, Icons.Filled.Brush),
}

/**
 * The whole app. A file opened from another app goes straight to its viewer; otherwise it asks
 * for photo access, then shows either the picker (when another app asked to choose photos) or
 * the screen on top of the back stack. [finish] ends a request from another app, handing back
 * what was chosen (null when cancelled).
 * Access is checked again each time the app comes back, since it can be changed in Android's
 * settings while the app is away.
 */
@Composable
fun GalleryApp(intent: Intent?, finish: (List<Uri>?) -> Unit, viewModel: GalleryViewModel = viewModel()) {
    val context = LocalContext.current
    val request = remember(intent) { parseRequest(context, intent) }
    // Another app's file needs no photo access: that app lends access to just this file.
    if (request is ExternalRequest.View) {
        val media = remember(request) { externalMedia(context, request.uri, request.mimeType) }
        ExternalViewer(media, viewModel, onClose = { finish(null) })
        return
    }
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

    if (request is ExternalRequest.Pick) {
        PickScreen(loaded, request, onDone = { picked -> finish(picked?.map { it.uri }) })
        BackHandler { finish(null) }
        return
    }

    val stack = rememberBackStack()
    val open: (Screen) -> Unit = { stack.add(it) }
    // Each screen's remembered state (the open tab, scroll positions, zoom) is kept while another
    // screen covers it, so going back returns to it as it was. Kept by place in the stack, and
    // dropped when a screen is closed.
    val saved = rememberSaveableStateHolder()
    val back: () -> Unit = {
        if (stack.size > 1) {
            saved.removeState(stack.lastIndex)
            stack.removeAt(stack.lastIndex)
        }
    }
    BackHandler(enabled = stack.size > 1, onBack = back)

    AskForNotifications(viewModel)

    // The Locked folder locks again whenever the app leaves the screen.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.lock() }

    // Messages from background work (copies, moves, wallpaper), over whichever screen is open.
    // Some offer Undo; taking things back out of the trash needs Android's confirmation.
    val snackbar = remember { SnackbarHostState() }
    val undoRequest = rememberRequestLauncher()
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val undo = message.undo
            val result = snackbar.showSnackbar(
                message.text,
                actionLabel = if (undo != null) undoLabel else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && undo != null) {
                if (undo is Undo.Untrash) undoRequest(viewModel.trashRequest(undo.items, trash = false)) else viewModel.undo(undo)
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        saved.SaveableStateProvider(stack.lastIndex) {
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
                Screen.Search -> SearchScreen(
                    viewModel, loaded, onBack = back,
                    onOpen = { query, media -> open(Screen.Viewer(MediaSet.Search(query), media.id)) },
                )
                Screen.Settings -> SettingsScreen(viewModel, onBack = back, onOpenFolders = { open(Screen.PhotoFolders) })
                Screen.PhotoFolders -> FoldersScreen(viewModel, loaded, onBack = back)
                Screen.Duplicates -> DuplicatesScreen(viewModel, loaded, onBack = back)
                is Screen.Person -> PersonScreen(
                    viewModel, loaded, screen.key,
                    onKeyChange = { key -> if (stack.lastOrNull() == screen) stack[stack.lastIndex] = Screen.Person(key) },
                    onBack = back,
                    onOpen = { open(Screen.Viewer(MediaSet.Person(screen.key), it.id)) },
                )
                Screen.Locked -> LockedScreen(viewModel, loaded, onBack = back, onOpen = { open(Screen.Viewer(MediaSet.Locked, it.id)) })
                is Screen.NewAlbum -> NewAlbumScreen(viewModel, loaded, screen.name, onDone = back)
                Screen.Trash -> TrashScreen(viewModel, loaded, onBack = back, onOpen = { open(Screen.Viewer(MediaSet.Trash, it.id)) })
                is Screen.Viewer -> ViewerScreen(
                    viewModel, loaded, screen.set, screen.id,
                    onCurrentChange = { id -> if (stack.lastOrNull() == screen) stack[stack.lastIndex] = screen.copy(id = id) },
                    onBack = back,
                    slideshowAtStart = screen.slideshow,
                    onEdit = { open(Screen.Editor(it.id)) },
                )
                is Screen.Editor -> {
                    val media = loaded.items.firstOrNull { it.id == screen.id }
                    when {
                        media == null -> LaunchedEffect(Unit) { back() }
                        media.isVideo -> VideoEditorScreen(viewModel, media, onClose = back)
                        else -> EditorScreen(viewModel, media, onClose = back, moods = screen.moods)
                    }
                }
                is Screen.Choose -> ChooseScreen(viewModel, loaded, screen.kind, onBack = back) { chosen ->
                    open(
                        if (screen.kind == Creation.Moods) Screen.Editor(chosen.first().id, moods = true)
                        else Screen.Make(screen.kind, chosen.map { it.id }),
                    )
                }
                is Screen.Make -> {
                    val byId = remember(loaded.items) { loaded.items.associateBy { it.id } }
                    val chosen = screen.ids.mapNotNull { byId[it] }
                    if (chosen.size < screen.kind.least) {
                        LaunchedEffect(Unit) { back() }
                    } else {
                        when (screen.kind) {
                            Creation.Collage -> CollageScreen(viewModel, chosen, onBack = back)
                            Creation.Animation -> AnimationScreen(viewModel, chosen, onBack = back)
                            Creation.VideoMaker -> VideoMakerScreen(viewModel, chosen, onBack = back)
                            Creation.Moods -> LaunchedEffect(Unit) { back() }
                        }
                    }
                }
            }
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp),
        )
    }
}

/**
 * The bottom tabs (Photos, Videos, Collections, Create) under the search bar with its Settings
 * button, as in Contacts. Long-pressing a photo or video selects it; while selecting, the search
 * bar becomes a count of the selection and the bottom tabs give way to its actions. Back from
 * any other tab returns to Photos.
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
    var videoZoom by rememberSaveable { mutableStateOf(GridZoom.Days) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    // Quick runs of shots show as one tile in Photos; selecting it selects the whole stack.
    val stackOn by viewModel.stackPhotos.collectAsStateWithLifecycle()
    val stacks = remember(library.timeline, stackOn) { if (stackOn) buildStacks(library.timeline) else emptyList() }
    val photosGrid = remember(library.timeline, stacks) { withoutStacked(library.timeline, stacks) }
    val stackSizes = remember(stacks) { stacks.associate { it.cover.id to it.members.size } }
    val stackMembers = remember(stacks) { stacks.associate { stack -> stack.cover.id to stack.members.map { it.id } } }
    val selectedWithStacks = if (tab == Tab.Photos) selectedIds + selectedIds.flatMap { stackMembers[it].orEmpty() } else selectedIds
    val selected = library.items.filter { it.id in selectedWithStacks }
    val selecting = selected.isNotEmpty() && (tab == Tab.Photos || tab == Tab.Videos)
    // A selection belongs to the tab it was made on.
    LaunchedEffect(tab) { selectedIds = emptySet() }
    val gridState = rememberLazyGridState()
    val videoGridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    BackHandler(enabled = selecting) { selectedIds = emptySet() }
    BackHandler(enabled = !selecting && tab != Tab.Photos) { tab = Tab.Photos }
    val actions = libraryActions(viewModel, library, selected, onDone = { selectedIds = emptySet() })
    val moveToLocked = rememberMoveToLocked(viewModel) { selectedIds = emptySet() }
    val lockMenu = BarAction(Icons.Outlined.Lock, stringResource(R.string.locked_move_in)) { moveToLocked(selected) }
    val openMedia: (Media) -> Unit = { open(Screen.Viewer(MediaSet.All, it.id)) }
    val showMemories by viewModel.showMemories.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val collectionOrder by viewModel.collectionOrder.collectAsStateWithLifecycle()
    val memories = remember(library.items, showMemories) {
        if (showMemories) memoriesFor(library.items, LocalDate.now(), ZoneId.systemDefault()) else emptyList()
    }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionTopBar(selected.size, menu = listOf(lockMenu)) { selectedIds = emptySet() }
            } else {
                // Pinned above the tabs, as in Contacts: the grid scrolls behind it. The strip is
                // opaque in the background colour (status bar included) so nothing shows around the pill.
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
                    SearchPill(
                        onClick = { open(Screen.Search) },
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        trailing = {
                            IconButton(onClick = { open(Screen.Settings) }) {
                                Icon(Icons.Rounded.Settings, stringResource(R.string.settings))
                            }
                        },
                    )
                }
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
                                // Tapping Photos or Videos again goes back to the newest.
                                if (tab == entry && entry == Tab.Photos) scope.launch { gridState.animateScrollToItem(0) }
                                if (tab == entry && entry == Tab.Videos) scope.launch { videoGridState.animateScrollToItem(0) }
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
                    items = photosGrid,
                    stackSizes = stackSizes,
                    partialAccess = partialAccess,
                    onAllowAll = onAllowAll,
                    zoom = zoom,
                    onZoomChange = { zoom = it },
                    selection = selectedIds,
                    onSelectionChange = { selectedIds = it },
                    onOpen = openMedia,
                    gridState = gridState,
                    contentPadding = padding,
                    memories = memories,
                    onOpenMemory = { memory ->
                        open(Screen.Viewer(MediaSet.Memory(memory.yearsAgo), memory.cover.id, slideshow = true))
                    },
                )
                Tab.Videos -> {
                    val videos = remember(library.items) { library.videos }
                    if (videos.isEmpty()) {
                        EmptyState(
                            Icons.Outlined.Videocam,
                            stringResource(R.string.no_videos_title),
                            stringResource(R.string.no_videos_body),
                            Modifier.padding(padding),
                        )
                    } else {
                        MediaGrid(
                            items = videos,
                            zoom = videoZoom,
                            onZoomChange = { videoZoom = it },
                            selection = selectedIds,
                            onSelectionChange = { selectedIds = it },
                            onOpen = { open(Screen.Viewer(MediaSet.Videos, it.id)) },
                            state = videoGridState,
                            contentPadding = padding,
                        )
                    }
                }
                Tab.Collections -> CollectionsTab(
                    library = library,
                    order = collectionOrder,
                    onReorder = viewModel::setCollectionOrder,
                    onSortAlbums = viewModel::setAlbumSort,
                    onOpenSet = { open(Screen.Grid(it)) },
                    onOpenTrash = { open(Screen.Trash) },
                    onNewAlbum = { open(Screen.NewAlbum(it)) },
                    onOpenDuplicates = { open(Screen.Duplicates) },
                    onOpenLocked = { open(Screen.Locked) },
                    contentPadding = padding,
                    people = people,
                    onOpenPerson = { open(Screen.Person(it.key)) },
                )
                Tab.Create -> CreateTab(onStart = { open(Screen.Choose(it)) }, contentPadding = padding)
            }
        }
    }
}
