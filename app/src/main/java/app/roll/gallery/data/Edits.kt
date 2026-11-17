package app.roll.gallery.data

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * A crop as fractions of the (turned and flipped) photo: 0 is the left or top edge, 1 the right
 * or bottom. Fractions rather than pixels, so the same crop fits the small preview and the
 * full-size photo.
 */
data class CropRect(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    /** True when nothing is cropped. */
    val isFull: Boolean get() = left <= 0f && top <= 0f && right >= 1f && bottom >= 1f
}

/** The colour adjustments, each from -1 (less) through 0 (unchanged) to 1 (more). */
data class Adjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
)

/** The editor's looks: each is a fixed set of colour changes applied before the adjustments. */
enum class Filter { None, Vivid, Warm, Cool, Fade, Mono, Noir, Sepia }

/** Crop shapes the editor offers; [ratio] is width ÷ height, null for free or the photo's own. */
enum class CropAspect(val ratio: Float?) {
    Free(null), Original(null), Square(1f), FourThree(4f / 3f), ThreeFour(3f / 4f), SixteenNine(16f / 9f),
}

/**
 * Everything done to a photo in the editor, applied in this order: turned [quarterTurns] times
 * clockwise, mirrored left to right when [flipped], tilted by [straighten] degrees (clockwise,
 * zoomed just enough to leave no empty corners, see [straightenScale]), cropped to [crop], then
 * recoloured by [filter] and [adjustments]. Nothing touches the photo until it's saved.
 */
data class Edits(
    val quarterTurns: Int = 0,
    val flipped: Boolean = false,
    val straighten: Float = 0f,
    val crop: CropRect = CropRect(),
    val aspect: CropAspect = CropAspect.Free,
    val filter: Filter = Filter.None,
    val adjustments: Adjustments = Adjustments(),
) {
    /** True when saving would change nothing. */
    val isUnchanged: Boolean
        get() = quarterTurns % 4 == 0 && !flipped && straighten == 0f && crop.isFull && filter == Filter.None && adjustments == Adjustments()
}

/** How far the straighten slider goes either way, in degrees. */
const val MAX_STRAIGHTEN = 45f

/**
 * How much a [width] × [height] photo tilted by [degrees] must be enlarged so that it still
 * covers its original frame, leaving no empty corners: the frame, tilted back the other way,
 * has to fit inside the enlarged photo. 1 when it isn't tilted.
 */
fun straightenScale(width: Float, height: Float, degrees: Float): Float {
    if (degrees == 0f || width <= 0f || height <= 0f) return 1f
    val radians = Math.toRadians(degrees.toDouble())
    val c = abs(cos(radians)).toFloat()
    val s = abs(sin(radians)).toFloat()
    return max((width * c + height * s) / width, (width * s + height * c) / height)
}

/** The 4 × 5 colour matrix (row by row, offsets in 0–255) that changes nothing. */
val IDENTITY_MATRIX: FloatArray = floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

/** The colour matrix that applies [second] after [first]. */
fun concat(first: FloatArray, second: FloatArray): FloatArray {
    val out = FloatArray(20)
    for (row in 0 until 4) {
        for (col in 0 until 5) {
            var sum = if (col == 4) second[row * 5 + 4] else 0f
            for (k in 0 until 4) sum += second[row * 5 + k] * first[k * 5 + col]
            out[row * 5 + col] = sum
        }
    }
    return out
}

/** Brightness: shifts every channel by up to a quarter of the range. */
fun brightnessMatrix(amount: Float): FloatArray = IDENTITY_MATRIX.copyOf().also { m ->
    for (row in 0 until 3) m[row * 5 + 4] = amount * 64f
}

/** Contrast: stretches (or squeezes) every channel around mid-grey. */
fun contrastMatrix(amount: Float): FloatArray {
    val scale = 1f + amount
    val offset = 128f * (1f - scale)
    return floatArrayOf(
        scale, 0f, 0f, 0f, offset,
        0f, scale, 0f, 0f, offset,
        0f, 0f, scale, 0f, offset,
        0f, 0f, 0f, 1f, 0f,
    )
}

