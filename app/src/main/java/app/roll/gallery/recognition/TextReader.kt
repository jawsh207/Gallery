package app.roll.gallery.recognition

import android.graphics.Bitmap

/**
 * Reads the text in a photo, on the phone. Only Gallery Plus has one (see `Edition`); the
 * plain edition leaves text search out entirely.
 */
interface TextReader : AutoCloseable {
    /** Whether it runs on the faster XNNPACK engine (shown in Settings). */
    val accelerated: Boolean

    /** The text found in [bitmap], lines in reading order separated by newlines; empty if none. */
    fun read(bitmap: Bitmap): String
}
