package app.roll.gallery.ui.photos

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.roll.gallery.R
import app.roll.gallery.data.GridZoom
import app.roll.gallery.data.Media
import app.roll.gallery.data.Memory
import app.roll.gallery.image.thumbnail
import coil3.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import app.roll.gallery.ui.components.EmptyState

/**
 * The Photos tab: every photo and video, newest first, in the date-grouped grid, under a row
 * of "On this day" [memories] when there are any ([onOpenMemory] plays one). With only
 * some photos shared with the app ("Select photos" access), a card at the top offers to allow
 * all of them.
 */
@Composable
fun PhotosTab(
    items: List<Media>,
    partialAccess: Boolean,
    onAllowAll: () -> Unit,
    zoom: GridZoom,
    onZoomChange: (GridZoom) -> Unit,
    selection: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit,
    onOpen: (Media) -> Unit,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    memories: List<Memory> = emptyList(),
    onOpenMemory: (Memory) -> Unit = {},
) {
    if (items.isEmpty() && !partialAccess) {
        EmptyState(
            Icons.Outlined.PhotoLibrary,
            stringResource(R.string.no_photos_title),
            stringResource(R.string.no_photos_body),
            Modifier.padding(contentPadding),
        )
        return
    }
    MediaGrid(
        items = items,
        zoom = zoom,
        onZoomChange = onZoomChange,
        selection = selection,
        onSelectionChange = onSelectionChange,
        onOpen = onOpen,
        state = gridState,
        contentPadding = contentPadding,
        header = {
            if (memories.isNotEmpty()) {
                item(key = "memories", span = { GridItemSpan(maxLineSpan) }, contentType = "memories") {
                    MemoriesRow(memories, onOpenMemory)
                }
            }
            if (partialAccess) {
                item(key = "partial", span = { GridItemSpan(maxLineSpan) }, contentType = "banner") {
                    PartialAccessCard(onAllowAll)
                }
            }
        },
    )
}

/** "On this day": a card per earlier year with photos from around today's date. */
@Composable
private fun MemoriesRow(memories: List<Memory>, onOpen: (Memory) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.on_this_day),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(memories, key = { it.yearsAgo }) { memory ->
                val label = pluralStringResource(R.plurals.years_ago, memory.yearsAgo, memory.yearsAgo)
                Box(
                    Modifier
                        .size(width = 120.dp, height = 168.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClickLabel = label) { onOpen(memory) },
                ) {
                    AsyncImage(
                        model = memory.cover.thumbnail(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))),
                    )
                    Text(
                        label,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    )
                }
            }
        }
    }
}

/** Explains that the app sees only the photos the user picked, with a button to allow all. */
@Composable
private fun PartialAccessCard(onAllowAll: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.partial_access_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.partial_access_body), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Button(onClick = onAllowAll) { Text(stringResource(R.string.allow_all)) }
            }
        }
    }
}
