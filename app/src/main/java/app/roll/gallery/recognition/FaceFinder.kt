package app.roll.gallery.recognition

import android.graphics.Bitmap

/**
 * Finds the faces in a photo and fingerprints each, on the phone. Only Gallery Plus has one
 * (see `Edition`).
 */
interface FaceFinder : AutoCloseable {
    /** Whether it runs on the faster XNNPACK engine (shown in Settings). */
    val accelerated: Boolean

    /** The faces in [bitmap], with boxes as fractions of its size; empty if there are none. */
    fun find(bitmap: Bitmap): List<StoredFace>
}
