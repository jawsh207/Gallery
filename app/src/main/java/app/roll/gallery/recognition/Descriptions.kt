package app.roll.gallery.recognition

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.util.Base64
import kotlin.math.roundToInt

/**
 * Turns photos into "description fingerprints" for searching by description (Gallery Plus):
 * lists of numbers that land close to the fingerprint of words describing the photo.
 */
interface ImageDescriber : AutoCloseable {
    /** [bitmap]'s fingerprint, length 1. */
    fun describe(bitmap: Bitmap): FloatArray
}

/** Turns a search's words into a fingerprint comparable with [ImageDescriber]'s (Gallery Plus). */
interface TextDescriber : AutoCloseable {
    /** The fingerprint of [text], length 1. */
    fun describe(text: String): FloatArray
}

/**
 * A fingerprint of length 1 stored small: each number as a byte from −127 to 127 (×127). Close
 * enough for ranking photos, and a quarter of the space.
 */
fun packFingerprint(v: FloatArray): ByteArray = ByteArray(v.size) { (v[it] * 127f).roundToInt().coerceIn(-127, 127).toByte() }

/**
 * How alike a packed photo fingerprint and a (length 1) search fingerprint are, as the cosine
 * similarity: roughly −1 to 1, higher is a better match.
 */
fun packedSimilarity(packed: ByteArray, query: FloatArray): Float {
    var sum = 0f
    for (i in packed.indices) sum += packed[i] * query[i]
    return sum / 127f
}

/**
 * The photos that match a description best: every photo whose similarity to [query] is at least
 * [minScore] and within [margin] of the best match, best first, at most [limit]. Description
 * similarities are small numbers that vary from search to search, so "close to the best" is a
 * steadier rule than one fixed cut-off.
 */
fun bestMatches(
    fingerprints: Map<Long, ByteArray>,
    query: FloatArray,
    minScore: Float = 0.20f,
    margin: Float = 0.05f,
    limit: Int = 300,
): List<Long> {
    val scored = fingerprints.map { (id, packed) -> id to packedSimilarity(packed, query) }
    val best = scored.maxOfOrNull { it.second } ?: return emptyList()
    val floor = maxOf(minScore, best - margin)
    return scored.filter { it.second >= floor }.sortedByDescending { it.second }.take(limit).map { it.first }
}

/** One photo's line in the description file: id, change time, packed fingerprint (empty if it couldn't be read). */
fun formatDescriptionLine(id: Long, modifiedAt: Long, packed: ByteArray?): String =
    "$id\t$modifiedAt\t${packed?.let { Base64.getEncoder().withoutPadding().encodeToString(it) }.orEmpty()}"

/** The id, change time and packed fingerprint (null if none) a line holds, or null if the line is damaged. */
fun parseDescriptionLine(line: String): Triple<Long, Long, ByteArray?>? {
    val parts = line.split('\t', limit = 3)
    if (parts.size < 2) return null
    val id = parts[0].toLongOrNull() ?: return null
    val modified = parts[1].toLongOrNull() ?: return null
    val text = parts.getOrElse(2) { "" }
    val packed = if (text.isEmpty()) null else runCatching { Base64.getDecoder().decode(text) }.getOrNull()
    return Triple(id, modified, packed)
}

/**
 * Photos' description fingerprints (Gallery Plus), in one file in Gallery's private storage, so
 * searching by description doesn't look at photos again. Works like [TextIndex]: one line per
 * photo, later lines win, edited photos are redone. About half a kilobyte per photo; no
 * pictures are kept.
 */
class DescriptionIndex(context: Context) {
    private val file = File(context.filesDir, "descriptions_index.tsv")

    /** Every photo looked at so far: its change time, and its fingerprint (null if it couldn't be read). */
    fun load(): Map<Long, Pair<Long, ByteArray?>> {
        if (!file.exists()) return emptyMap()
        val entries = HashMap<Long, Pair<Long, ByteArray?>>()
        file.forEachLine { line -> parseDescriptionLine(line)?.let { (id, modified, packed) -> entries[id] = modified to packed } }
        return entries
    }

    /** Adds lines for photos ([lines]: id, change time, fingerprint). */
    fun append(lines: List<Triple<Long, Long, ByteArray?>>) {
        if (lines.isEmpty()) return
        file.appendText(lines.joinToString("") { (id, modified, packed) -> formatDescriptionLine(id, modified, packed) + "\n" })
    }

    /** Replaces the file with [entries] (id to change time and fingerprint). */
    fun rewrite(entries: Map<Long, Pair<Long, ByteArray?>>) {
        val temp = File(file.parentFile, file.name + ".new")
        temp.writeText(entries.entries.joinToString("") { (id, v) -> formatDescriptionLine(id, v.first, v.second) + "\n" })
        temp.renameTo(file)
    }

    /** Deletes every fingerprint, for when searching by description is turned off. */
    fun clear() {
        file.delete()
    }
}
