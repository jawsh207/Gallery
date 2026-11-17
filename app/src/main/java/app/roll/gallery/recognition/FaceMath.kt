package app.roll.gallery.recognition

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The plain arithmetic of finding and matching faces, kept apart from the models so it can be
 * tested without a phone. The finder (YuNet) scores a grid of spots at three scales; the
 * matcher (SFace) turns a face, straightened and cropped to 112 × 112, into 128 numbers that are
 * close together for the same person.
 */

/** A face the finder saw: its box and five landmarks (eyes, nose tip, mouth corners), in image pixels. */
data class DetectedFace(val left: Float, val top: Float, val width: Float, val height: Float, val landmarks: FloatArray, val score: Float) {
    override fun equals(other: Any?): Boolean =
        other is DetectedFace && left == other.left && top == other.top && width == other.width &&
            height == other.height && score == other.score && landmarks.contentEquals(other.landmarks)

    override fun hashCode(): Int = listOf(left, top, width, height, score).hashCode() * 31 + landmarks.contentHashCode()
}

/** The grid spacings the finder works at, finest first. */
val FINDER_STRIDES = intArrayOf(8, 16, 32)

/**
 * Faces from the finder's raw output for an [inputWidth] × [inputHeight] input. For each
 * stride, [cls] and [obj] give one score per grid spot (row by row), [bbox] four numbers (centre
 * offset, then log size) and [kps] ten (five landmark offsets), all in grid units. A spot's
 * score is √(cls × obj); spots below [minScore] are dropped. Overlapping faces are not merged
 * here; see [suppressOverlaps].
 */
fun decodeFaces(
    cls: Map<Int, FloatArray>,
    obj: Map<Int, FloatArray>,
    bbox: Map<Int, FloatArray>,
    kps: Map<Int, FloatArray>,
    inputWidth: Int,
    inputHeight: Int,
    minScore: Float = 0.8f,
): List<DetectedFace> {
    val faces = ArrayList<DetectedFace>()
    for (stride in FINDER_STRIDES) {
        val c = cls[stride] ?: continue
        val o = obj[stride] ?: continue
        val b = bbox[stride] ?: continue
        val k = kps[stride] ?: continue
        val cols = inputWidth / stride
        val rows = inputHeight / stride
        for (i in 0 until min(rows * cols, c.size)) {
            val score = sqrt(c[i].coerceIn(0f, 1f) * o[i].coerceIn(0f, 1f))
            if (score < minScore) continue
            val col = i % cols
            val row = i / cols
            val cx = (col + b[i * 4]) * stride
            val cy = (row + b[i * 4 + 1]) * stride
            val w = exp(b[i * 4 + 2]) * stride
            val h = exp(b[i * 4 + 3]) * stride
            val marks = FloatArray(10) { j -> (k[i * 10 + j] + if (j % 2 == 0) col else row) * stride }
            faces += DetectedFace(cx - w / 2, cy - h / 2, w, h, marks, score)
        }
    }
    return faces
}

/** How much two faces' boxes overlap: shared area ÷ combined area. */
fun overlap(a: DetectedFace, b: DetectedFace): Float {
    val w = min(a.left + a.width, b.left + b.width) - max(a.left, b.left)
    val h = min(a.top + a.height, b.top + b.height) - max(a.top, b.top)
    if (w <= 0 || h <= 0) return 0f
    val shared = w * h
    return shared / (a.width * a.height + b.width * b.height - shared)
}

/** Keeps the best of each group of faces overlapping by more than [maxOverlap], best first. */
fun suppressOverlaps(faces: List<DetectedFace>, maxOverlap: Float = 0.3f): List<DetectedFace> {
    val kept = ArrayList<DetectedFace>()
    for (face in faces.sortedByDescending { it.score }) {
        if (kept.none { overlap(it, face) > maxOverlap }) kept += face
    }
    return kept
}

/** Where the matcher expects the five landmarks in its 112 × 112 face, as x, y pairs. */
val FACE_TEMPLATE = floatArrayOf(
    38.2946f, 51.6963f, 73.5318f, 51.5014f, 56.0252f, 71.7366f, 41.5493f, 92.3655f, 70.7299f, 92.2041f,
)

