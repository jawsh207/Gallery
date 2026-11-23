package app.roll.gallery.ui.viewer

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.launch

/**
 * How far a photo in the viewer is zoomed and moved. [aspect] (width ÷ height, 0 if unknown)
 * lets panning stop at the photo's edges rather than the screen's, so a zoomed-in wide photo
 * can't be dragged off into the black bars around it.
 */
@Stable
class ZoomState(private val aspect: Float) {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    /** The viewer's size, set when it's laid out. */
    var container: IntSize = IntSize.Zero

    /** True while zoomed in past the fitted size. */
    val isZoomed: Boolean get() = scale > 1.01f

    /** Applies a pinch: [zoom] around [centroid], plus a [pan], all in screen pixels. */
    fun transform(zoom: Float, pan: Offset, centroid: Offset) {
        // A position that isn't a number would move the photo off the screen for good.
        if (!centroid.isSpecified || zoom.isNaN() || !pan.isValid()) return
        val newScale = (scale * zoom).coerceIn(1f, MAX_SCALE)
        val factor = newScale / scale
        val c = centroid - center()
        offset = clamp((offset - c) * factor + c + pan, newScale)
        scale = newScale
    }

    /** Double-tap: zooms in on [tap], or back out if already zoomed. */
    suspend fun toggle(tap: Offset) {
        if (isZoomed) {
            animateTo(1f, Offset.Zero)
        } else {
            // Keeps the tapped point under the finger: it moves by c × (scale − 1).
            val c = tap - center()
            animateTo(DOUBLE_TAP_SCALE, clamp(c * (1 - DOUBLE_TAP_SCALE), DOUBLE_TAP_SCALE))
        }
    }

    /** Snaps back inside the edges after a gesture ends. */
    fun settle() {
        if (!offset.isValid()) offset = Offset.Zero
        if (!isZoomed) {
            scale = 1f
            offset = Offset.Zero
        } else {
            offset = clamp(offset, scale)
        }
    }

    private suspend fun animateTo(targetScale: Float, targetOffset: Offset) {
        val startScale = scale
        val startOffset = offset
        animate(0f, 1f) { value, _ ->
            scale = lerp(startScale, targetScale, value)
            offset = androidx.compose.ui.geometry.lerp(startOffset, targetOffset, value)
        }
    }

    private fun center() = Offset(container.width / 2f, container.height / 2f)

    /** Keeps the scaled photo covering the screen wherever it's bigger than the screen. */
    private fun clamp(value: Offset, scale: Float): Offset {
        val w = container.width.toFloat()
        val h = container.height.toFloat()
        if (w <= 0f || h <= 0f) return value
        // The photo's size when fitted to the screen.
        val (fitW, fitH) = when {
            aspect <= 0f -> w to h
            aspect > w / h -> w to w / aspect
            else -> h * aspect to h
        }
        val maxX = max(0f, (fitW * scale - w) / 2f)
        val maxY = max(0f, (fitH * scale - h) / 2f)
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    private companion object {
        const val MAX_SCALE = 6f
        const val DOUBLE_TAP_SCALE = 2.5f
    }
}

/**
 * The viewer's touch handling for one page: tap to show or hide the controls, double-tap and
 * pinch to zoom (when [zoom] is given), drag to move a zoomed photo, and drag up or down to
 * close or open details ([onVerticalDrag] with each step, [onVerticalDragEnd] on lifting).
 *
 * Sideways swipes are left alone so the pager can move between photos, except while zoomed
 * in, when a drag moves the photo instead.
 */
@Composable
fun Modifier.viewerGestures(
    zoom: ZoomState?,
    onTap: () -> Unit,
    onVerticalDrag: (Float) -> Unit,
    onVerticalDragEnd: () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val tap by rememberUpdatedState(onTap)
    val drag by rememberUpdatedState(onVerticalDrag)
    val dragEnd by rememberUpdatedState(onVerticalDragEnd)
    val doubleTap: ((Offset) -> Unit)? = zoom?.let { z -> { position: Offset -> scope.launch { z.toggle(position) }; Unit } }
    return this
        .pointerInput(zoom) {
            detectTapGestures(onTap = { tap() }, onDoubleTap = doubleTap)
        }
        .pointerInput(zoom) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var mode = Mode.Undecided
                var travel = Offset.Zero
                do {
                    val event = awaitPointerEvent()
                    val fingers = event.changes.count { it.pressed }
                    if (zoom != null && mode != Mode.Vertical && (fingers >= 2 || zoom.isZoomed)) {
                        mode = Mode.Transform
                        // The event that lifts the last finger has no fingers down, so no centre
                        // (it comes back unspecified, not a number); nothing moves on that one.
                        val centroid = event.calculateCentroid(useCurrent = true)
                        if (fingers > 0 && centroid.isSpecified) {
                            zoom.transform(event.calculateZoom(), event.calculatePan(), centroid)
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    } else if (mode == Mode.Undecided) {
                        travel += event.changes.first().positionChange()
                        if (travel.getDistance() > viewConfiguration.touchSlop) {
                            mode = if (abs(travel.y) > abs(travel.x) * 1.4f) Mode.Vertical else Mode.Ignore
                        }
                    } else if (mode == Mode.Vertical) {
                        val change = event.changes.first()
                        drag(change.positionChange().y)
                        change.consume()
                    }
                } while (event.changes.any { it.pressed })
                if (mode == Mode.Vertical) dragEnd()
                if (mode == Mode.Transform) zoom?.settle()
            }
        }
}

/** What a touch on a viewer page has turned out to be. */
private enum class Mode { Undecided, Transform, Vertical, Ignore }
