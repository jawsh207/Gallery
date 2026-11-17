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
import java.nio.LongBuffer

/** The fingerprint a model run produced: the output named [name] if there is one, else the first. */
private fun OrtSession.Result.fingerprint(name: String): FloatArray {
    val tensor = (get(name).orElse(null) ?: get(0)) as OnnxTensor
    return tensor.floatBuffer.let { buf -> FloatArray(buf.remaining()).also { buf.get(it) } }
}

/**
 * Gallery Plus's image half of TinyCLIP (ViT-39M/16, shrunk to 8-bit), on the bundled runtime.
 * A photo is scaled so its shorter side is 224 pixels, the middle 224 × 224 is cut out, and
 * its colours are normalised with CLIP's averages, as the model was trained.
 */
class TinyClipImageDescriber(context: Context, threads: Int) : ImageDescriber {
    private val env = OrtEnvironment.getEnvironment()
    private val model = loadModel(env, context.assets.open("clip/clip_image.ort").use { it.readBytes() }, threads)

    override fun describe(bitmap: Bitmap): FloatArray {
        val scale = SIZE.toFloat() / minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        Canvas(square).drawBitmap(
            bitmap,
            Matrix().apply {
                setScale(scale, scale)
                postTranslate((SIZE - bitmap.width * scale) / 2, (SIZE - bitmap.height * scale) / 2)
            },
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        val pixels = IntArray(SIZE * SIZE)
        square.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        square.recycle()
        val plane = SIZE * SIZE
        val data = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val c = pixels[i]
            data[i] = (Color.red(c) / 255f - MEAN[0]) / STD[0]
            data[plane + i] = (Color.green(c) / 255f - MEAN[1]) / STD[1]
            data[2 * plane + i] = (Color.blue(c) / 255f - MEAN[2]) / STD[2]
        }
        val input = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong()))
        val out = model.session.run(mapOf(model.session.inputNames.first() to input)).use { it.fingerprint("image_embeds") }
        input.close()
        return normalized(out)
    }

    override fun close() = model.close()

    private companion object {
        const val SIZE = 224
        val MEAN = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        val STD = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
    }
}

/**
 * Gallery Plus's text half of TinyCLIP (Text-19M, shrunk to 8-bit), used while searching: the
 * words go through [ClipTokenizer] and the model gives their fingerprint.
 */
class TinyClipTextDescriber(context: Context) : TextDescriber {
    private val env = OrtEnvironment.getEnvironment()
    private val model = loadModel(env, context.assets.open("clip/clip_text.ort").use { it.readBytes() }, threads = 2)
    private val tokenizer = ClipTokenizer(
        context.assets.open("clip/clip_vocab.txt").bufferedReader().readLines(),
        context.assets.open("clip/clip_merges.txt").bufferedReader().readLines(),
    )

    override fun describe(text: String): FloatArray {
        val ids = tokenizer.encode(text).map { it.toLong() }.toLongArray()
        val shape = longArrayOf(1, ids.size.toLong())
        val inputs = HashMap<String, OnnxTensor>()
        inputs["input_ids"] = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape)
        // Some exports also want a mask; every piece is real text, so it's all ones.
        if ("attention_mask" in model.session.inputNames) {
            inputs["attention_mask"] = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { 1 }), shape)
        }
        val out = model.session.run(inputs).use { it.fingerprint("text_embeds") }
        inputs.values.forEach { it.close() }
        return normalized(out)
    }

    override fun close() = model.close()
}
