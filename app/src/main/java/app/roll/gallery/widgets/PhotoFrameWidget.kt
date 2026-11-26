package app.roll.gallery.widgets

import android.content.Context
import android.util.Size
import android.widget.RemoteViews
import app.roll.gallery.R
import app.roll.gallery.data.FrameSource
import app.roll.gallery.data.Library
import app.roll.gallery.data.decodeFrameSource
import app.roll.gallery.data.encode
import app.roll.gallery.data.framePhotos
import app.roll.gallery.data.framePick
import app.roll.gallery.data.frameSlot
import app.roll.gallery.ui.Screen

/**
 * The Photo frame widget: one photo filling the widget, from Favorites, every photo, or one
 * album (chosen in [FrameSetupActivity] when it's placed, and again from the widget's settings),
 * moving on to another every few hours. A tap opens the photo in the viewer, paging through
 * the same set.
 */
class PhotoFrameWidget : PhotoWidget() {
    /** The photo for this stretch of hours. */
    override fun views(context: Context, library: Library?, widgetId: Int, size: Size): RemoteViews {
        library ?: return noAccessViews(context)
        val photos = framePhotos(library, source(context, widgetId))
        val photo = framePick(photos.items, widgetId, frameSlot(System.currentTimeMillis()))
            ?: return messageViews(context, context.getString(R.string.no_photos_title))
        return photoViews(
            context, loadPicture(context, photo, size), title = null, subtitle = null,
            tap = tapOpening(context, widgetId, Screen.Viewer(photos.set, photo.id)),
        )
    }

    /** Forgets removed frames' choices. */
    override fun onDeleted(context: Context, ids: IntArray) {
        val edit = prefs(context).edit()
        ids.forEach { edit.remove(key(it)) }
        edit.apply()
    }

    companion object {
        /** Where frame [widgetId] takes its photos from; Favorites until one is chosen. */
        fun source(context: Context, widgetId: Int): FrameSource = decodeFrameSource(prefs(context).getString(key(widgetId), null))

        /** Keeps [source] as frame [widgetId]'s choice. */
        fun setSource(context: Context, widgetId: Int, source: FrameSource) {
            prefs(context).edit().putString(key(widgetId), source.encode()).apply()
        }

        // Widget choices only; kept apart from the app's own settings, which are about the app.
        private fun prefs(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

        private fun key(widgetId: Int) = "frame_$widgetId"
    }
}
