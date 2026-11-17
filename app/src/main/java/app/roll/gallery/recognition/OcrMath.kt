package app.roll.gallery.recognition

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The parts of reading text that are plain arithmetic: sizing images for the models, turning
 * the text-finding model's map into boxes, and turning the reading model's output into
 * characters. Kept apart from the model code so it can be tested without a phone.
 *
 * The models are PaddleOCR's: a "DB" text finder, which colours each pixel by how likely it is
 * to be text, and a reader that, for each slice of a line of text, scores every character it
 * knows (the "CTC" scheme, where slot 0 means "no character here").
 */

/** A box around some text, in pixels of whatever image it was found in. */
data class TextBox(val left: Float, val top: Float, val right: Float, val bottom: Float, val score: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * The size to give the text finder for a [width] × [height] photo: scaled so the longer side
 * is at most [maxSide], with both sides rounded to a multiple of 32 (the model needs that) and
 * at least 32.
 */
fun finderInputSize(width: Int, height: Int, maxSide: Int = 960): Pair<Int, Int> {
    val scale = min(1f, maxSide.toFloat() / max(width, height))
    fun round32(v: Float) = max(32, (v / 32f).roundToInt() * 32)
    return round32(width * scale) to round32(height * scale)
}

/**
 * The boxes of text in the finder's output: [probability] is a [width] × [height] map (row by
 * row) of how likely each pixel is to be text. Pixels above [threshold] are joined into
 * regions; a region becomes a box if its average probability is at least [boxThreshold] and
 * it's at least [minSide] pixels high and wide. The model draws text slightly smaller than it
 * is, so each box is grown by [unclipRatio] × its area ÷ its perimeter on every side, as
 * PaddleOCR does. Boxes come back in reading order: top to bottom, then left to right along a line.
 */
fun findTextBoxes(
    probability: FloatArray,
    width: Int,
    height: Int,
    threshold: Float = 0.3f,
    boxThreshold: Float = 0.6f,
    unclipRatio: Float = 1.6f,
    minSide: Int = 3,
): List<TextBox> {
    require(probability.size >= width * height)
    val seen = BooleanArray(width * height)
    val stack = IntArray(width * height)
    val boxes = ArrayList<TextBox>()
    for (start in 0 until width * height) {
        if (seen[start] || probability[start] <= threshold) continue
        // Flood-fill one region (4-connected), tracking its bounds and total probability.
        var top = 0
        stack[top++] = start
        seen[start] = true
        var minX = width; var minY = height; var maxX = -1; var maxY = -1
        var sum = 0.0
        var count = 0
        while (top > 0) {
            val p = stack[--top]
            val x = p % width
            val y = p / width
            sum += probability[p]
            count++
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            if (x > 0) push(p - 1, probability, threshold, seen, stack, top).also { top = it }
            if (x < width - 1) push(p + 1, probability, threshold, seen, stack, top).also { top = it }
            if (y > 0) push(p - width, probability, threshold, seen, stack, top).also { top = it }
            if (y < height - 1) push(p + width, probability, threshold, seen, stack, top).also { top = it }
        }
        val w = (maxX - minX + 1).toFloat()
        val h = (maxY - minY + 1).toFloat()
        val score = (sum / count).toFloat()
        if (w < minSide || h < minSide || score < boxThreshold) continue
        val grow = unclipRatio * (w * h) / (2 * (w + h))
        boxes += TextBox(
            left = max(0f, minX - grow),
            top = max(0f, minY - grow),
            right = min(width.toFloat(), maxX + 1 + grow),
            bottom = min(height.toFloat(), maxY + 1 + grow),
            score = score,
        )
    }
    return readingOrder(boxes)
}

/** Adds pixel [p] to the fill if it's text and not yet visited; returns the new stack size. */
private fun push(p: Int, probability: FloatArray, threshold: Float, seen: BooleanArray, stack: IntArray, top: Int): Int {
    if (seen[p] || probability[p] <= threshold) return top
    seen[p] = true
    stack[top] = p
    return top + 1
}

/**
 * [boxes] sorted the way a person reads: lines from top to bottom (boxes whose middles are
 * within half a box height of each other share a line), and left to right within a line.
 */
fun readingOrder(boxes: List<TextBox>): List<TextBox> {
    val byTop = boxes.sortedBy { it.top }
    val lines = ArrayList<MutableList<TextBox>>()
    for (box in byTop) {
        val middle = (box.top + box.bottom) / 2
        val line = lines.lastOrNull()?.takeIf { l ->
            val ref = l.first()
            kotlin.math.abs(middle - (ref.top + ref.bottom) / 2) <= ref.height / 2
        }
        if (line != null) line += box else lines += mutableListOf(box)
    }
    return lines.flatMap { line -> line.sortedBy { it.left } }
}

/** The reader's input width for a [width] × [height] crop scaled to [targetHeight] pixels high: at least 8, at most [maxWidth]. */
fun readerInputWidth(width: Int, height: Int, targetHeight: Int = 48, maxWidth: Int = 1280): Int =
    ceil(targetHeight * width.toFloat() / max(1, height)).toInt().coerceIn(8, maxWidth)

/**
 * The text the reader saw, from its output: [scores] holds, for each of [steps] slices, one
 * score per character slot ([classes] slots). Slot 0 is "no character"; slot i is
 * [characters][i − 1]. The best slot is taken for each slice, repeats are merged (a letter
 * spans several slices) unless a "no character" slice comes between, and the blanks dropped.
 * Also returns the average score of the kept characters, as a measure of confidence.
 */
fun decodeCharacters(scores: FloatArray, steps: Int, classes: Int, characters: List<String>): Pair<String, Float> {
    val text = StringBuilder()
    var total = 0f
    var kept = 0
    var previous = -1
    for (t in 0 until steps) {
        var best = 0
        var bestScore = Float.NEGATIVE_INFINITY
        for (c in 0 until classes) {
            val s = scores[t * classes + c]
            if (s > bestScore) {
                bestScore = s
                best = c
            }
        }
        if (best != 0 && best != previous) {
            characters.getOrNull(best - 1)?.let {
                text.append(it)
                total += bestScore
                kept++
            }
        }
        previous = best
    }
    return text.toString() to (if (kept == 0) 0f else total / kept)
}

/**
 * The reader's characters from its dictionary file (one per line), with the space added at the
 * end as PaddleOCR's models expect.
 */
fun readerCharacters(dictionary: String): List<String> =
    dictionary.split('\n').map { it.trimEnd('\r') }.filter { it.isNotEmpty() } + " "
