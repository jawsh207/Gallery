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
import app.roll.gallery.ui.components.EmptyState

/**
 * The Photos tab: every photo and video, newest first, in the date-grouped grid. With only
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
            if (partialAccess) {
                item(key = "partial", span = { GridItemSpan(maxLineSpan) }, contentType = "banner") {
                    PartialAccessCard(onAllowAll)
                }
            }
        },
    )
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
