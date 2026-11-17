package app.roll.gallery.ui.photos

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.roll.gallery.R
import app.roll.gallery.data.GridZoom
import app.roll.gallery.data.Media
import app.roll.gallery.data.Section
import app.roll.gallery.data.SectionLabel
import app.roll.gallery.data.buildSections
import app.roll.gallery.data.dragSelection
import app.roll.gallery.ui.components.MediaThumb
import app.roll.gallery.ui.components.text
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.isActive

/** One cell of the grid: a group header or a photo, with the group it belongs to. */
sealed interface GridEntry {
    val section: Section

    /** The header above a group. */
    data class Header(override val section: Section) : GridEntry

    /** A photo or video in a group. */
    data class Cell(val media: Media, override val section: Section) : GridEntry
}

/**
 * The photo grid used by the Photos tab, albums and the trash: photos grouped under date
 * headers, pinch to zoom between [GridZoom] levels, tap to open, and long-press to start
 * selecting, then drag across photos to select a run of them (the grid scrolls by itself when
 * the finger nears its top or bottom edge). A fast-scroll handle appears while scrolling.
 *
 * [header] adds anything that should scroll above the photos (a banner, an intro line).
 */
@Composable
fun MediaGrid(
    items: List<Media>,
    zoom: GridZoom,
    onZoomChange: (GridZoom) -> Unit,
    selection: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit,
    onOpen: (Media) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(),
    header: LazyGridScope.() -> Unit = {},
) {
    val today = remember { LocalDate.now() }
    val zone = remember { ZoneId.systemDefault() }
    val sections = remember(items, zoom.byMonth) { buildSections(items, zoom.byMonth, today, zone) }
    val entries = remember(sections) {
        sections.flatMap { section -> listOf(GridEntry.Header(section)) + section.items.map { GridEntry.Cell(it, section) } }
    }
    val order = remember(items) { items.map { it.id } }
    val indexOfId = remember(items) { HashMap<Long, Int>(items.size).apply { items.forEachIndexed { i, m -> put(m.id, i) } } }
    val selecting = selection.isNotEmpty()

    val currentZoom by rememberUpdatedState(zoom)
    val zoomTo by rememberUpdatedState(onZoomChange)
    val currentSelection by rememberUpdatedState(selection)
    val select by rememberUpdatedState(onSelectionChange)
    val currentOrder by rememberUpdatedState(order)
    val currentIndexOfId by rememberUpdatedState(indexOfId)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val edge = with(density) { 72.dp.toPx() }
    val maxSpeed = with(density) { 24.dp.toPx() }

    // Keeps the photo that was at the top in view when the zoom level changes.
    var anchorId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(entries) {
        val id = anchorId ?: return@LaunchedEffect
        anchorId = null
        val index = entries.indexOfFirst { it is GridEntry.Cell && it.media.id == id }
        if (index >= 0) state.scrollToItem(index)
    }

    /** The index (in [order]) of the photo under [position], or null over a header or gap. */
    fun indexAt(position: Offset): Int? {
        val info = state.layoutInfo
        val y = position.y + info.viewportStartOffset
        val hit = info.visibleItemsInfo.firstOrNull { item ->
            position.x >= item.offset.x && position.x < item.offset.x + item.size.width &&
                y >= item.offset.y && y < item.offset.y + item.size.height
        } ?: return null
        return (hit.key as? Long)?.let { currentIndexOfId[it] }
    }

    // The drag that's selecting photos, if any, and how fast it's scrolling the grid.
    var drag by remember { mutableStateOf<DragSelect?>(null) }
    var autoScroll by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(autoScroll != 0f) {
        while (isActive && autoScroll != 0f) {
            state.scrollBy(autoScroll)
            drag?.let { d -> indexAt(d.pointer)?.let { select(d.selectionTo(it, currentOrder)) } }
            withFrameNanos { }
        }
    }

    Box(modifier) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(zoom.columns),
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxSize()
                // Pinch: two fingers apart zooms in a level, together zooms out. Read before the
                // grid sees the touch (Initial pass), so the grid doesn't scroll while pinching.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var scale = 1f
                        var changed = false
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                scale *= event.calculateZoom()
                                if (!changed && (scale > 1.25f || scale < 0.8f)) {
                                    val next = if (scale > 1f) currentZoom.zoomIn() else currentZoom.zoomOut()
                                    if (next != currentZoom) {
                                        // Switching between days and months changes the headers, which the
                                        // grid can't follow by key; otherwise it keeps its place itself.
                                        if (next.byMonth != currentZoom.byMonth) {
                                            anchorId = state.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { it.key as? Long }
                                        }
                                        zoomTo(next)
                                    }
                                    changed = true
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                // Long-press then drag to select a run of photos.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        val start = indexAt(press.position) ?: return@awaitEachGesture
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val base = currentSelection
                        val d = DragSelect(base, start, selecting = currentOrder[start] !in base, pointer = press.position)
                        drag = d
                        select(d.selectionTo(start, currentOrder))
                        press.consume()
                        while (true) {
                            // Initial pass, so the photo under the finger doesn't also see a tap
                            // when it lifts, and the grid doesn't scroll on its own.
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == press.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            d.pointer = change.position
                            indexAt(change.position)?.let { select(d.selectionTo(it, currentOrder)) }
                            val top = -state.layoutInfo.viewportStartOffset + edge
                            val bottom = size.height - state.layoutInfo.afterContentPadding - edge
                            autoScroll = when {
                                change.position.y < top -> -maxSpeed * ((top - change.position.y) / edge).coerceAtMost(1f)
                                change.position.y > bottom -> maxSpeed * ((change.position.y - bottom) / edge).coerceAtMost(1f)
                                else -> 0f
                            }
                        }
                        drag = null
                        autoScroll = 0f
                    }
                },
        ) {
            header()
            for (entry in entries) {
                when (entry) {
                    is GridEntry.Header -> item(
                        key = "h:${entry.section.key}",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "header",
                    ) {
                        SectionHeader(
                            label = entry.section.label,
                            selecting = selecting,
                            allSelected = selecting && entry.section.items.all { it.id in selection },
                            onToggleAll = {
                                val ids = entry.section.items.map { it.id }
                                select(if (ids.all { it in selection }) selection - ids.toSet() else selection + ids)
                            },
                        )
                    }
                    is GridEntry.Cell -> item(key = entry.media.id, contentType = "cell") {
                        val media = entry.media
                        MediaThumb(
                            media = media,
                            selected = media.id in selection,
                            selecting = selecting,
                            onClick = {
                                if (selecting) {
                                    select(if (media.id in selection) selection - media.id else selection + media.id)
                                } else {
                                    onOpen(media)
                                }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
        FastScroller(
            state = state,
            entries = entries,
            contentPadding = contentPadding,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

/** A long-press drag in progress: where it started, what was selected before, and the finger. */
private class DragSelect(val base: Set<Long>, val anchor: Int, val selecting: Boolean, var pointer: Offset) {
    fun selectionTo(current: Int, order: List<Long>): Set<Long> = dragSelection(base, order, anchor, current, selecting)
}

/** A group's date, with a circle to select the whole group while selecting. */
@Composable
private fun SectionHeader(label: SectionLabel, selecting: Boolean, allSelected: Boolean, onToggleAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.text(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (selecting) {
            IconButton(onClick = onToggleAll, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (allSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = stringResource(if (allSelected) R.string.deselect_day else R.string.select_day),
                    tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The month a grid entry belongs to, for the fast-scroll bubble. */
internal fun GridEntry.month(zone: ZoneId): YearMonth =
    YearMonth.from(section.items.first().let { java.time.Instant.ofEpochMilli(it.takenAt).atZone(zone) })
