package app.roll.gallery.data

/**
 * The things the Create tab makes, with how many photos each needs ([least] to [most]) and
 * whether videos can be used too ([takesVideos]). Each is made on the phone and saved as a new
 * file; the photos used are left as they are.
 */
enum class Creation(val least: Int, val most: Int, val takesVideos: Boolean) {
    /** Several photos in one picture, laid out in a grid of cells. */
    Collage(2, 9, false),
    /** Photos shown one after another, quickly and on a loop, saved as a short video. */
    Animation(2, 60, false),
    /** A slideshow video of photos and clips, with optional music. */
    VideoMaker(1, 100, true),
    /** One photo opened in the editor on its looks (moods). */
    Moods(1, 1, false),
}

/** One cell of a collage, as fractions of the whole picture: 0 is the left or top edge, 1 the right or bottom. */
data class Cell(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** The shape of a collage: [ratio] is width ÷ height. */
enum class CollageShape(val ratio: Float) { Square(1f), Portrait(4f / 5f), Story(9f / 16f), Landscape(4f / 3f) }

/**
 * The layouts offered for a collage of [count] photos (2 to 9), each a list of [count] cells
 * that together fill the picture without overlapping. The first is the plainest: rows of
 * equal cells, as even as the count allows.
 */
fun collageLayouts(count: Int): List<List<Cell>> {
    require(count in 2..9) { "a collage takes 2 to 9 photos" }
    val layouts = ArrayList<List<Cell>>()
    layouts += rows(evenSplit(count, rowsFor(count)))
    layouts += columns(evenSplit(count, rowsFor(count)))
    when (count) {
        2 -> Unit // rows and columns are the only ways to split two
        3 -> {
            layouts += bigAndRest(count, bigOnLeft = true)
            layouts += bigAndRest(count, bigOnLeft = false)
            layouts += rows(listOf(1, 2))
        }
        else -> {
            layouts += bigAndRest(count, bigOnLeft = true)
            layouts += rows(listOf(1) + evenSplit(count - 1, rowsFor(count - 1)))
            layouts += rows(evenSplit(count - 1, rowsFor(count - 1)) + listOf(1))
        }
    }
    return layouts.distinct()
}

/** How many rows suit [count] equal cells. */
private fun rowsFor(count: Int): Int = when {
    count <= 2 -> 1
    count <= 6 -> 2
    else -> 3
}

/** [count] split into [parts] whole numbers as equal as possible, larger ones last. */
internal fun evenSplit(count: Int, parts: Int): List<Int> =
    List(parts) { i -> count / parts + if (i >= parts - count % parts) 1 else 0 }

/** Rows of equal height, each split into its number of equal cells. */
private fun rows(perRow: List<Int>): List<Cell> {
    val height = 1f / perRow.size
    return perRow.flatMapIndexed { row, n ->
        List(n) { i -> Cell(i.toFloat() / n, row * height, (i + 1).toFloat() / n, (row + 1) * height) }
    }
}

/** Columns of equal width, each split into its number of equal cells. */
private fun columns(perColumn: List<Int>): List<Cell> =
    rows(perColumn).map { Cell(it.top, it.left, it.bottom, it.right) }

/** One big cell taking two thirds of the width, the rest stacked beside it. */
private fun bigAndRest(count: Int, bigOnLeft: Boolean): List<Cell> {
    val split = 2f / 3f
    val rest = count - 1
    val stacked = List(rest) { i ->
        val top = i.toFloat() / rest
        val bottom = (i + 1).toFloat() / rest
        if (bigOnLeft) Cell(split, top, 1f, bottom) else Cell(0f, top, 1f - split, bottom)
    }
    val big = if (bigOnLeft) Cell(0f, 0f, split, 1f) else Cell(1f - split, 0f, 1f, 1f)
    return listOf(big) + stacked
}

/**
 * Where an image of [imageWidth] × [imageHeight] is cut so it fills a [cellWidth] × [cellHeight]
 * cell without stretching: the largest centred piece with the cell's shape, as
 * (left, top, right, bottom) in the image's pixels.
 */
fun centreCrop(imageWidth: Int, imageHeight: Int, cellWidth: Float, cellHeight: Float): FloatArray {
    val cellRatio = cellWidth / cellHeight
    val imageRatio = imageWidth.toFloat() / imageHeight
    return if (imageRatio > cellRatio) {
        val w = imageHeight * cellRatio
        val left = (imageWidth - w) / 2f
        floatArrayOf(left, 0f, left + w, imageHeight.toFloat())
    } else {
        val h = imageWidth / cellRatio
        val top = (imageHeight - h) / 2f
        floatArrayOf(0f, top, imageWidth.toFloat(), top + h)
    }
}

/**
 * How long each photo shows in an animation at [speed] (0 slowest to 1 fastest), in
 * milliseconds: from half a second down to a tenth.
 */
fun animationFrameMs(speed: Float): Long = (500 - 400 * speed.coerceIn(0f, 1f)).toLong()

/**
 * The order an animation plays [count] photos in, by index: straight through, or there and
 * back again ([backAndForth], without showing the ends twice) so the loop has no jump.
 */
fun animationOrder(count: Int, backAndForth: Boolean): List<Int> {
    val forward = List(count) { it }
    return if (backAndForth && count > 2) forward + forward.subList(1, count - 1).reversed() else forward
}

/**
 * Where [cell] sits in a [width] × [height] picture, in pixels (left, top, right, bottom), with
 * [gap] pixels between neighbouring cells and around the edge.
 */
fun cellRect(cell: Cell, width: Float, height: Float, gap: Float): FloatArray {
    val innerW = width - gap
    val innerH = height - gap
    return floatArrayOf(
        gap / 2 + cell.left * innerW + gap / 2,
        gap / 2 + cell.top * innerH + gap / 2,
        gap / 2 + cell.right * innerW - gap / 2,
        gap / 2 + cell.bottom * innerH - gap / 2,
    )
}

/** A name for something made on the Create tab, like "Collage_20261117_194512.jpg", made at [millis]. */
fun creationName(prefix: String, extension: String, millis: Long, zone: java.time.ZoneId): String {
    val time = java.time.Instant.ofEpochMilli(millis).atZone(zone)
    return "%s_%04d%02d%02d_%02d%02d%02d.%s".format(
        prefix, time.year, time.monthValue, time.dayOfMonth, time.hour, time.minute, time.second, extension,
    )
}
