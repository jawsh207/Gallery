package app.roll.gallery.intents

import android.content.Context
import android.content.Intent
import app.roll.gallery.MainActivity
import app.roll.gallery.data.MediaSet
import app.roll.gallery.ui.Screen
import app.roll.gallery.ui.decodeScreen
import app.roll.gallery.ui.encode

/**
 * Opening one of Gallery's own screens from outside the app: a home-screen widget's tap, or a
 * shortcut from long-pressing the app's icon. The screen travels as the same text the back stack
 * is saved as (see [Screen.encode]), and opens over Photos, so Back lands on Photos.
 */

/** The action of an intent that opens one of Gallery's screens. Only Gallery's own widgets and shortcuts send it. */
const val ACTION_OPEN_SCREEN = "app.roll.gallery.action.OPEN_SCREEN"

/** The extra holding the screen to open, as [Screen.encode] text. */
const val EXTRA_SCREEN = "app.roll.gallery.extra.SCREEN"

/**
 * An intent that opens [screen] in a fresh copy of the app. The task is cleared so the screen
 * always opens over Photos, whatever was open before; the activity only reads its intent when it
 * starts, and the Locked folder re-locks on leaving anyway.
 */
fun openScreenIntent(context: Context, screen: Screen): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(ACTION_OPEN_SCREEN)
        .putExtra(EXTRA_SCREEN, screen.encode())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

/**
 * The screen [intent] asks to open, or null when it isn't an open-screen intent. Any app can
 * send Gallery this intent, so nothing that shows Locked folder items directly is accepted: only
 * the Locked folder screen itself, which asks for the screen lock first.
 */
fun screenToOpen(intent: Intent?): Screen? {
    val screen = intent?.takeIf { it.action == ACTION_OPEN_SCREEN }?.getStringExtra(EXTRA_SCREEN)?.let(::decodeScreen)
    val showsLocked = (screen is Screen.Viewer && screen.set == MediaSet.Locked) || (screen is Screen.Grid && screen.set == MediaSet.Locked)
    return screen.takeUnless { showsLocked }
}
