package app.roll.gallery.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.roll.gallery.ui.GalleryViewModel

/**
 * Asks once for permission to show notifications, in Gallery Plus while looking at photos for
 * search is on, so the progress notification of that work can be seen. Android hides it
 * otherwise (the work still runs). Asked only once whatever the answer; it can be changed
 * later in Android's settings for Gallery.
 */
@Composable
fun AskForNotifications(viewModel: GalleryViewModel) {
    val context = LocalContext.current
    val shouldAsk by viewModel.shouldAskNotifications.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(shouldAsk) {
        if (shouldAsk != true) return@LaunchedEffect
        // Not on top of the photo access question; there's nothing to look at until that's answered.
        if (context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) return@LaunchedEffect
        viewModel.askedNotifications()
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
