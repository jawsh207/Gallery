package app.roll.gallery.widgets

import android.content.Context
import android.util.Size
import android.widget.RemoteViews
import app.roll.gallery.R
import app.roll.gallery.data.FrameSource
import app.roll.gallery.data.Library
import app.roll.gallery.data.Memory
import app.roll.gallery.data.allMemories
import app.roll.gallery.data.framePhotos
import app.roll.gallery.data.framePick
import app.roll.gallery.data.widgetMemory
import app.roll.gallery.ui.Screen
import app.roll.gallery.data.MediaSet
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The Memories widget: today's memory (On this day first, then Recent highlights, then a busy
 * day) as its cover photo with its name, opening the memory's slideshow when tapped. On a day
 * with no memories it shows a favorite instead, a different one each day. It shows memories even
 * when the On this day setting hides them in Photos: placing the widget is asking for them.
 */
class MemoriesWidget : PhotoWidget() {
    /** Today's memory, or the favorite of the day. */
    override fun views(context: Context, library: Library?, widgetId: Int, size: Size): RemoteViews {
        library ?: return noAccessViews(context)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val memory = widgetMemory(allMemories(library.timeline, today, zone))
        if (memory != null) {
            return photoViews(
                context, loadPicture(context, memory.cover, size),
                title = title(context, memory),
                subtitle = if (!memory.recent && memory.day == null) context.getString(R.string.on_this_day) else null,
                tap = tapOpening(context, widgetId, Screen.Viewer(MediaSet.Memory(memory.key), memory.cover.id, slideshow = true)),
            )
        }
        val photos = framePhotos(library, FrameSource.Favorites)
        val photo = framePick(photos.items, widgetId, today.toEpochDay())
            ?: return messageViews(context, context.getString(R.string.no_photos_title))
        return photoViews(
            context, loadPicture(context, photo, size), title = null, subtitle = null,
            tap = tapOpening(context, widgetId, Screen.Viewer(photos.set, photo.id)),
        )
    }

    /** The memory's name, worded as on its card in Photos. */
    private fun title(context: Context, memory: Memory): String = when {
        memory.recent -> context.getString(R.string.memory_recent)
        memory.day != null -> memory.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        else -> context.resources.getQuantityString(R.plurals.years_ago, memory.yearsAgo, memory.yearsAgo)
    }
}
