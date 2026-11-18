package app.roll.gallery.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.roll.gallery.data.CropHandle
import app.roll.gallery.data.CropRect
import app.roll.gallery.data.dragCrop
import app.roll.gallery.data.straightenScale
import app.roll.gallery.data.Mark
import app.roll.gallery.data.drawMarkup
import app.roll.gallery.data.drawVignette
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** Where [imageSize] lands when fitted, centred, into [area]. */
private fun fit(imageSize: Size, area: Size): Rect {
    val scale = min(area.width / imageSize.width, area.height / imageSize.height)
    val w = imageSize.width * scale
    val h = imageSize.height * scale
    return Rect(Offset((area.width - w) / 2, (area.height - h) / 2), Size(w, h))
}

/**
 * Draws [image] tilted by [degrees] (and enlarged by [straightenScale] so it fills its frame)
 * as if its whole frame were [frame] on screen, showing only the part inside [visible]. The
 * tilt is done while drawing, so dragging the straighten slider stays smooth.
 */
private fun DrawScope.drawStraightened(image: ImageBitmap, frame: Rect, visible: Rect, degrees: Float, filter: ColorFilter?) {
    val zoom = straightenScale(image.width.toFloat(), image.height.toFloat(), degrees)
    clipRect(visible.left, visible.top, visible.right, visible.bottom) {
        rotate(degrees, frame.center) {
            scale(zoom, frame.center) {
                drawImage(
                    image,
                    dstOffset = IntOffset(frame.left.roundToInt(), frame.top.roundToInt()),
                    dstSize = IntSize(frame.width.roundToInt(), frame.height.roundToInt()),
                    colorFilter = filter,
                    filterQuality = FilterQuality.High,
                )
            }
        }
    }
}

/** Where the [crop]ped part of [image] lands when fitted into [area]: the finished photo's frame on screen. */
fun editedFrame(image: ImageBitmap, crop: CropRect, area: Size): Rect {
    val cropW = ((crop.right - crop.left) * image.width).coerceAtLeast(1f)
    val cropH = ((crop.bottom - crop.top) * image.height).coerceAtLeast(1f)
    return fit(Size(cropW, cropH), area)
}

/**
 * The photo as it will be saved: tilted by [degrees], only the [crop]ped part, fitted to the
 * space, with [filter] applied (null shows the original colours, while the user holds to
 * compare), then the [vignette] and [markup] on top, as saving draws them.
 */
@Composable
fun EditedImage(
    image: ImageBitmap,
    crop: CropRect,
    degrees: Float,
    filter: ColorFilter?,
    modifier: Modifier = Modifier,
    vignette: Float = 0f,
    markup: List<Mark> = emptyList(),
) {
    Canvas(modifier) {
        val cropW = ((crop.right - crop.left) * image.width).coerceAtLeast(1f)
        val cropH = ((crop.bottom - crop.top) * image.height).coerceAtLeast(1f)
        val dst = editedFrame(image, crop, size)
        // Where the whole (uncropped) frame would be, at the same scale, for the crop to land on dst.
        val k = dst.width / cropW
        val frame = Rect(
            Offset(dst.left - crop.left * image.width * k, dst.top - crop.top * image.height * k),
            Size(image.width * k, image.height * k),
        )
        drawStraightened(image, frame, dst, degrees, filter)
        // Holding to compare (no filter) shows the photo as it was, without these either.
        if (filter != null) {
            translate(dst.left, dst.top) {
                drawIntoCanvas { canvas ->
                    // The same drawing code as saving, so what's shown is what's saved.
                    drawVignette(canvas.nativeCanvas, dst.width.roundToInt(), dst.height.roundToInt(), vignette)
                    drawMarkup(canvas.nativeCanvas, markup, dst.width.roundToInt(), dst.height.roundToInt())
                }
            }
        }
    }
}

/**
 * The crop frame over the whole (turned and tilted by [degrees]) photo: the part outside is dimmed, with thirds lines
 * inside. Drag a corner to resize (keeping [ratio], width ÷ height, when set) or the middle to
 * move; [onChange] gets each new crop.
 */
@Composable
fun CropView(
    image: ImageBitmap,
    crop: CropRect,
    ratio: Float?,
    degrees: Float,
    filter: ColorFilter?,
    onChange: (CropRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(crop)
    val currentRatio by rememberUpdatedState(ratio)
    val change by rememberUpdatedState(onChange)
    val density = LocalDensity.current
    val touch = with(density) { 36.dp.toPx() }
    val minSize = with(density) { 48.dp.toPx() }
    val imageSize = remember(image) { Size(image.width.toFloat(), image.height.toFloat()) }

    Canvas(
        modifier.pointerInput(image) {
            var handle: CropHandle? = null
            detectDragGestures(
                onDragStart = { start ->
                    val box = fit(imageSize, Size(size.width.toFloat(), size.height.toFloat()))
                    val c = current
                    val l = box.left + c.left * box.width
                    val t = box.top + c.top * box.height
                    val r = box.left + c.right * box.width
                    val b = box.top + c.bottom * box.height
                    val corners = listOf(
                        CropHandle.TopLeft to Offset(l, t), CropHandle.TopRight to Offset(r, t),
                        CropHandle.BottomLeft to Offset(l, b), CropHandle.BottomRight to Offset(r, b),
                    )
                    handle = corners.minByOrNull { (_, p) -> hypot(p.x - start.x, p.y - start.y) }
                        ?.takeIf { (_, p) -> hypot(p.x - start.x, p.y - start.y) <= touch }?.first
                        ?: if (start.x in l..r && start.y in t..b) CropHandle.Move else null
                },
                onDragEnd = { handle = null },
                onDragCancel = { handle = null },
                onDrag = { pointer, amount ->
                    val h = handle
                    if (h != null) {
                        pointer.consume()
                        val box = fit(imageSize, Size(size.width.toFloat(), size.height.toFloat()))
                        change(dragCrop(current, h, amount.x, amount.y, currentRatio, box.width, box.height, minSize))
                    }
                },
            )
        },
    ) {
        val box = fit(imageSize, size)
        drawStraightened(image, box, box, degrees, filter)
        val frame = Rect(
            box.left + crop.left * box.width, box.top + crop.top * box.height,
            box.left + crop.right * box.width, box.top + crop.bottom * box.height,
        )
        // Dim everything outside the frame (even-odd: the frame is a hole in the box).
        val shade = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(box)
            addRect(frame)
        }
        drawPath(shade, Color.Black.copy(alpha = 0.6f))
        val line = 1.dp.toPx()
        for (i in 1..2) {
            val x = frame.left + frame.width * i / 3
            val y = frame.top + frame.height * i / 3
            drawLine(Color.White.copy(alpha = 0.5f), Offset(x, frame.top), Offset(x, frame.bottom), line)
            drawLine(Color.White.copy(alpha = 0.5f), Offset(frame.left, y), Offset(frame.right, y), line)
        }
        drawRect(Color.White, frame.topLeft, frame.size, style = Stroke(2.dp.toPx()))
        val arm = 18.dp.toPx()
        val thick = 4.dp.toPx()
        for ((corner, dir) in listOf(
            frame.topLeft to Offset(1f, 1f), Offset(frame.right, frame.top) to Offset(-1f, 1f),
            Offset(frame.left, frame.bottom) to Offset(1f, -1f), frame.bottomRight to Offset(-1f, -1f),
        )) {
            drawLine(Color.White, corner, corner + Offset(arm * dir.x, 0f), thick)
            drawLine(Color.White, corner, corner + Offset(0f, arm * dir.y), thick)
        }
    }
}
