package app.roll.gallery

import android.content.Context
import app.roll.gallery.recognition.PaddleTextReader
import app.roll.gallery.recognition.FaceFinder
import app.roll.gallery.recognition.TextReader
import app.roll.gallery.recognition.YuNetFaceFinder

/** What this edition of Gallery includes. Gallery Plus: reading text in photos, and people. */
object Edition {
    /** Whether photos can be searched by the text in them. */
    const val HAS_TEXT_SEARCH = true

    /** A reader for the text in photos, using the bundled PaddleOCR models. Close it when done. */
    fun textReader(context: Context): TextReader? = PaddleTextReader(context)

    /** Whether faces are found and grouped into people. */
    const val HAS_PEOPLE = true

    /** A face finder using the bundled YuNet and SFace models. Close it when done. */
    fun faceFinder(context: Context): FaceFinder? = YuNetFaceFinder(context)
}
