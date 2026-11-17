package app.roll.gallery.ui.photos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.roll.gallery.R
import app.roll.gallery.ui.components.currentLocale
import app.roll.gallery.ui.components.formatSkeleton
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The handle on the grid's right edge for jumping through a big library. It slides in while
 * the grid scrolls and hides a moment after; dragging it jumps straight to that point and
 * shows the month there. Short grids don't get one.
 */
@Composable
fun FastScroller(
    state: LazyGridState,
    entries: List<GridEntry>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val total = entries.size
    if (total < 60) return
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(state.isScrollInProgress, dragging) {
        if (state.isScrollInProgress || dragging) {
            visible = true
        } else {
            delay(1500)
            visible = false
        }
    }
    val zone = remember { ZoneId.systemDefault() }
    val locale = currentLocale()
    val thumbHeight = 52.dp
    val thumbPx = with(LocalDensity.current) { thumbHeight.toPx() }

    BoxWithConstraints(
        modifier
            .fillMaxHeight()
            .padding(top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 8.dp),
    ) {
        val track = (constraints.maxHeight - thumbPx).coerceAtLeast(1f)
        val scrolled by remember(state, total) {
            derivedStateOf {
                val shown = state.layoutInfo.visibleItemsInfo.size
                (state.firstVisibleItemIndex.toFloat() / (total - shown).coerceAtLeast(1)).coerceIn(0f, 1f)
            }
        }
        var dragFraction by remember { mutableFloatStateOf(0f) }
        val fraction = if (dragging) dragFraction else scrolled
        val draggableState = rememberDraggableState { delta ->
            dragFraction = (dragFraction + delta / track).coerceIn(0f, 1f)
            val index = ((total - 1) * dragFraction).roundToInt()
            scope.launch { state.scrollToItem(index) }
        }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (track * fraction).roundToInt()) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dragging) {
                    val entry = entries[((total - 1) * dragFraction).roundToInt().coerceIn(0, total - 1)]
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(
                            formatSkeleton(entry.month(zone), "MMMMyyyy", locale),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(width = 36.dp, height = thumbHeight)
                        .draggable(
                            state = draggableState,
                            orientation = Orientation.Vertical,
                            onDragStarted = {
                                dragFraction = scrolled
                                dragging = true
                            },
                            onDragStopped = { dragging = false },
                        ),
                ) {
                    Icon(
                        Icons.Rounded.UnfoldMore,
                        contentDescription = stringResource(R.string.fast_scroll),
                        modifier = Modifier.padding(6.dp),
                    )
                }
            }
        }
    }
}
