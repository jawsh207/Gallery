package app.roll.gallery.ui.editor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.roll.gallery.R
import app.roll.gallery.data.Adjustments
import app.roll.gallery.data.CropAspect
import app.roll.gallery.data.CropRect
import app.roll.gallery.data.Edits
import app.roll.gallery.data.Filter
import app.roll.gallery.data.Media
import app.roll.gallery.data.canOverwrite
import app.roll.gallery.data.MAX_STRAIGHTEN
import androidx.compose.material.icons.outlined.Straighten
import app.roll.gallery.data.colorMatrix
import app.roll.gallery.data.filterMatrix
import app.roll.gallery.data.fitAspect
import app.roll.gallery.data.turnBitmap
import app.roll.gallery.ui.GalleryViewModel
import app.roll.gallery.ui.components.rememberRequestLauncher
import app.roll.gallery.ui.theme.GalleryTheme
import kotlin.math.roundToInt

/** The editor's three sets of tools. */
private enum class EditTab { Crop, Adjust, Filters }

/** The adjustments the Adjust tab offers, each with how to read and change it. */
private enum class Adjustment(val label: Int, val icon: ImageVector) {
    Brightness(R.string.edit_brightness, Icons.Outlined.LightMode),
    Contrast(R.string.edit_contrast, Icons.Outlined.Contrast),
    Saturation(R.string.edit_saturation, Icons.Outlined.Palette),
    Warmth(R.string.edit_warmth, Icons.Outlined.Thermostat),
    ;

    fun read(a: Adjustments): Float = when (this) {
        Brightness -> a.brightness
        Contrast -> a.contrast
        Saturation -> a.saturation
        Warmth -> a.warmth
    }

    fun write(a: Adjustments, value: Float): Adjustments = when (this) {
        Brightness -> a.copy(brightness = value)
        Contrast -> a.copy(contrast = value)
        Saturation -> a.copy(saturation = value)
        Warmth -> a.copy(warmth = value)
    }
}

/** The colour filter Compose draws with for [matrix]. */
private fun colorFilterOf(matrix: FloatArray): ColorFilter = ColorFilter.colorMatrix(ColorMatrix(matrix))

/**
 * The photo editor: crop (with fixed shapes), straighten, turn, mirror, brightness, contrast, saturation,
 * warmth and filters, previewed live on a screen-sized copy. Hold the photo to see the original.
 * Save writes over the original (after Android's write confirmation) when its format allows,
 * or Save as copy keeps the original and adds an "-edited" file beside it. Nothing is changed
 * until then; leaving with unsaved edits asks first.
 */
