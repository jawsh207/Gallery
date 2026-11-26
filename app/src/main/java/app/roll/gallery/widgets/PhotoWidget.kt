package app.roll.gallery.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Size
import android.view.View
import android.widget.RemoteViews
import app.roll.gallery.R
import app.roll.gallery.data.AppSettings
import app.roll.gallery.data.Library
import app.roll.gallery.data.Media
import app.roll.gallery.data.MediaStoreSource
import app.roll.gallery.intents.openScreenIntent
import app.roll.gallery.ui.Screen
import app.roll.gallery.ui.access.PhotoAccess
import app.roll.gallery.ui.access.photoAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * What both home-screen widgets share: reading the library, drawing a photo into the widget,
 * and what a tap opens. Each widget is drawn with Android's RemoteViews (plain layouts the home
 * screen draws), which needs no extra library. Widgets are redrawn when placed, when resized,
 * and every few hours (`updatePeriodMillis` in their `res/xml` info).
 */
abstract class PhotoWidget : AppWidgetProvider() {

    /**
     * What widget [widgetId] shows, [size] pixels across and down. [library] is null when
     * Gallery may not see photos.
     */
    protected abstract fun views(context: Context, library: Library?, widgetId: Int, size: Size): RemoteViews

    /**
     * Redraws [ids] in the background. Reading the library can take a moment on a big one, longer
     * than a broadcast may block, so the broadcast is held open with goAsync until it's done.
     */
    private fun redraw(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        scope.launch {
            try {
                val library = loadLibrary(context)
                for (id in ids) {
                    val views = runCatching { views(context, library, id, widgetSize(context, manager, id)) }
                        .getOrElse { messageViews(context, context.getString(R.string.widget_failed)) }
                    manager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** Placed, or the regular update came round: redraw them. */
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = redraw(context, manager, ids)

    /** Resized: redraw at the new size, so the photo stays sharp. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        redraw(context, manager, intArrayOf(id))

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Asks every widget of [provider] (or just [ids]) to redraw now, through the same update
         * broadcast Android sends, so it runs in the provider like any update.
         */
        fun refresh(context: Context, provider: Class<out PhotoWidget>, ids: IntArray? = null) {
            val manager = AppWidgetManager.getInstance(context)
            val targets = ids ?: manager.getAppWidgetIds(ComponentName(context, provider))
            if (targets.isEmpty()) return
            context.sendBroadcast(
                Intent(context, provider)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, targets),
            )
        }

        /**
         * Every photo and video, with the folders kept out of Photos applied, as the app sees
         * them; null without photo access. Read fresh each time: widgets update rarely, and the
         * app keeps no copy of the library to read instead.
         */
        suspend fun loadLibrary(context: Context): Library? {
            if (context.photoAccess() == PhotoAccess.None) return null
            val prefs = AppSettings(context).albumPrefs.first()
            return MediaStoreSource(context.contentResolver).load().copy(albumPrefs = prefs)
        }

        /**
         * The widget's largest size in pixels. Android gives sizes in dp, the largest being the
         * width in portrait and the height in landscape; kept between 200 and 1024 pixels so
         * the photo is sharp but well under the size limit for pictures sent to the home screen.
         */
        fun widgetSize(context: Context, manager: AppWidgetManager, id: Int): Size {
            val options = manager.getAppWidgetOptions(id)
            val density = context.resources.displayMetrics.density
            fun px(key: String, fallback: Int) =
                ((options.getInt(key).takeIf { it > 0 } ?: fallback) * density).roundToInt().coerceIn(200, 1024)
            return Size(px(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 180), px(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180))
        }

        /** [media]'s picture at about [size], from Android's thumbnail cache; null if it can't be read. */
        fun loadPicture(context: Context, media: Media, size: Size): Bitmap? =
            runCatching { context.contentResolver.loadThumbnail(media.uri, size, null) }.getOrNull()

        /**
         * A tap that opens [screen]. [widgetId] keeps each widget's tap apart: Android treats two
         * pending intents for the same activity as one unless their request codes differ.
         */
        fun tapOpening(context: Context, widgetId: Int, screen: Screen): PendingIntent =
            PendingIntent.getActivity(
                context, widgetId, openScreenIntent(context, screen),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /** A tap that just opens Gallery, on Photos (where it asks for access if it needs to). */
        private fun tapOpeningApp(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context, -1, openScreenIntent(context, Screen.Home),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /**
         * A photo filling the widget, with [title] and [subtitle] over a shade at the bottom
         * when given. Falls back to [messageViews] if the picture can't be read.
         */
        fun photoViews(
            context: Context,
            picture: Bitmap?,
            title: String?,
            subtitle: String?,
            tap: PendingIntent,
        ): RemoteViews {
            if (picture == null) return messageViews(context, context.getString(R.string.widget_failed))
            return RemoteViews(context.packageName, R.layout.widget_photo).apply {
                setImageViewBitmap(R.id.widget_picture, picture)
                setContentDescription(R.id.widget_picture, title ?: context.getString(R.string.photo))
                setViewVisibility(R.id.widget_message, View.GONE)
                setViewVisibility(R.id.widget_caption, if (title == null) View.GONE else View.VISIBLE)
                setTextViewText(R.id.widget_title, title.orEmpty())
                setTextViewText(R.id.widget_subtitle, subtitle.orEmpty())
                setViewVisibility(R.id.widget_subtitle, if (subtitle == null) View.GONE else View.VISIBLE)
                setOnClickPendingIntent(R.id.widget_root, tap)
            }
        }

        /** A short message in place of a photo ("No photos yet"); a tap opens Gallery. */
        fun messageViews(context: Context, message: String): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_photo).apply {
                setViewVisibility(R.id.widget_picture, View.GONE)
                setViewVisibility(R.id.widget_caption, View.GONE)
                setViewVisibility(R.id.widget_message, View.VISIBLE)
                setTextViewText(R.id.widget_message, message)
                setOnClickPendingIntent(R.id.widget_root, tapOpeningApp(context))
            }

        /** The message for when Gallery may not see photos. */
        fun noAccessViews(context: Context): RemoteViews = messageViews(context, context.getString(R.string.widget_no_access))
    }
}
