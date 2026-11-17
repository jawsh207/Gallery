package app.roll.gallery.ui.viewer

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.roll.gallery.R
import app.roll.gallery.data.Library
import app.roll.gallery.data.MediaSet
import app.roll.gallery.data.itemsIn
import app.roll.gallery.ui.GalleryViewModel
import app.roll.gallery.ui.components.BarAction
import app.roll.gallery.ui.components.currentLocale
import app.roll.gallery.ui.components.takenAtText
import app.roll.gallery.ui.libraryActions
import app.roll.gallery.ui.theme.GalleryTheme
import app.roll.gallery.ui.trashActions
import androidx.compose.material.icons.outlined.Info
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Photos and videos one at a time, full screen on black. Swipe sideways through the rest of
 * [set], tap to show or hide the controls (and Android's status and navigation bars), pinch or
 * double-tap to zoom, drag down to close and drag up for details. The bottom bar has the same
 * actions as a selection: share, favorite and trash, or restore and delete inside the trash.
 * [onCurrentChange] reports the item on screen so the back stack reopens it after a restart.
 */
@Composable
fun ViewerScreen(
    viewModel: GalleryViewModel,
    library: Library,
    set: MediaSet,
    startId: Long,
    onCurrentChange: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val items = remember(library, set) { library.itemsIn(set) }
    if (items.isEmpty()) {
        // The last item was trashed or deleted.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val startIndex = remember { items.indexOfFirst { it.id == startId }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = startIndex) { items.size }
    val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]
    LaunchedEffect(pager.settledPage, items) {
        items.getOrNull(pager.settledPage)?.let { onCurrentChange(it.id) }
    }

    var controls by rememberSaveable { mutableStateOf(true) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    SystemBarsVisible(controls)

    // Drag up or down: the page follows the finger and the black fades; far enough down closes.
    val scope = rememberCoroutineScope()
    var dragY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val closeAt = with(density) { 120.dp.toPx() }
    val infoAt = with(density) { 80.dp.toPx() }
    val onDrag: (Float) -> Unit = { dragY += it }
    val onDragEnd: () -> Unit = {
        when {
            dragY > closeAt -> onBack()
            dragY < -infoAt -> showInfo = true
        }
        val from = dragY
        scope.launch { animate(from, 0f) { value, _ -> dragY = value } }
    }

    val actions = if (set == MediaSet.Trash) {
        trashActions(viewModel, listOf(current)) {}
    } else {
        libraryActions(viewModel, listOf(current)) {}
    }

    GalleryTheme(darkTheme = true) {
        val fade = (1f - abs(dragY) / (closeAt * 4)).coerceIn(0f, 1f)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = fade))) {
            HorizontalPager(
                state = pager,
                key = { items[it].id },
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize().graphicsLayer { translationY = dragY },
            ) { page ->
                val media = items[page]
                if (media.isVideo) {
                    VideoPage(
                        media = media,
                        active = page == pager.settledPage,
                        controlsVisible = controls,
                        gestures = Modifier.viewerGestures(null, { controls = !controls }, onDrag, onDragEnd),
                    )
                } else {
                    val zoom = remember(media.id) {
                        ZoomState(if (media.height > 0) media.width.toFloat() / media.height else 0f)
                    }
                    PhotoPage(
                        media = media,
                        zoom = zoom,
                        gestures = Modifier.viewerGestures(zoom, { controls = !controls }, onDrag, onDragEnd),
                    )
                }
            }

            AnimatedVisibility(
                visible = controls && dragY == 0f,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                TopControls(current.takenAt, onBack)
            }
            AnimatedVisibility(
                visible = controls && dragY == 0f,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                BottomControls(actions + BarAction(Icons.Outlined.Info, stringResource(R.string.info)) { showInfo = true })
            }
        }
        if (showInfo) InfoSheet(current, viewModel) { showInfo = false }
    }
}

/** The back arrow and when the item was taken, over a fade so they read on bright photos. */
@Composable
private fun TopControls(takenAt: Long, onBack: () -> Unit) {
    val (date, time) = takenAtText(takenAt, currentLocale())
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
            .statusBarsPadding()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
        }
        Column(Modifier.padding(start = 4.dp)) {
            Text(date, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(time, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The row of actions along the bottom. */
@Composable
private fun BottomControls(actions: List<BarAction>) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
            .navigationBarsPadding()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (action in actions) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = action.onClick) { Icon(action.icon, contentDescription = action.label, tint = Color.White) }
                Text(
                    action.label,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

/**
 * Shows or hides Android's status and navigation bars with the viewer's controls (a swipe from
 * the edge brings them back briefly), with light icons over the black. Everything is put back
 * when the viewer closes.
 */
@Composable
private fun SystemBarsVisible(visible: Boolean) {
    val view = LocalView.current
    val window = (LocalContext.current as? Activity)?.window ?: return
    val controller = remember(window, view) { WindowCompat.getInsetsController(window, view) }
    DisposableEffect(controller) {
        val lightBefore = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = lightBefore
            controller.isAppearanceLightNavigationBars = lightBefore
        }
    }
    LaunchedEffect(controller, visible) {
        if (visible) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