@Composable
fun EditorScreen(viewModel: GalleryViewModel, media: Media, onClose: () -> Unit) {
    val preview by produceState<Bitmap?>(null, media.id) { value = viewModel.editPreview(media) }
    var edits by remember(media.id) { mutableStateOf(Edits()) }
    var tab by rememberSaveable { mutableStateOf(EditTab.Crop) }
    var adjustment by rememberSaveable { mutableStateOf(Adjustment.Brightness) }
    var comparing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var saveMenu by remember { mutableStateOf(false) }

    val source = preview
    val turned: ImageBitmap? = remember(source, edits.quarterTurns, edits.flipped) {
        source?.let { turnBitmap(it, edits.quarterTurns, edits.flipped).asImageBitmap() }
    }
    val filter = remember(edits.filter, edits.adjustments) { colorFilterOf(colorMatrix(edits)) }
    val tiny: ImageBitmap? = remember(source) {
        source?.let { b ->
            val side = 160f / maxOf(b.width, b.height)
            Bitmap.createScaledBitmap(b, (b.width * side).roundToInt().coerceAtLeast(1), (b.height * side).roundToInt().coerceAtLeast(1), true).asImageBitmap()
        }
    }
    /** The crop for [aspect] on the photo as currently turned. */
    fun cropFor(aspect: CropAspect, image: ImageBitmap?): CropRect {
        image ?: return CropRect()
        val ratio = if (aspect == CropAspect.Original) image.width.toFloat() / image.height else aspect.ratio
        return fitAspect(ratio, image.width, image.height)
    }

    val finishSave: (Boolean) -> Unit = { asCopy ->
        saving = true
        viewModel.saveEdits(media, edits, asCopy) { ok ->
            saving = false
            if (ok) onClose()
        }
    }
    val writeAccess = rememberRequestLauncher { agreed -> if (agreed) finishSave(false) }
    val leave: () -> Unit = { if (edits.isUnchanged) onClose() else confirmLeave = true }
    BackHandler(onBack = leave)

    GalleryTheme(darkTheme = true) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = leave) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel), tint = Color.White) }
                Spacer(Modifier.weight(1f))
                Box {
                    Button(
                        onClick = { if (canOverwrite(media.mimeType)) saveMenu = true else finishSave(true) },
                        enabled = !edits.isUnchanged && !saving && source != null,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text(stringResource(if (canOverwrite(media.mimeType)) R.string.save else R.string.save_copy)) }
                    DropdownMenu(expanded = saveMenu, onDismissRequest = { saveMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.save)) },
                            onClick = {
                                saveMenu = false
                                writeAccess(viewModel.writeRequest(listOf(media)))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.save_copy)) },
                            onClick = {
                                saveMenu = false
                                finishSave(true)
                            },
                        )
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val image = turned
                when {
                    image == null -> LoadingIndicator()
                    tab == EditTab.Crop -> CropView(
                        image = image,
                        crop = edits.crop,
                        ratio = if (edits.aspect == CropAspect.Original) image.width.toFloat() / image.height else edits.aspect.ratio,
                        degrees = edits.straighten,
                        filter = filter,
                        onChange = { edits = edits.copy(crop = it) },
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                    )
                    else -> EditedImage(
                        image = image,
                        crop = edits.crop,
                        degrees = edits.straighten,
                        filter = if (comparing) null else filter,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(onPress = {
                                    comparing = true
                                    tryAwaitRelease()
                                    comparing = false
                                })
                            },
                    )
                }
                if (saving) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
            }

            Box(Modifier.fillMaxWidth().height(176.dp), contentAlignment = Alignment.Center) {
                when (tab) {
                    EditTab.Crop -> CropTools(
                        aspect = edits.aspect,
                        straighten = edits.straighten,
                        onStraighten = { edits = edits.copy(straighten = it) },
                        onTurn = {
                            val next = edits.copy(quarterTurns = (edits.quarterTurns + 1) % 4)
                            // The photo's sides swap, so the crop starts again from the chosen shape.
                            val image = source?.let { turnBitmap(it, next.quarterTurns, next.flipped).asImageBitmap() }
                            edits = next.copy(crop = cropFor(next.aspect, image))
                        },
                        onFlip = {
                            val c = edits.crop
                            // Mirroring also mirrors the tilt.
                            edits = edits.copy(
                                flipped = !edits.flipped,
                                straighten = -edits.straighten,
                                crop = CropRect(1f - c.right, c.top, 1f - c.left, c.bottom),
                            )
                        },
                        onAspect = { aspect -> edits = edits.copy(aspect = aspect, crop = cropFor(aspect, turned)) },
                        onReset = { edits = edits.copy(quarterTurns = 0, flipped = false, straighten = 0f, crop = CropRect(), aspect = CropAspect.Free) },
                    )
                    EditTab.Adjust -> AdjustTools(
                        selected = adjustment,
                        adjustments = edits.adjustments,
                        onSelect = { adjustment = it },
                        onChange = { edits = edits.copy(adjustments = it) },
                    )
                    EditTab.Filters -> FilterTools(tiny, edits.filter) { edits = edits.copy(filter = it) }
                }
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                EditTab.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        shape = SegmentedButtonDefaults.itemShape(index, EditTab.entries.size),
                    ) {
                        Text(
                            stringResource(
                                when (entry) {
                                    EditTab.Crop -> R.string.edit_crop
                                    EditTab.Adjust -> R.string.edit_adjust
                                    EditTab.Filters -> R.string.edit_filters
                                },
                            ),
                        )
                    }
                }
            }
        }

        if (confirmLeave) {
            AlertDialog(
                onDismissRequest = { confirmLeave = false },
                title = { Text(stringResource(R.string.edit_discard_title)) },
                text = { Text(stringResource(R.string.edit_discard_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmLeave = false
                        onClose()
                    }) { Text(stringResource(R.string.discard)) }
                },
                dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.keep_editing)) } },
            )
        }
    }
}