/**
 * The turn, uniform scale and shift that best moves the points [from] onto [to] (x, y pairs),
 * as the six numbers a, b, tx, ty of x' = a·x − b·y + tx, y' = b·x + a·y + ty (least squares).
 * Used to straighten a face so its eyes and mouth land where the matcher expects them.
 */
fun similarityTransform(from: FloatArray, to: FloatArray): FloatArray {
    val n = from.size / 2
    var fx = 0f; var fy = 0f; var tx = 0f; var ty = 0f
    for (i in 0 until n) {
        fx += from[2 * i]; fy += from[2 * i + 1]; tx += to[2 * i]; ty += to[2 * i + 1]
    }
    fx /= n; fy /= n; tx /= n; ty /= n
    var dot = 0f; var cross = 0f; var norm = 0f
    for (i in 0 until n) {
        val x = from[2 * i] - fx
        val y = from[2 * i + 1] - fy
        val u = to[2 * i] - tx
        val v = to[2 * i + 1] - ty
        dot += x * u + y * v
        cross += x * v - y * u
        norm += x * x + y * y
    }
    val a = if (norm == 0f) 1f else dot / norm
    val b = if (norm == 0f) 0f else cross / norm
    return floatArrayOf(a, b, tx - (a * fx - b * fy), ty - (b * fx + a * fy))
}

/** [v] scaled to length 1 (unchanged if it's all zeros). */
fun normalized(v: FloatArray): FloatArray {
    val length = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
    return if (length == 0f) v else FloatArray(v.size) { v[it] / length }
}

/** How alike two faces are, from their (length 1) fingerprints: 1 is identical, around 0 unrelated. */
fun similarity(a: FloatArray, b: FloatArray): Float {
    var sum = 0f
    for (i in a.indices) sum += a[i] * b[i]
    return sum
}

/** One face seen in one photo: [photoId], its place in that photo ([index]), and its fingerprint. */
data class FaceRef(val photoId: Long, val index: Int, val embedding: FloatArray) {
    /** A short name for this face that stays the same between visits: "photo#index". */
    val key: String get() = "$photoId#$index"

    override fun equals(other: Any?): Boolean = other is FaceRef && photoId == other.photoId && index == other.index
    override fun hashCode(): Int = key.hashCode()
}

/** Faces this alike (or more) are taken to be the same person. */
const val SAME_PERSON = 0.42f

/**
 * Groups [faces] into people. Each face joins the person whose average face it's most like, if
 * at least [threshold] alike, or starts a new person. Faces are taken in key order, so the
 * grouping comes out the same every time for the same faces. Biggest groups first.
 */
fun groupFaces(faces: List<FaceRef>, threshold: Float = SAME_PERSON): List<List<FaceRef>> {
    val groups = ArrayList<MutableList<FaceRef>>()
    val centres = ArrayList<FloatArray>()
    val sums = ArrayList<FloatArray>()
    for (face in faces.sortedWith(compareBy<FaceRef> { it.photoId }.thenBy { it.index })) {
        var best = -1
        var bestScore = threshold
        for (g in centres.indices) {
            val s = similarity(face.embedding, centres[g])
            if (s >= bestScore) {
                bestScore = s
                best = g
            }
        }
        if (best < 0) {
            groups += mutableListOf(face)
            sums += face.embedding.copyOf()
            centres += normalized(face.embedding)
        } else {
            groups[best] += face
            val sum = sums[best]
            for (i in sum.indices) sum[i] += face.embedding[i]
            centres[best] = normalized(sum)
        }
    }
    return groups.sortedByDescending { it.size }
}

/** A fingerprint as text for the index file (128 floats, little-endian, base64). */
fun encodeEmbedding(v: FloatArray): String {
    val buffer = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    for (x in v) buffer.putFloat(x)
    return Base64.getEncoder().withoutPadding().encodeToString(buffer.array())
}

/** A fingerprint back from [encodeEmbedding]'s text, or null if it's damaged. */
fun decodeEmbedding(text: String): FloatArray? = runCatching {
    val bytes = Base64.getDecoder().decode(text)
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    FloatArray(bytes.size / 4) { buffer.getFloat() }
}.getOrNull()
