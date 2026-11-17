package app.roll.gallery.data

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** A video's length as "0:05", "12:30" or "1:02:03". */
fun formatDuration(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 500) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
}

/** A file size as "820 KB", "2.4 MB" or "1.1 GB" (1 KB = 1000 bytes, as Android's Files app shows). */
fun formatBytes(bytes: Long): String {
    if (bytes < 1000) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1000.0
    var unit = 0
    while (value >= 999.95 && unit < units.lastIndex) {
        value /= 1000.0
        unit++
    }
    return if (value >= 100 || unit == 0) "${value.roundToLong()} ${units[unit]}"
    else "%.1f %s".format(Locale.ROOT, value, units[unit])
}

/** A photo's resolution in megapixels, as "12 MP" or "0.3 MP"; null if the size is unknown. */
fun megapixels(width: Int, height: Int): String? {
    if (width <= 0 || height <= 0) return null
    val mp = width.toLong() * height / 1_000_000.0
    return if (mp >= 1) "${mp.roundToInt()} MP" else "%.1f MP".format(Locale.ROOT, mp)
}

/** Shutter speed as "1/120 s" for fractions of a second, or "2 s" / "0.5 s" otherwise. */
fun formatExposure(seconds: Double): String = when {
    seconds <= 0 -> ""
    seconds < 0.5 -> "1/${(1 / seconds).roundToInt()} s"
    seconds == Math.floor(seconds) -> "${seconds.toLong()} s"
    else -> "%.1f s".format(Locale.ROOT, seconds)
}

/** Aperture as "ƒ/1.8", or "ƒ/8" for whole numbers. */
fun formatAperture(fNumber: Double): String {
    val rounded = (fNumber * 10).roundToInt() / 10.0
    return if (rounded == Math.floor(rounded)) "ƒ/${rounded.toLong()}" else "ƒ/$rounded"
}

/** Focal length as "6.9 mm", or "24 mm" for whole numbers. */
fun formatFocalLength(mm: Double): String {
    val rounded = (mm * 10).roundToInt() / 10.0
    return if (rounded == Math.floor(rounded)) "${rounded.toLong()} mm" else "$rounded mm"
}

/**
 * The MIME type to share [types] under: the type itself if they're all the same, "image/\*" or
 * "video/\*" if they share a kind, and "\*\/\*" for a mix of photos and videos.
 */
fun commonMimeType(types: List<String>): String {
    val distinct = types.distinct()
    if (distinct.size == 1) return distinct.single()
    val kinds = distinct.map { it.substringBefore('/') }.distinct()
    return if (kinds.size == 1) "${kinds.single()}/*" else "*/*"
}
