package app.roll.gallery.recognition

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import java.nio.FloatBuffer
import kotlin.math.min

/**
 * Gallery Plus's face finder: YuNet finds faces and their landmarks, then each face is turned
 * and scaled so its eyes and mouth sit where SFace expects ([FACE_TEMPLATE]), cropped to
 * 112 × 112, and SFace turns it into a 128-number fingerprint. Both models come from the
 * OpenCV Zoo and run on the bundled cut-down ONNX Runtime, on the phone.
 *
 * YuNet has a fixed 640 × 640 input: the photo is shrunk to fit and the rest left black. It
 * takes colours in blue-green-red order as plain 0–255 values; SFace takes red-green-blue, also
 * 0–255 (its own first layer rescales them).
 */
class YuNetFaceFinder(context: Context) : FaceFinder {
    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
    private val finder = env.createSession(context.assets.open("faces/yunet.ort").use { it.readBytes() }, options)
    private val matcher = env.createSession(context.assets.open("faces/sface.ort").use { it.readBytes() }, options)

    override fun find(bitmap: Bitmap): List<StoredFace> {
        val scale = min(INPUT.toFloat() / bitmap.width, INPUT.toFloat() / bitmap.height)
        val input = Bitmap.createBitmap(INPUT, INPUT, Bitmap.Config.ARGB_8888)
        Canvas(input).apply {
            drawColor(Color.BLACK)
            drawBitmap(bitmap, Matrix().apply { setScale(scale, scale) }, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val tensor = tensorOf(input, INPUT, INPUT, blueFirst = true)
        input.recycle()
        val faces = finder.run(mapOf(finder.inputNames.first() to tensor)).use { result ->
            fun outputs(prefix: String) = FINDER_STRIDES.associateWith { s ->
                (result.get("${prefix}_$s").get() as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } }
            }
            decodeFaces(outputs("cls"), outputs("obj"), outputs("bbox"), outputs("kps"), INPUT, INPUT)
        }
        tensor.close()
        return suppressOverlaps(faces)
            // Tiny faces in the background fingerprint badly; leave them out.
            .filter { it.width / scale >= MIN_FACE && it.height / scale >= MIN_FACE }
            .take(MAX_FACES)
            .map { face ->
                // Back to the photo's own pixels.
                val marks = FloatArray(10) { face.landmarks[it] / scale }
                StoredFace(
                    left = (face.left / scale / bitmap.width).coerceIn(0f, 1f),
                    top = (face.top / scale / bitmap.height).coerceIn(0f, 1f),
                    width = (face.width / scale / bitmap.width).coerceIn(0f, 1f),
                    height = (face.height / scale / bitmap.height).coerceIn(0f, 1f),
                    embedding = fingerprint(bitmap, marks),
                )
            }
    }

    /** The fingerprint of the face whose landmarks are [marks] (in [bitmap]'s pixels), length 1. */
    private fun fingerprint(bitmap: Bitmap, marks: FloatArray): FloatArray {
        val (a, b, tx, ty) = similarityTransform(marks, FACE_TEMPLATE).toList()
        val aligned = Bitmap.createBitmap(FACE, FACE, Bitmap.Config.ARGB_8888)
        val matrix = Matrix().apply { setValues(floatArrayOf(a, -b, tx, b, a, ty, 0f, 0f, 1f)) }
        Canvas(aligned).drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        val tensor = tensorOf(aligned, FACE, FACE, blueFirst = false)
        aligned.recycle()
        val embedding = matcher.run(mapOf(matcher.inputNames.first() to tensor)).use { result ->
            (result[0] as OnnxTensor).floatBuffer.let { buf -> FloatArray(buf.remaining()).also { buf.get(it) } }
        }
        tensor.close()
        return normalized(embedding)
    }

    /** [bitmap] as a model input: channels first, plain 0–255 values, blue first or red first. */
    private fun tensorOf(bitmap: Bitmap, width: Int, height: Int, blueFirst: Boolean): OnnxTensor {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val plane = width * height
        val data = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val c = pixels[i]
            val first = if (blueFirst) Color.blue(c) else Color.red(c)
            val last = if (blueFirst) Color.red(c) else Color.blue(c)
            data[i] = first.toFloat()
            data[plane + i] = Color.green(c).toFloat()
            data[2 * plane + i] = last.toFloat()
        }
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, height.toLong(), width.toLong()))
    }

    override fun close() {
        finder.close()
        matcher.close()
        options.close()
    }

    private companion object {
        const val INPUT = 640
        const val FACE = 112
        // Smallest face worth keeping, in the photo's own pixels (photos are read at up to 1280).
        const val MIN_FACE = 48f
        const val MAX_FACES = 20
    }
}
