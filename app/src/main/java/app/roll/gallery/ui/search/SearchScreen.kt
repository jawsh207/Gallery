package app.roll.gallery.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.roll.gallery.R
import app.roll.gallery.Edition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.roll.gallery.ui.locked.rememberMoveToLocked
import app.roll.gallery.ui.components.BarAction
import androidx.compose.material.icons.outlined.Lock
import app.roll.gallery.data.GridZoom
import app.roll.gallery.data.Library
import app.roll.gallery.data.Media
import app.roll.gallery.data.buildAlbums
import app.roll.gallery.data.day
import app.roll.gallery.data.searchMedia
import app.roll.gallery.data.isDescriptionQuery
import app.roll.gallery.ui.GalleryViewModel
import app.roll.gallery.ui.components.EmptyState
import app.roll.gallery.ui.components.SelectionBottomBar
import app.roll.gallery.ui.components.SelectionTopBar
import app.roll.gallery.ui.components.currentLocale
import app.roll.gallery.ui.components.rememberSearchWords
import app.roll.gallery.ui.libraryActions
import app.roll.gallery.ui.photos.MediaGrid
import java.time.ZoneId

/**
 * Search, as in Contacts: the field opens focused, with shortcuts underneath (videos, favorites,
 * screenshots, each year, the biggest albums) until something is typed. In Gallery Plus it also
 * matches the text read in photos. Results show as you
 * type, in the same grid as the Photos tab, and can be selected the same way. In Gallery Plus
 * a person's name finds their photos, and descriptions ("a black dog") find photos that look like them. [onOpen] gets the
 * query too, so the viewer pages through the same results.
 */
@Composable
fun SearchScreen(viewModel: GalleryViewModel, library: Library, onBack: () -> Unit, onOpen: (String, Media) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val words = rememberSearchWords()
    val locale = currentLocale()
    val zone = remember { ZoneId.systemDefault() }
    // Gallery Plus: the text read in photos and people's names count too; picked up fresh each
    // time search opens.
    val photoText by viewModel.searchText.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshRecognition() }
    // Searching by description (Gallery Plus) runs in the background a moment after typing stops;
    // its best matches join the others.
    val described by viewModel.described.collectAsStateWithLifecycle()
    val descriptionMatches by viewModel.descriptionMatches.collectAsStateWithLifecycle()
    LaunchedEffect(query, described) {
        kotlinx.coroutines.delay(400)
        viewModel.searchDescriptions(query, isDescriptionQuery(query, words, locale))
    }
    val extra = if (descriptionMatches.first == query.trim()) descriptionMatches.second else emptySet()
    val results = remember(library, query, words, locale, photoText, extra) {
        searchMedia(library.items, query, words, locale, zone, { photoText[it] }, extra)
    }
    var zoom by rememberSaveable { mutableStateOf(GridZoom.Days) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val selected = results.filter { it.id in selectedIds }
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selectedIds = emptySet() }
    val actions = libraryActions(viewModel, library, selected, onDone = { selectedIds = emptySet() })
    val moveToLocked = rememberMoveToLocked(viewModel) { selectedIds = emptySet() }
    val lockMenu = BarAction(Icons.Outlined.Lock, stringResource(R.string.locked_move_in)) { moveToLocked(selected) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionTopBar(selected.size, menu = listOf(lockMenu)) { selectedIds = emptySet() }
            } else {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    onBack = onBack,
                    onSearch = { focusManager.clearFocus() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(focus),
                )
            }
        },
        bottomBar = { if (selecting) SelectionBottomBar(actions) },
    ) { padding ->
        when {
            query.isBlank() -> Suggestions(
                library = library,
                zone = zone,
                onPick = {
                    query = it
                    focusManager.clearFocus()
                },
                modifier = Modifier.padding(padding).imePadding(),
            )
            results.isEmpty() -> EmptyState(
                Icons.Rounded.SearchOff,
                stringResource(R.string.search_no_results_title),
                stringResource(if (Edition.HAS_TEXT_SEARCH) R.string.search_no_results_body_text else R.string.search_no_results_body, query.trim()),
                Modifier.padding(padding).imePadding(),
            )
            else -> Column(Modifier.padding(top = padding.calculateTopPadding())) {
                Text(
                    pluralStringResource(R.plurals.item_count, results.size, results.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                MediaGrid(
                    items = results,
                    zoom = zoom,
                    onZoomChange = { zoom = it },
                    selection = selectedIds,
                    onSelectionChange = { selectedIds = it },
                    onOpen = { onOpen(query.trim(), it) },
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The search field: back arrow, text, and a clear button once something is typed. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier,
) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = modifier.height(56.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_photos)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.clear_search))
                }
            }
        }
    }
}

/** Shortcuts shown before anything is typed; each one fills in the search. */
@Composable
private fun Suggestions(library: Library, zone: ZoneId, onPick: (String) -> Unit, modifier: Modifier) {
    val years = remember(library.items) { library.items.map { it.day(zone).year }.distinct().sortedDescending() }
    val albums = remember(library.items) { buildAlbums(library.items).sortedByDescending { it.count }.take(8) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        SuggestionGroup(stringResource(R.string.search_kinds)) {
            val videos = stringResource(R.string.videos)
            val favorites = stringResource(R.string.favorites)
            val screenshots = stringResource(R.string.screenshots)
            Suggestion(Icons.Outlined.Videocam, videos) { onPick(videos) }
            Suggestion(Icons.Outlined.FavoriteBorder, favorites) { onPick(favorites) }
            Suggestion(Icons.Outlined.Screenshot, screenshots) { onPick(screenshots) }
        }
        if (years.isNotEmpty()) {
            SuggestionGroup(stringResource(R.string.search_years)) {
                for (year in years) Suggestion(Icons.Outlined.CalendarMonth, year.toString()) { onPick(year.toString()) }
            }
        }
        if (albums.isNotEmpty()) {
            SuggestionGroup(stringResource(R.string.albums)) {
                for (album in albums) Suggestion(Icons.Outlined.PhotoAlbum, album.name) { onPick(album.name) }
            }
        }
    }
}

/** A titled group of suggestion chips that wrap onto more lines as needed. */
@Composable
private fun SuggestionGroup(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** One suggestion chip. */
@Composable
private fun Suggestion(icon: ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, Modifier.height(AssistChipDefaults.IconSize)) },
    )
}
