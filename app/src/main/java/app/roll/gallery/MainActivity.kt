package app.roll.gallery

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.roll.gallery.ui.GalleryApp
import app.roll.gallery.ui.theme.GalleryTheme
import kotlin.concurrent.thread

/**
 * The app's only screen holder; everything inside it is drawn by [GalleryApp]. It opens from
 * the launcher, from Gallery's widgets and icon shortcuts (which name a screen to open), and
 * also handles other apps' requests: showing a file, or choosing photos and handing them back.
 * Each time it opens it publishes the icon shortcuts again (see [publishShortcuts]).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Off the main thread: it's a call into the system, and the shortcuts change rarely.
        if (savedInstanceState == null) thread { publishShortcuts(applicationContext) }
        setContent {
            GalleryTheme {
                GalleryApp(intent, finish = ::finishWith)
            }
        }
    }

    /**
     * Ends a request from another app. Chosen [uris] go back with read access to just those
     * files (the first as the result's address, all of them as clip data for apps that take
     * several); null cancels.
     */
    private fun finishWith(uris: List<Uri>?) {
        if (uris.isNullOrEmpty()) {
            setResult(RESULT_CANCELED)
        } else {
            val result = Intent().setData(uris.first()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            result.clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            setResult(RESULT_OK, result)
        }
        finish()
    }
}
