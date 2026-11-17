package app.roll.gallery.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.roll.gallery.R
import app.roll.gallery.data.Media
import app.roll.gallery.ui.GalleryViewModel
import app.roll.gallery.ui.components.BarAction
import app.roll.gallery.ui.components.shareMedia
import app.roll.gallery.ui.theme.GalleryTheme
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * The viewer for a single file another app opened in Gallery ("Open with", or the camera's
 * thumbnail). It works without photo access, since the other app lends access to just that
 * file. It zooms, plays and shares like the main viewer, but has no favorite or trash: the file
 * may not be in the phone's photo library at all. Back or dragging down closes it.
 */
@Composable
fun ExternalViewer(media: Media, viewModel: GalleryViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    var controls by rememberSaveable { mutableStateOf(true) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    SystemBarsVisible(controls)
    val scope = rememberCoroutineScope()
    var dragY by remember { mutableFloatStateOf(0f) }
    val closeAt = with(LocalDensity.current) { 120.dp.toPx() }
    val onDrag: (Float) -> Unit = { dragY += it }
    val onDragEnd: () -> Unit = {
        when {
            dragY > closeAt -> onClose()
            dragY < -closeAt / 1.5f -> showInfo = true
        }
        val from = dragY
        scope.launch { animate(from, 0f) { value, _ -> dragY = value } }
    }
    val zoom = remember(media.uri) { ZoomState(0f) }

    GalleryTheme(darkTheme = true) {
        val fade = (1f - abs(dragY) / (closeAt * 4)).coerceIn(0f, 1f)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = fade))) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationY = dragY }) {
                if (media.isVideo) {
                    VideoPage(
                        media = media,
                        active = true,
                        controlsVisible = controls,
                        gestures = Modifier.viewerGestures(null, { controls = !controls }, onDrag, onDragEnd),
                    )
                } else {
                    PhotoPage(
                        media = media,
                        zoom = zoom,
                        gestures = Modifier.viewerGestures(zoom, { controls = !controls }, onDrag, onDragEnd),
                    )
                }
            }
            AnimatedVisibility(controls && dragY == 0f, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
                TopControls(media.takenAt, onClose)
            }
            AnimatedVisibility(controls && dragY == 0f, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
                BottomControls(
                    listOf(
                        BarAction(Icons.Outlined.Share, stringResource(R.string.share)) { context.shareMedia(listOf(media)) },
                        BarAction(Icons.Outlined.Info, stringResource(R.string.info)) { showInfo = true },
                    ),
                )
            }
        }
        if (showInfo) InfoSheet(media, viewModel) { showInfo = false }
    }
}
