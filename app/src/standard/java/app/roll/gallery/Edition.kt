package app.roll.gallery

import android.content.Context
import app.roll.gallery.recognition.TextReader

/** What this edition of Gallery includes. The plain edition: no recognition, so no models or runtime. */
object Edition {
    /** Whether photos can be searched by the text in them. */
    const val HAS_TEXT_SEARCH = false

    /** No text reader in this edition. */
    @Suppress("UNUSED_PARAMETER")
    fun textReader(context: Context): TextReader? = null
}
