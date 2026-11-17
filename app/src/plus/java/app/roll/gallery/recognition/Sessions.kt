package app.roll.gallery.recognition

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

/** A loaded model and its settings, closed together. */
class LoadedModel(val session: OrtSession, private val options: OrtSession.SessionOptions) : AutoCloseable {
    override fun close() {
        session.close()
        options.close()
    }
}

/** Loads a model ([bytes], in ONNX Runtime's format) to run on [threads] processor cores. */
fun loadModel(env: OrtEnvironment, bytes: ByteArray, threads: Int): LoadedModel {
    val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(threads) }
    return LoadedModel(env.createSession(bytes, options), options)
}
