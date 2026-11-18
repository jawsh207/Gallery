package app.roll.gallery.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The editor's changes that work pixel by pixel rather than through a colour matrix: lifting or
 * lowering [Adjustments.highlights] and [Adjustments.shadows] (each weighted towards the light
 * or dark parts, so the other end is left alone), then [Adjustments.sharpen] (each pixel pushed
 * away from the average of its four neighbours). Returns [bitmap] itself when none are set.
 * [isActive] lets a preview give up early when the sliders have moved on.
 */
fun applyTones(bitmap: Bitmap, a: Adjustments, isActive: () -> Boolean = { true }): Bitmap {
    if (a.highlights == 0f && a.shadows == 0f && a.sharpen == 0f) return bitmap
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    if (a.highlights != 0f || a.shadows != 0f) {
        for (i in pixels.indices) {
            if (i % w == 0 && !isActive()) return bitmap
            val p = pixels[i]
            pixels[i] = (p and 0xFF000000.toInt()) or tone(p, a.highlights, a.shadows)
        }
    }
    if (a.sharpen > 0f) sharpen(pixels, w, h, a.sharpen * 1.5f)
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

/** One pixel's red, green and blue after [highlights] and [shadows] (see [applyTones]). */
internal fun tone(pixel: Int, highlights: Float, shadows: Float): Int {
    val r = (pixel shr 16) and 0xFF
    val g = (pixel shr 8) and 0xFF
    val b = pixel and 0xFF
    val light = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    val forShadows = shadows * (1 - light) * (1 - light) * TONE_STRENGTH
    val forHighlights = highlights * light * light * TONE_STRENGTH
    fun shift(c: Int): Int {
        var v = c.toFloat()
        v += if (forShadows > 0) forShadows * (255 - v) else forShadows * v
        v += if (forHighlights > 0) forHighlights * (255 - v) else forHighlights * v
        return v.roundToInt().coerceIn(0, 255)
    }
    return (shift(r) shl 16) or (shift(g) shl 8) or shift(b)
}

/** How far highlights and shadows can move a pixel towards white or black. */
private const val TONE_STRENGTH = 0.6f

/** Sharpens [pixels] ([w] × [h]) in place by [amount], leaving the outermost pixels as they are. */
private fun sharpen(pixels: IntArray, w: Int, h: Int, amount: Float) {
    val source = pixels.copyOf()
    for (y in 1 until h - 1) {
        for (x in 1 until w - 1) {
            val i = y * w + x
            val p = source[i]
            var out = p and 0xFF000000.toInt()
            for (shift in intArrayOf(16, 8, 0)) {
                val c = (p shr shift) and 0xFF
                val around = (((source[i - 1] shr shift) and 0xFF) + ((source[i + 1] shr shift) and 0xFF) +
                    ((source[i - w] shr shift) and 0xFF) + ((source[i + w] shr shift) and 0xFF)) / 4f
                out = out or ((c + amount * (c - around)).roundToInt().coerceIn(0, 255) shl shift)
            }
            pixels[i] = out
        }
    }
}

/** Darkens [bitmap]'s corners by [amount] (0 to 1), drawing onto it. */
fun drawVignette(canvas: Canvas, width: Int, height: Int, amount: Float) {
    if (amount <= 0f) return
    val radius = hypot(width.toFloat(), height.toFloat()) / 2
    val paint = Paint().apply {
        shader = RadialGradient(
            width / 2f, height / 2f, radius,
            intArrayOf(0, 0, android.graphics.Color.argb((amount * 220).roundToInt(), 0, 0, 0)),
            floatArrayOf(0f, VIGNETTE_START, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
}

/** How far from the middle (as a share of the way to the corner) the vignette starts. */
const val VIGNETTE_START = 0.45f

/** Draws [marks] onto a [width] × [height] canvas (see [Mark]). */
fun drawMarkup(canvas: Canvas, marks: List<Mark>, width: Int, height: Int) {
    val short = minOf(width, height).toFloat()
    for (mark in marks) {
        when (mark) {
            is Mark.Stroke -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = mark.color
                    if (mark.highlighter) alpha = HIGHLIGHTER_ALPHA
                    style = Paint.Style.STROKE
                    strokeWidth = mark.width * short
                    strokeCap = if (mark.highlighter) Paint.Cap.SQUARE else Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val path = android.graphics.Path()
                mark.points.chunked(2).forEachIndexed { i, (x, y) ->
                    if (i == 0) path.moveTo(x * width, y * height) else path.lineTo(x * width, y * height)
                }
                if (mark.points.size == 2) path.lineTo(mark.points[0] * width + 0.1f, mark.points[1] * height)
                canvas.drawPath(path, paint)
            }
            is Mark.Label -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = mark.color
                    textSize = mark.size * short
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setShadowLayer(textSize / 12, 0f, 0f, android.graphics.Color.argb(160, 0, 0, 0))
                }
                canvas.drawText(mark.text, mark.x * width, mark.y * height - paint.ascent(), paint)
            }
        }
    }
}

/** How see-through the highlighter is, 0 to 255. */
const val HIGHLIGHTER_ALPHA = 110

/**
 * What one-tap Enhance sets, from a photo's brightness [histogram] (256 counts, dark to light)
 * and its average colourfulness [meanSaturation] (0 to 1): spread the tones to fill the range
 * (more contrast for flat photos), lift a dark photo or calm a bright one, open up shadows and
 * hold back highlights a little, and add colour to dull photos. Gentle, so it rarely overdoes
 * a photo that was fine.
 */
fun autoEnhance(histogram: IntArray, meanSaturation: Float): Adjustments {
    val total = histogram.sum().coerceAtLeast(1)
    fun percentile(p: Float): Int {
        var seen = 0
        for (i in histogram.indices) {
            seen += histogram[i]
            if (seen >= total * p) return i
        }
        return 255
    }
    val low = percentile(0.02f)
    val high = percentile(0.98f)
    val mean = histogram.indices.sumOf { it.toLong() * histogram[it] }.toFloat() / total / 255f
    val spread = (high - low) / 255f
    return Adjustments(
        brightness = ((0.5f - mean) * 0.5f).coerceIn(-0.2f, 0.25f),
        contrast = ((0.85f - spread) * 0.6f).coerceIn(0f, 0.3f),
        saturation = ((0.35f - meanSaturation) * 0.8f).coerceIn(0f, 0.25f),
        shadows = if (low > 30 || mean < 0.4f) 0.25f else 0.15f,
        highlights = if (high > 245) -0.2f else -0.05f,
    )
}

/** [bitmap]'s brightness histogram and average colourfulness, for [autoEnhance]. */
fun measure(bitmap: Bitmap): Pair<IntArray, Float> {
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    val histogram = IntArray(256)
    var saturation = 0.0
    for (p in pixels) {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        histogram[((0.299f * r + 0.587f * g + 0.114f * b)).roundToInt().coerceIn(0, 255)]++
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        if (max > 0) saturation += (max - min).toDouble() / max
    }
    return histogram to (saturation / pixels.size.coerceAtLeast(1)).toFloat()
}
