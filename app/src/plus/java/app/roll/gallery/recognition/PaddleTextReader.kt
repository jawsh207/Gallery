package app.roll.gallery.recognition

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * Gallery Plus's text reader: PaddleOCR's text finder and Latin-script reader, run by the
 * cut-down ONNX Runtime bundled with this edition. Both models load from the app's own files
 * when it's made, and everything runs on the phone.
 *
 * For a photo: a quick look at a small copy first rules out photos with no text; then the finder
 * marks where text is, [findTextBoxes] turns that into boxes, each box
 * is cut out of the photo and read, and [decodeCharacters] turns the reader's scores into text.
 * Both models expect colours in blue-green-red order, as PaddleOCR feeds them.
 */
class PaddleTextReader(context: Context, threads: Int) : TextReader {
    private val env = OrtEnvironment.getEnvironment()
    private val finderModel = loadModel(env, context.assets.open("ocr/det.ort").use { it.readBytes() }, threads)
    private val readerModel = loadModel(env, context.assets.open("ocr/rec.ort").use { it.readBytes() }, threads)
    private val finder = finderModel.session
    private val reader = readerModel.session
    private val characters = readerCharacters(context.assets.open("ocr/rec_dict.txt").use { it.readBytes().decodeToString() })

    override fun read(bitmap: Bitmap): String {
        // Most photos have no text. A quick look at a small copy rules those out cheaply; only
        // if it sees something like text is the photo looked at properly.
        val (pw, ph) = finderInputSize(bitmap.width, bitmap.height, PROBE_SIDE)
        val probe = Bitmap.createScaledBitmap(bitmap, pw, ph, true)
        val probeMap = run(finder, toTensor(probe, FINDER_MEAN, FINDER_STD), pw, ph)
        if (probe !== bitmap) probe.recycle()
        if (findTextBoxes(probeMap, pw, ph, threshold = 0.2f, boxThreshold = 0.4f, minSide = 2).isEmpty()) return ""

        val (w, h) = finderInputSize(bitmap.width, bitmap.height)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val map = run(finder, toTensor(scaled, FINDER_MEAN, FINDER_STD), w, h)
        if (scaled !== bitmap) scaled.recycle()
        val sx = bitmap.width / w.toFloat()
        val sy = bitmap.height / h.toFloat()
        val lines = ArrayList<String>()
        for (box in findTextBoxes(map, w, h)) {
            val left = (box.left * sx).roundToInt().coerceIn(0, bitmap.width - 1)
            val top = (box.top * sy).roundToInt().coerceIn(0, bitmap.height - 1)
            val width = (box.width * sx).roundToInt().coerceIn(1, bitmap.width - left)
            val height = (box.height * sy).roundToInt().coerceIn(1, bitmap.height - top)
            if (height < MIN_TEXT_HEIGHT) continue
            var crop = Bitmap.createBitmap(bitmap, left, top, width, height)
            // Tall, narrow boxes are usually sideways text; turn them to read along the line.
            if (height >= width * 1.5f) {
                crop = Bitmap.createBitmap(crop, 0, 0, crop.width, crop.height, Matrix().apply { postRotate(-90f) }, true)
            }
            val text = readLine(crop)
            if (text.isNotBlank()) lines += text
        }
        return lines.joinToString("\n")
    }

    /** One line of text from a cut-out box, or empty if the reader isn't confident. */
    private fun readLine(crop: Bitmap): String {
        val width = readerInputWidth(crop.width, crop.height, READER_HEIGHT)
        val scaled = Bitmap.createScaledBitmap(crop, width, READER_HEIGHT, true)
        val input = toTensor(scaled, READER_MEAN, READER_STD)
        reader.run(mapOf(reader.inputNames.first() to input)).use { result ->
            val out = result[0] as OnnxTensor
            val shape = out.info.shape // [1, steps, classes]
            val scores = out.floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } }
            val (text, confidence) = decodeCharacters(scores, shape[1].toInt(), shape[2].toInt(), characters)
            input.close()
            return if (confidence >= MIN_CONFIDENCE) text.trim() else ""
        }
    }

    /** Runs the finder and returns its probability map for a [width] × [height] input. */
    private fun run(session: OrtSession, input: OnnxTensor, width: Int, height: Int): FloatArray {
        session.run(mapOf(session.inputNames.first() to input)).use { result ->
            val out = (result[0] as OnnxTensor).floatBuffer
            input.close()
            return FloatArray(width * height).also { out.get(it, 0, minOf(it.size, out.remaining())) }
        }
    }

    /**
     * [bitmap] as the models' input: channels first, in blue-green-red order, each value scaled
     * to 0–1, then shifted by [mean] and divided by [std] (given in that same channel order).
     */
    private fun toTensor(bitmap: Bitmap, mean: FloatArray, std: FloatArray): OnnxTensor {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val plane = w * h
        val data = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val c = pixels[i]
            data[i] = (Color.blue(c) / 255f - mean[0]) / std[0]
            data[plane + i] = (Color.green(c) / 255f - mean[1]) / std[1]
            data[2 * plane + i] = (Color.red(c) / 255f - mean[2]) / std[2]
        }
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, h.toLong(), w.toLong()))
    }

    override fun close() {
        finderModel.close()
        readerModel.close()
    }

    private companion object {
        // PaddleOCR's settings for these models.
        val FINDER_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        val FINDER_STD = floatArrayOf(0.229f, 0.224f, 0.225f)
        val READER_MEAN = floatArrayOf(0.5f, 0.5f, 0.5f)
        val READER_STD = floatArrayOf(0.5f, 0.5f, 0.5f)
        const val READER_HEIGHT = 48
        // The quick first look's size: a quarter of the pixels of the full look.
        const val PROBE_SIDE = 480
        const val MIN_TEXT_HEIGHT = 8
        const val MIN_CONFIDENCE = 0.5f
    }
}
