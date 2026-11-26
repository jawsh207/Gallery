package app.roll.gallery

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.roll.gallery.data.Creation
import app.roll.gallery.data.MediaSet
import app.roll.gallery.intents.openScreenIntent
import app.roll.gallery.ui.Screen

/**
 * The shortcuts shown when Gallery's icon is long-pressed: Search, Favorites, Collage and the
 * Locked folder, each opening that screen over Photos. They're published from the app each time
 * it opens rather than listed in a resource file, because a resource file has to name the app's
 * package and the dev build's package differs. Publishing again also keeps their names in the
 * phone's current language. The Locked folder shortcut still asks for the screen lock.
 */
fun publishShortcuts(context: Context) {
    val manager = context.getSystemService(ShortcutManager::class.java) ?: return
    var rank = 0
    fun shortcut(id: String, @StringRes short: Int, @StringRes long: Int, @DrawableRes icon: Int, screen: Screen) =
        ShortcutInfo.Builder(context, id)
            .setShortLabel(context.getString(short))
            .setLongLabel(context.getString(long))
            .setIcon(Icon.createWithResource(context, icon))
            .setIntent(openScreenIntent(context, screen))
            .setRank(rank++)
            .build()
    val shortcuts = listOf(
        shortcut("search", R.string.shortcut_search, R.string.search_photos, R.drawable.ic_shortcut_search, Screen.Search),
        shortcut("favorites", R.string.favorites, R.string.favorites, R.drawable.ic_shortcut_favorites, Screen.Grid(MediaSet.Favorites)),
        shortcut("collage", R.string.shortcut_collage, R.string.shortcut_collage_long, R.drawable.ic_shortcut_collage, Screen.Choose(Creation.Collage)),
        shortcut("locked", R.string.shortcut_locked, R.string.locked_folder, R.drawable.ic_shortcut_locked, Screen.Locked),
    )
    // Launchers show about four; more would be cut off.
    runCatching { manager.setDynamicShortcuts(shortcuts) }
}
