package app.roll.gallery.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.roll.gallery.R
import app.roll.gallery.data.CropRect
import app.roll.gallery.data.Mark

/** What a touch does in Markup: draw a line, highlight, or place text. */
enum class MarkTool { Pen, Highlighter, Text }

/** The colours Markup offers. */
val MARK_COLORS = listOf(Color.White, Color.Black, Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5))

/** Pen and highlighter widths, and text size, as shares of the photo's shorter side. */
private const val PEN_WIDTH = 0.008f
private const val HIGHLIGHTER_WIDTH = 0.04f
private const val TEXT_SIZE = 0.06f

/**
 * The photo with its marks, where a drag draws with [tool] in [color] ([onAdd] gets the
 * finished mark) and, for text, a tap asks what to write there. Marks are kept as fractions of
 * the finished photo (see [Mark]), so they save at any size.
 */
@Composable
fun MarkupView(
    image: ImageBitmap,
    crop: CropRect,
    degrees: Float,
    filter: ColorFilter?,
    vignette: Float,
    markup: List<Mark>,
    tool: MarkTool,
    color: Color,
    onAdd: (Mark) -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawing = remember { mutableStateListOf<Float>() }
    var textAt by remember { mutableStateOf<Offset?>(null) }
    val currentTool by rememberUpdatedState(tool)
    val currentColor by rememberUpdatedState(color)
    val add by rememberUpdatedState(onAdd)
    val live = if (drawing.size >= 2) {
        markup + Mark.Stroke(drawing.toList(), color.toArgb(), widthFor(tool), tool == MarkTool.Highlighter)
    } else {
        markup
    }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val area = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val frame = editedFrame(image, crop, area)
        fun toPhoto(at: Offset) = Offset(
            ((at.x - frame.left) / frame.width).coerceIn(0f, 1f),
            ((at.y - frame.top) / frame.height).coerceIn(0f, 1f),
        )
        EditedImage(
            image = image,
            crop = crop,
            degrees = degrees,
            filter = filter,
            vignette = vignette,
            markup = live,
            modifier = Modifier
                .matchParentSize()
                .pointerInput(frame) {
                    detectTapGestures { at -> if (currentTool == MarkTool.Text) textAt = toPhoto(at) }
                }
                .pointerInput(frame) {
                    detectDragGestures(
                        onDragStart = { at ->
                            drawing.clear()
                            if (currentTool != MarkTool.Text) toPhoto(at).let { drawing += it.x; drawing += it.y }
                        },
                        onDrag = { change, _ ->
                            if (currentTool != MarkTool.Text) toPhoto(change.position).let { drawing += it.x; drawing += it.y }
                        },
                        onDragEnd = {
                            if (drawing.size >= 2) {
                                add(Mark.Stroke(drawing.toList(), currentColor.toArgb(), widthFor(currentTool), currentTool == MarkTool.Highlighter))
                            }
                            drawing.clear()
                        },
                        onDragCancel = { drawing.clear() },
                    )
                },
        )
    }
    textAt?.let { at ->
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { textAt = null },
            title = { Text(stringResource(R.string.markup_text)) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) add(Mark.Label(at.x, at.y, text.trim(), currentColor.toArgb(), TEXT_SIZE))
                    textAt = null
                }) { Text(stringResource(R.string.done)) }
            },
            dismissButton = { TextButton(onClick = { textAt = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun widthFor(tool: MarkTool) = if (tool == MarkTool.Highlighter) HIGHLIGHTER_WIDTH else PEN_WIDTH

/** Pen, highlighter and text, the colours, and undoing the last mark or clearing them all. */
@Composable
fun MarkupTools(
    tool: MarkTool,
    color: Color,
    hasMarks: Boolean,
    onTool: (MarkTool) -> Unit,
    onColor: (Color) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for ((entry, label, icon) in listOf(
                Triple(MarkTool.Pen, R.string.markup_pen, Icons.Outlined.Draw),
                Triple(MarkTool.Highlighter, R.string.markup_highlighter, Icons.Outlined.BorderColor),
                Triple(MarkTool.Text, R.string.markup_text, Icons.Outlined.TextFields),
            )) {
                FilterChip(
                    selected = tool == entry,
                    onClick = { onTool(entry) },
                    label = { Text(stringResource(label)) },
                    leadingIcon = { Icon(icon, contentDescription = null, Modifier.size(18.dp)) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            for (swatch in MARK_COLORS) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(swatch, CircleShape)
                        .border(if (swatch == color) 3.dp else 1.dp, if (swatch == color) Color.White else Color.Gray, CircleShape)
                        .clickable { onColor(swatch) },
                )
            }
            IconButton(onClick = onUndo, enabled = hasMarks) {
                Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.undo), tint = if (hasMarks) Color.White else Color.Gray)
            }
            IconButton(onClick = onClear, enabled = hasMarks) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.markup_clear), tint = if (hasMarks) Color.White else Color.Gray)
            }
        }
    }
}
