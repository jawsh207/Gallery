package app.roll.gallery

import android.content.Context
import app.roll.gallery.recognition.PaddleTextReader
import app.roll.gallery.recognition.TextReader

/** What this edition of Gallery includes. Gallery Plus: reading text in photos. */
object Edition {
    /** Whether photos can be searched by the text in them. */
    const val HAS_TEXT_SEARCH = true

    /** A reader for the text in photos, using the bundled PaddleOCR models. Close it when done. */
    fun textReader(context: Context): TextReader? = PaddleTextReader(context)
}
