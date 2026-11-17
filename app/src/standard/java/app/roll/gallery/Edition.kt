package app.roll.gallery

import android.content.Context
import app.roll.gallery.recognition.FaceFinder
import app.roll.gallery.recognition.ImageDescriber
import app.roll.gallery.recognition.TextDescriber
import app.roll.gallery.recognition.TextReader

/** What this edition of Gallery includes. The plain edition: no recognition, so no models or runtime. */
object Edition {
    /** Whether photos can be searched by the text in them. */
    const val HAS_TEXT_SEARCH = false

    /** No text reader in this edition. */
    @Suppress("UNUSED_PARAMETER")
    fun textReader(context: Context, threads: Int): TextReader? = null

    /** Whether faces are found and grouped into people. */
    const val HAS_PEOPLE = false

    /** No face finder in this edition. */
    @Suppress("UNUSED_PARAMETER")
    fun faceFinder(context: Context, threads: Int): FaceFinder? = null

    /** Whether photos can be searched by description ("a black dog"). */
    const val HAS_DESCRIPTIONS = false

    /** No image describer in this edition. */
    @Suppress("UNUSED_PARAMETER")
    fun imageDescriber(context: Context, threads: Int): ImageDescriber? = null

    /** No text describer in this edition. */
    @Suppress("UNUSED_PARAMETER")
    fun textDescriber(context: Context): TextDescriber? = null
}
