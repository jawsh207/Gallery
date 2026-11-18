package app.roll.gallery.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.roll.gallery.R
import app.roll.gallery.data.DuplicateGroup
import app.roll.gallery.data.DuplicateKind
import app.roll.gallery.data.Library
import app.roll.gallery.data.formatBytes
import app.roll.gallery.image.thumbnail
import app.roll.gallery.ui.GalleryViewModel
import app.roll.gallery.ui.components.EmptyState
import app.roll.gallery.ui.components.currentLocale
import app.roll.gallery.ui.components.rememberTrash
import app.roll.gallery.ui.components.takenAtText
import coil3.compose.AsyncImage

/**
 * The duplicate finder: copies of the same photo, and near-identical shots taken moments apart.
 * It looks through every photo in the background (showing how far it's got), then lists each
 * group with every photo but the suggested keeper ticked. Tap a photo to tick or untick it;
 * the button moves everything ticked to the trash, where it can still be restored for 30 days.
 */
@Composable
fun DuplicatesScreen(viewModel: GalleryViewModel, library: Library, onBack: () -> Unit) {
    val scan by viewModel.duplicates.collectAsStateWithLifecycle()
    // Runs again whenever the library changes (after trashing, say); unchanged photos aren't re-read.
    LaunchedEffect(library) { viewModel.scanDuplicates(library) }
    val groups = scan?.groups
    var marked by remember { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(groups) {
        marked = groups.orEmpty().flatMap { g -> g.items.filter { it.id != g.keep.id } }.map { it.id }.toSet()
    }
    val markedItems = groups.orEmpty().flatMap { it.items }.filter { it.id in marked }
    val trash = rememberTrash(viewModel)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.duplicates)) }, navigationIcon = { BackButton(onBack) }) },
        bottomBar = {
            if (markedItems.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Button(
                        onClick = { trash(markedItems) },
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    ) {
                        Text(
                            pluralStringResource(
                                R.plurals.trash_duplicates, markedItems.size, markedItems.size,
                                formatBytes(markedItems.sumOf { it.sizeBytes }),
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        when {
            groups == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val current = scan
                val fraction = if (current == null || current.total == 0) 0f else current.done.toFloat() / current.total
                LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.duplicates_scanning, current?.done ?: 0, current?.total ?: 0),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            groups.isEmpty() -> EmptyState(
                Icons.Outlined.ContentCopy,
                stringResource(R.string.duplicates_none_title),
                stringResource(R.string.duplicates_none_body),
                Modifier.padding(padding),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "intro") {
                    Text(
                        stringResource(R.string.duplicates_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                items(groups, key = { g -> g.items.joinToString(",") { it.id.toString() } }) { group ->
                    GroupRow(group, marked) { id -> marked = if (id in marked) marked - id else marked + id }
                }
            }
        }
    }
}

/** One group: what kind it is and when, then its photos side by side. */
@Composable
private fun GroupRow(group: DuplicateGroup, marked: Set<Long>, onToggle: (Long) -> Unit) {
    val (date, _) = takenAtText(group.items.first().takenAt, currentLocale())
    Column {
        Text(
            stringResource(if (group.kind == DuplicateKind.Exact) R.string.duplicates_exact else R.string.duplicates_similar) + " · " + date,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(group.items, key = { it.id }) { media ->
                val isMarked = media.id in marked
                Box(
                    Modifier
                        .size(112.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onToggle(media.id) },
                ) {
                    AsyncImage(
                        model = media.thumbnail(),
                        contentDescription = stringResource(R.string.photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                    if (isMarked) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = stringResource(R.string.duplicates_marked),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .background(MaterialTheme.colorScheme.onPrimary, CircleShape),
                        )
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                        ) {
                            Text(
                                stringResource(R.string.duplicates_keep),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
