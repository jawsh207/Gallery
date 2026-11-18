package app.roll.gallery.ui.create

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.roll.gallery.R
import app.roll.gallery.data.Cell
import app.roll.gallery.data.CollageShape
import app.roll.gallery.data.Creation
import app.roll.gallery.data.CreationWriter
import app.roll.gallery.data.Library
import app.roll.gallery.data.Media
import app.roll.gallery.data.PickKinds
import app.roll.gallery.data.Shot
import app.roll.gallery.data.VideoShape
import app.roll.gallery.data.animationFrameMs
import app.roll.gallery.data.animationOrder
import app.roll.gallery.data.cellRect
import app.roll.gallery.data.collageLayouts
import app.roll.gallery.image.thumbnail
import app.roll.gallery.intents.ExternalRequest
import app.roll.gallery.ui.GalleryViewModel
import app.roll.gallery.ui.collections.BackButton
import app.roll.gallery.ui.pick.PickScreen
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * Choosing what [kind] is made from: photos (and clips, for a video) picked in the usual grid.
 * Too few chosen asks for more; too many keeps the first [Creation.most]. [onChosen] gets them,
 * oldest first, so animations and videos play in the order they were taken.
 */
@Composable
fun ChooseScreen(viewModel: GalleryViewModel, library: Library, kind: Creation, onBack: () -> Unit, onChosen: (List<Media>) -> Unit) {
    val (_, title, _) = creationLook(kind)
    PickScreen(
        library = library,
        request = ExternalRequest.Pick(PickKinds(images = true, videos = kind.takesVideos), multiple = kind.most > 1),
        title = stringResource(title),
        onDone = { picked ->
            when {
                picked == null -> onBack()
                picked.size < kind.least -> viewModel.tell(R.string.choose_at_least, kind.least)
                else -> {
                    if (picked.size > kind.most) viewModel.tell(R.string.choose_first_only, kind.most)
                    onChosen(picked.sortedBy { it.takenAt }.take(kind.most))
                }
            }
        },
    )
}

/** The progress of making a video, over the screen, with a way to stop. */
@Composable
private fun MakingDialog(progress: Float?, onCancel: () -> Unit) {
    if (progress == null) return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.making)) },
        text = { LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The usual top bar for a Create screen: back, a title, and a button to make it. */
@Composable
private fun MakeBar(title: String, action: String, enabled: Boolean, onBack: () -> Unit, onMake: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { BackButton(onBack) },
        actions = {
            Button(onClick = onMake, enabled = enabled, modifier = Modifier.padding(end = 8.dp)) { Text(action) }
        },
    )
}

/** A label above a row of controls. */
@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

/** Chips to pick one of [options], named by [name]. */
@Composable
private fun <T> Choice(options: List<T>, selected: T, name: @Composable (T) -> String, onPick: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(options) { _, option ->
            FilterChip(selected = option == selected, onClick = { onPick(option) }, label = { Text(name(option)) })
        }
    }
}

/**
 * A collage of [photos]: pick a layout, a shape, the spacing, the corner rounding and the colour
 * between, see it as it will be, and save it as a new photo. Tapping one photo and then another
 * swaps them.
 */
