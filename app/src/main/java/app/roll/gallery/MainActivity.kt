package app.roll.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.roll.gallery.ui.GalleryApp
import app.roll.gallery.ui.theme.GalleryTheme

/** The app's only screen holder; everything inside it is drawn by [GalleryApp]. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GalleryTheme {
                GalleryApp()
            }
        }
    }
}
