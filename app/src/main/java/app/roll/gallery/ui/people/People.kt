package app.roll.gallery.ui.people

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.roll.gallery.R
import app.roll.gallery.data.GridZoom
import app.roll.gallery.data.Library
import app.roll.gallery.data.Media
import app.roll.gallery.image.FaceCrop
import app.roll.gallery.recognition.Person
import app.roll.gallery.ui.GalleryViewModel
import app.roll.gallery.ui.collections.BackButton
import app.roll.gallery.ui.components.SelectionBottomBar
import app.roll.gallery.ui.components.SelectionTopBar
import app.roll.gallery.ui.components.TextInputDialog
import app.roll.gallery.ui.libraryActions
import app.roll.gallery.ui.photos.MediaGrid
import coil3.compose.AsyncImage

/** A person's round picture: their clearest face, cut from [photo]. */
@Composable
fun PersonPicture(person: Person, photo: Media?, modifier: Modifier = Modifier) {
    if (photo == null) {
        Icon(Icons.Outlined.Face, contentDescription = null, modifier = modifier)
        return
    }
    val c = person.cover
    AsyncImage(
        model = FaceCrop(photo.uri, photo.modifiedAt, c.left, c.top, c.width, c.height),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(CircleShape),
    )
}

/**
 * The People row on the Collections tab (Gallery Plus): everyone found in the photos, as round
 * pictures with their names, or "Add a name". Tapping one opens their photos.
 */
@Composable
fun PeopleRow(people: List<Person>, photosById: Map<Long, Media>, onOpen: (Person) -> Unit) {
    Column {
        Text(
            stringResource(R.string.people),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) {
            items(people, key = { it.key }) { person ->
                Column(
                    Modifier.width(80.dp).clickable { onOpen(person) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PersonPicture(person, photosById[person.coverPhoto], Modifier.size(72.dp))
                    Text(
                        person.name ?: stringResource(R.string.person_add_name),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (person.name == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * One person's photos (Gallery Plus), in the usual grid with the usual selecting, and their name
 * in the top bar with a button to give or change it. People are worked out again as photos are
 * looked at, so the person is found by [personKey]; naming them can change their key, which
 * [onKeyChange] reports, and if they can no longer be found the screen closes.
 */
@Composable
fun PersonScreen(
    viewModel: GalleryViewModel,
    library: Library,
    personKey: String,
    onKeyChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpen: (Media) -> Unit,
) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    val person = people.firstOrNull { it.key == personKey }
    if (person == null) {
        // Give the grouping a moment (it's worked out in the background) before giving up.
        LaunchedEffect(people) {
            kotlinx.coroutines.delay(1500)
            onBack()
        }
        return
    }
    val items = remember(library.items, person) { library.items.filter { it.id in person.photoIds } }
    var zoom by rememberSaveable { mutableStateOf(GridZoom.Days) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val selected = items.filter { it.id in selectedIds }
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selectedIds = emptySet() }
    val actions = libraryActions(viewModel, library, selected, onDone = { selectedIds = emptySet() })
    var naming by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionTopBar(selected.size) { selectedIds = emptySet() }
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(person.name ?: stringResource(R.string.person_add_name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                pluralStringResource(R.plurals.item_count, items.size, items.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = { BackButton(onBack) },
                    actions = {
                        IconButton(onClick = { naming = true }) {
                            Icon(Icons.Outlined.Edit, stringResource(R.string.person_rename))
                        }
                    },
                )
            }
        },
        bottomBar = { if (selecting) SelectionBottomBar(actions) },
    ) { padding ->
        MediaGrid(
            items = items,
            zoom = zoom,
            onZoomChange = { zoom = it },
            selection = selectedIds,
            onSelectionChange = { selectedIds = it },
            onOpen = onOpen,
            contentPadding = padding,
        )
    }

    if (naming) {
        TextInputDialog(
            title = stringResource(R.string.person_rename),
            initial = person.name.orEmpty(),
            confirmLabel = stringResource(R.string.save),
            fieldLabel = stringResource(R.string.person_name),
            onConfirm = { name ->
                naming = false
                viewModel.renamePerson(person, name)
                // A named person is known by their first face from now on.
                onKeyChange(person.faceKeys.first())
            },
            onDismiss = { naming = false },
        )
    }
}