@Composable
fun CollageScreen(viewModel: GalleryViewModel, photos: List<Media>, onBack: () -> Unit) {
    val layouts = remember(photos.size) { collageLayouts(photos.size) }
    var layout by rememberSaveable { mutableIntStateOf(0) }
    var shape by rememberSaveable { mutableStateOf(CollageShape.Square) }
    var gap by rememberSaveable { mutableFloatStateOf(0.4f) }
    var corners by rememberSaveable { mutableFloatStateOf(0.3f) }
    var dark by rememberSaveable { mutableStateOf(false) }
    var order by remember(photos) { mutableStateOf(photos) }
    var picked by remember { mutableStateOf<Int?>(null) }
    val making by viewModel.making.collectAsStateWithLifecycle()
    val background = if (dark) Color.Black else Color.White
    Scaffold(
        topBar = {
            MakeBar(stringResource(R.string.create_collage), stringResource(R.string.save), making == null, onBack) {
                viewModel.saveCollage(order, layouts[layout], shape, gap, corners, background.toArgb()) { if (it) onBack() }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            CollagePreview(order, layouts[layout], shape, gap, corners, background, picked) { index ->
                val first = picked
                picked = when {
                    first == null -> index
                    first == index -> null
                    else -> {
                        order = order.toMutableList().apply { val a = this[first]; this[first] = this[index]; this[index] = a }
                        null
                    }
                }
            }
            Label(stringResource(R.string.collage_layout))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(layouts) { index, cells ->
                    LayoutIcon(cells, selected = index == layout) { layout = index }
                }
            }
            Label(stringResource(R.string.collage_shape))
            Choice(CollageShape.entries, shape, { stringResource(collageShapeName(it)) }) { shape = it }
            Label(stringResource(R.string.collage_spacing))
            Slider(value = gap, onValueChange = { gap = it })
            Label(stringResource(R.string.collage_corners))
            Slider(value = corners, onValueChange = { corners = it })
            Label(stringResource(R.string.collage_background))
            Choice(listOf(false, true), dark, { stringResource(if (it) R.string.collage_black else R.string.collage_white) }) { dark = it }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The name of a collage shape. */
private fun collageShapeName(shape: CollageShape): Int = when (shape) {
    CollageShape.Square -> R.string.shape_square
    CollageShape.Portrait -> R.string.shape_portrait
    CollageShape.Story -> R.string.shape_story
    CollageShape.Landscape -> R.string.shape_landscape
}

/** The collage as it will be saved, drawn with thumbnails; [picked] is outlined, and [onTap] gets a cell's index. */
@Composable
private fun CollagePreview(
    photos: List<Media>,
    cells: List<Cell>,
    shape: CollageShape,
    gap: Float,
    corners: Float,
    background: Color,
    picked: Int?,
    onTap: (Int) -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        // Tall shapes are kept to a sensible height so the controls stay in reach.
        val maxW = with(density) { maxWidth.toPx() }
        val widthPx = if (shape.ratio < 1f) minOf(maxW, maxW * 1.1f * shape.ratio) else maxW
        val heightPx = widthPx / shape.ratio
        val longSide = maxOf(widthPx, heightPx)
        val gapPx = gap * CreationWriter.MAX_GAP * longSide
        val radius = with(density) { (corners * CreationWriter.MAX_CORNER * longSide).toDp() }
        Box(
            Modifier
                .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
                .background(background),
        ) {
            photos.zip(cells).forEachIndexed { index, (photo, cell) ->
                val r = cellRect(cell, widthPx, heightPx, gapPx)
                val cellShape = RoundedCornerShape(radius)
                AsyncImage(
                    model = photo.thumbnail(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset(with(density) { r[0].toDp() }, with(density) { r[1].toDp() })
                        .size(with(density) { (r[2] - r[0]).toDp() }, with(density) { (r[3] - r[1]).toDp() })
                        .clip(cellShape)
                        .then(if (picked == index) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, cellShape) else Modifier)
                        .clickable { onTap(index) },
                )
            }
        }
    }
}

/** A small drawing of a layout's cells, outlined when it's the one in use. */
@Composable
private fun LayoutIcon(cells: List<Cell>, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val side = 56.dp
    Box(
        Modifier
            .size(side)
            .clip(RoundedCornerShape(10.dp))
            .border(if (selected) 2.dp else 1.dp, color, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            for (cell in cells) {
                Box(
                    Modifier
                        .offset(maxWidth * cell.left + 1.dp, maxHeight * cell.top + 1.dp)
                        .size(maxWidth * cell.width - 2.dp, maxHeight * cell.height - 2.dp)
                        .background(color.copy(alpha = if (selected) 0.5f else 0.3f), RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

/** The name of a video frame shape. */
private fun videoShapeName(shape: VideoShape): Int = when (shape) {
    VideoShape.Landscape -> R.string.shape_landscape
    VideoShape.Portrait -> R.string.shape_portrait
    VideoShape.Square -> R.string.shape_square
}

/** The frame that suits [first]: portrait for a tall photo, landscape for a wide one. */
private fun shapeFor(first: Media?): VideoShape = when {
    first == null || first.width == first.height -> VideoShape.Square
    first.height > first.width -> VideoShape.Portrait
    else -> VideoShape.Landscape
}

/**
 * An animation of [photos]: shown one after another, quickly, previewed here on a loop, then
 * saved as a short video (played through enough times to last at least a few seconds). Speed,
 * playing there and back, and the frame shape can be changed.
 */
@Composable
fun AnimationScreen(viewModel: GalleryViewModel, photos: List<Media>, onBack: () -> Unit) {
    var speed by rememberSaveable { mutableFloatStateOf(0.6f) }
    var bounce by rememberSaveable { mutableStateOf(false) }
    var shape by rememberSaveable { mutableStateOf(shapeFor(photos.firstOrNull())) }
    val making by viewModel.making.collectAsStateWithLifecycle()
    val order = remember(photos.size, bounce) { animationOrder(photos.size, bounce) }
    val frameMs = animationFrameMs(speed)
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(order, frameMs) {
        while (true) {
            delay(frameMs)
            frame = (frame + 1) % order.size
        }
    }
    BackHandler(enabled = making != null) { viewModel.cancelMaking() }
    MakingDialog(making, viewModel::cancelMaking)
    Scaffold(
        topBar = {
            MakeBar(stringResource(R.string.create_animation), stringResource(R.string.save), making == null, onBack) {
                // At least three seconds, so it doesn't flash by in a player that doesn't loop.
                val rounds = ((ANIMATION_MIN_MS + order.size * frameMs - 1) / (order.size * frameMs)).coerceAtLeast(1)
                val shots = List(rounds.toInt()) { order }.flatten().map { Shot(photos[it], frameMs) }
                viewModel.makeMovie(shots, shape, fill = true, music = null, prefix = "Animation") { if (it) onBack() }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            val current = photos.getOrNull(order.getOrElse(frame % order.size) { 0 })
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = current?.thumbnail(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth(if (shape == VideoShape.Portrait) 0.6f else 1f)
                        .aspectRatio(shape.width.toFloat() / shape.height)
                        .clip(RoundedCornerShape(16.dp)),
                )
            }
            Text(
                pluralStringResource(R.plurals.photo_count, photos.size, photos.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Label(stringResource(R.string.animation_speed))
            Slider(value = speed, onValueChange = { speed = it })
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.animation_bounce), Modifier.weight(1f))
                Switch(checked = bounce, onCheckedChange = { bounce = it })
            }
            Label(stringResource(R.string.video_shape))
            Choice(VideoShape.entries, shape, { stringResource(videoShapeName(it)) }) { shape = it }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** How long an animation lasts at least, in milliseconds. */
private const val ANIMATION_MIN_MS = 3000L

/** The photo lengths the video maker offers, in milliseconds. */
private val PHOTO_LENGTHS = listOf(2000L, 3000L, 5000L)

/**
 * A slideshow video of [items], photos and clips in the order they were taken: each photo shows
 * for the length picked and clips play in full. Music can be added from any audio file on the
 * phone, chosen with Android's file picker. The frame shape, and whether to fill it (cropping) or
 * fit inside it (black bars), can be changed.
 */
@Composable
fun VideoMakerScreen(viewModel: GalleryViewModel, items: List<Media>, onBack: () -> Unit) {
    var photoMs by rememberSaveable { mutableStateOf(3000L) }
    var shape by rememberSaveable { mutableStateOf(shapeFor(items.firstOrNull())) }
    var fill by rememberSaveable { mutableStateOf(false) }
    var music by rememberSaveable { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val musicName = remember(music) {
        music?.let { uri ->
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()
        }
    }
    val pickMusic = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) music = uri }
    val making by viewModel.making.collectAsStateWithLifecycle()
    val totalMs = items.sumOf { if (it.isVideo) it.durationMs else photoMs }
    BackHandler(enabled = making != null) { viewModel.cancelMaking() }
    MakingDialog(making, viewModel::cancelMaking)
    Scaffold(
        topBar = {
            MakeBar(stringResource(R.string.create_video), stringResource(R.string.save), making == null, onBack) {
                viewModel.makeMovie(items.map { Shot(it, photoMs) }, shape, fill, music, prefix = "Movie") { if (it) onBack() }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(top = 8.dp)) {
                itemsIndexed(items) { _, media ->
                    AsyncImage(
                        model = media.thumbnail(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
            Text(
                stringResource(R.string.video_length, (totalMs / 1000) / 60, (totalMs / 1000) % 60),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (items.any { !it.isVideo }) {
                Label(stringResource(R.string.video_photo_length))
                Choice(PHOTO_LENGTHS, photoMs, { stringResource(R.string.seconds_short, it / 1000) }) { photoMs = it }
            }
            Label(stringResource(R.string.video_shape))
            Choice(VideoShape.entries, shape, { stringResource(videoShapeName(it)) }) { shape = it }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.video_fill), Modifier.weight(1f))
                Switch(checked = fill, onCheckedChange = { fill = it })
            }
            Label(stringResource(R.string.video_music))
            if (music == null) {
                OutlinedButton(onClick = { pickMusic.launch(arrayOf("audio/*")) }) {
                    Icon(Icons.Outlined.MusicNote, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.video_add_music))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        musicName ?: stringResource(R.string.video_music_chosen),
                        Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { music = null }) { Icon(Icons.Rounded.Close, stringResource(R.string.video_remove_music)) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