/** Saturation: -1 is grey, 0 unchanged, 1 twice as colourful. Greys use the standard luminance weights. */
fun saturationMatrix(amount: Float): FloatArray {
    val s = 1f + amount
    val r = 0.2126f * (1 - s)
    val g = 0.7152f * (1 - s)
    val b = 0.0722f * (1 - s)
    return floatArrayOf(
        r + s, g, b, 0f, 0f,
        r, g + s, b, 0f, 0f,
        r, g, b + s, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

/** Warmth: more red and less blue (or the reverse, below 0). */
fun warmthMatrix(amount: Float): FloatArray = IDENTITY_MATRIX.copyOf().also { m ->
    m[4] = amount * 24f
    m[14] = -amount * 24f
}

/** The classic brown-toned sepia. */
val SEPIA_MATRIX: FloatArray = floatArrayOf(
    0.393f, 0.769f, 0.189f, 0f, 0f,
    0.349f, 0.686f, 0.168f, 0f, 0f,
    0.272f, 0.534f, 0.131f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

/** The colour changes [filter] stands for. */
fun filterMatrix(filter: Filter): FloatArray = when (filter) {
    Filter.None -> IDENTITY_MATRIX
    Filter.Vivid -> concat(saturationMatrix(0.35f), contrastMatrix(0.1f))
    Filter.Warm -> concat(warmthMatrix(0.6f), saturationMatrix(0.1f))
    Filter.Cool -> warmthMatrix(-0.6f)
    Filter.Fade -> concat(contrastMatrix(-0.3f), brightnessMatrix(0.12f))
    Filter.Mono -> saturationMatrix(-1f)
    Filter.Noir -> concat(saturationMatrix(-1f), contrastMatrix(0.45f))
    Filter.Sepia -> SEPIA_MATRIX
}

/** The full colour matrix for [edits]: the filter first, then each adjustment. */
fun colorMatrix(edits: Edits): FloatArray {
    val a = edits.adjustments
    var m = filterMatrix(edits.filter)
    if (a.brightness != 0f) m = concat(m, brightnessMatrix(a.brightness))
    if (a.contrast != 0f) m = concat(m, contrastMatrix(a.contrast))
    if (a.saturation != 0f) m = concat(m, saturationMatrix(a.saturation))
    if (a.warmth != 0f) m = concat(m, warmthMatrix(a.warmth))
    return m
}

/**
 * The biggest centred crop of [ratio] (width ÷ height) in a photo [width] × [height] pixels;
 * the whole photo when [ratio] is null.
 */
fun fitAspect(ratio: Float?, width: Int, height: Int): CropRect {
    if (ratio == null || width <= 0 || height <= 0) return CropRect()
    val photo = width.toFloat() / height
    return if (ratio > photo) {
        val h = photo / ratio
        CropRect(0f, (1 - h) / 2, 1f, (1 + h) / 2)
    } else {
        val w = ratio / photo
        CropRect((1 - w) / 2, 0f, (1 + w) / 2, 1f)
    }
}

/** The part of the crop frame being dragged. */
enum class CropHandle { TopLeft, TopRight, BottomLeft, BottomRight, Move }

/**
 * The crop after dragging [handle] by ([dx], [dy]) pixels, on a photo shown [width] × [height]
 * pixels. Corners resize (keeping [ratio], width ÷ height, when given) with the opposite corner
 * fixed; [CropHandle.Move] slides the whole frame. The frame stays inside the photo and at
 * least [minSize] pixels across.
 */
fun dragCrop(crop: CropRect, handle: CropHandle, dx: Float, dy: Float, ratio: Float?, width: Float, height: Float, minSize: Float): CropRect {
    var l = crop.left * width
    var t = crop.top * height
    var r = crop.right * width
    var b = crop.bottom * height
    if (handle == CropHandle.Move) {
        val w = r - l
        val h = b - t
        l = (l + dx).coerceIn(0f, width - w)
        t = (t + dy).coerceIn(0f, height - h)
        return CropRect(l / width, t / height, (l + w) / width, (t + h) / height)
    }
    val movesLeft = handle == CropHandle.TopLeft || handle == CropHandle.BottomLeft
    val movesTop = handle == CropHandle.TopLeft || handle == CropHandle.TopRight
    // The corner that stays put.
    val anchorX = if (movesLeft) r else l
    val anchorY = if (movesTop) b else t
    val maxW = if (movesLeft) anchorX else width - anchorX
    val maxH = if (movesTop) anchorY else height - anchorY
    var w = ((if (movesLeft) r - (l + dx) else (r + dx) - l)).coerceIn(minSize, maxW.coerceAtLeast(minSize))
    var h = ((if (movesTop) b - (t + dy) else (b + dy) - t)).coerceIn(minSize, maxH.coerceAtLeast(minSize))
    if (ratio != null) {
        // Follow whichever side moved more, then shrink both to fit.
        if (w / ratio > h) h = w / ratio else w = h * ratio
        if (h > maxH) { h = maxH; w = h * ratio }
        if (w > maxW) { w = maxW; h = w / ratio }
    }
    l = if (movesLeft) anchorX - w else anchorX
    r = l + w
    t = if (movesTop) anchorY - h else anchorY
    b = t + h
    return CropRect(l / width, t / height, r / width, b / height)
}
