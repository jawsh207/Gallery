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

/**
 * Loads a model ([bytes], in ONNX Runtime's format) to run on [threads] processor cores. Idle
 * threads sleep rather than spin waiting for work: the text, face and description models run at
 * the same time and share the cores, so spinning would only take turns away from each other
 * (and waste battery).
 */
fun loadModel(env: OrtEnvironment, bytes: ByteArray, threads: Int): LoadedModel {
    val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(threads)
        addConfigEntry("session.intra_op.allow_spinning", "0")
    }
    return LoadedModel(env.createSession(bytes, options), options)
}