/** The straighten slider, turn, mirror, the crop shapes, and reset. */
@Composable
private fun CropTools(
    aspect: CropAspect,
    straighten: Float,
    onStraighten: (Float) -> Unit,
    onTurn: () -> Unit,
    onFlip: () -> Unit,
    onAspect: (CropAspect) -> Unit,
    onReset: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Straighten, stringResource(R.string.edit_straighten), tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Slider(
                value = straighten,
                onValueChange = { onStraighten((it * 2).roundToInt() / 2f) },
                valueRange = -MAX_STRAIGHTEN..MAX_STRAIGHTEN,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.edit_degrees, straighten),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(52.dp).clickable { onStraighten(0f) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(onClick = onTurn) { Icon(Icons.Outlined.Rotate90DegreesCw, stringResource(R.string.edit_rotate), tint = Color.White) }
            IconButton(onClick = onFlip) { Icon(Icons.Outlined.Flip, stringResource(R.string.edit_flip), tint = Color.White) }
            IconButton(onClick = onReset) { Icon(Icons.Outlined.RestartAlt, stringResource(R.string.edit_reset), tint = Color.White) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CropAspect.entries) { entry ->
                FilterChip(
                    selected = aspect == entry,
                    onClick = { onAspect(entry) },
                    label = {
                        Text(
                            when (entry) {
                                CropAspect.Free -> stringResource(R.string.edit_aspect_free)
                                CropAspect.Original -> stringResource(R.string.edit_aspect_original)
                                CropAspect.Square -> stringResource(R.string.edit_aspect_square)
                                CropAspect.FourThree -> "4:3"
                                CropAspect.ThreeFour -> "3:4"
                                CropAspect.SixteenNine -> "16:9"
                            },
                        )
                    },
                )
            }
        }
    }
}

/** A chip per adjustment and a slider for the chosen one, from -100 to 100. */
@Composable
private fun AdjustTools(selected: Adjustment, adjustments: Adjustments, onSelect: (Adjustment) -> Unit, onChange: (Adjustments) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Adjustment.entries) { entry ->
                FilterChip(
                    selected = selected == entry,
                    onClick = { onSelect(entry) },
                    label = { Text(stringResource(entry.label)) },
                    leadingIcon = { Icon(entry.icon, contentDescription = null, Modifier.size(18.dp)) },
                )
            }
        }
        val value = selected.read(adjustments)
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = value,
                onValueChange = { onChange(selected.write(adjustments, it)) },
                valueRange = -1f..1f,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                (value * 100).roundToInt().toString(),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(40.dp).clickable { onChange(selected.write(adjustments, 0f)) },
            )
        }
    }
}

/** Each filter as a small preview of the photo with its name. */
@Composable
private fun FilterTools(tiny: ImageBitmap?, current: Filter, onPick: (Filter) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(Filter.entries) { entry ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onPick(entry) }) {
                val shape = RoundedCornerShape(16.dp)
                val ring = if (entry == current) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier
                if (tiny != null) {
                    Image(
                        tiny,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = colorFilterOf(filterMatrix(entry)),
                        modifier = Modifier.size(72.dp).clip(shape).then(ring),
                    )
                }
                Text(
                    stringResource(filterName(entry)),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** The name shown for [filter]. */
private fun filterName(filter: Filter): Int = when (filter) {
    Filter.None -> R.string.filter_none
    Filter.Vivid -> R.string.filter_vivid
    Filter.Warm -> R.string.filter_warm
    Filter.Cool -> R.string.filter_cool
    Filter.Fade -> R.string.filter_fade
    Filter.Mono -> R.string.filter_mono
    Filter.Noir -> R.string.filter_noir
    Filter.Sepia -> R.string.filter_sepia
}
