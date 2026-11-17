package app.roll.gallery.recognition

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

/** A loaded model, and whether it runs on the faster XNNPACK engine. */
class LoadedModel(val session: OrtSession, private val options: OrtSession.SessionOptions, val accelerated: Boolean) : AutoCloseable {
    override fun close() {
        session.close()
        options.close()
    }
}

/**
 * Loads a model ([bytes], in ONNX Runtime's format) to run on [threads] processor cores. It tries
 * the XNNPACK engine first, which is tuned for phone processors; if that won't start, it falls
 * back to ONNX Runtime's plain engine, so a problem with the faster one never stops the work.
 *
 * With XNNPACK, the engine itself spreads the work over the cores, so the runtime's own thread
 * pool is kept to one thread and told not to spin while waiting, as ONNX Runtime advises.
 */
fun loadModel(env: OrtEnvironment, bytes: ByteArray, threads: Int): LoadedModel {
    runCatching {
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(1)
            addConfigEntry("session.intra_op.allow_spinning", "0")
            addXnnpack(mapOf("intra_op_num_threads" to threads.toString()))
        }
        return LoadedModel(env.createSession(bytes, options), options, accelerated = true)
    }
    val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(threads) }
    return LoadedModel(env.createSession(bytes, options), options, accelerated = false)
}
