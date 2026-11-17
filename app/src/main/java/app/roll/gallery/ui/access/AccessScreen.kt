package app.roll.gallery.ui.access

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.roll.gallery.R
import app.roll.gallery.ui.components.EmptyState

/** How much of the phone's photos the app may see. */
enum class PhotoAccess {
    /** Nothing yet: the app shows [AccessScreen]. */
    None,

    /** Only the photos the user picked in Android's "Select photos" dialog. */
    Partial,

    /** Every photo and video. */
    Full,
}

/** The permissions the app asks for together; Android shows them as one dialog. */
val PHOTO_PERMISSIONS = arrayOf(
    Manifest.permission.READ_MEDIA_IMAGES,
    Manifest.permission.READ_MEDIA_VIDEO,
    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
)

/** What the app may see right now. Asked fresh each time, as the user can change it in Settings. */
fun Context.photoAccess(): PhotoAccess {
    fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    return when {
        granted(Manifest.permission.READ_MEDIA_IMAGES) || granted(Manifest.permission.READ_MEDIA_VIDEO) -> PhotoAccess.Full
        granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> PhotoAccess.Partial
        else -> PhotoAccess.None
    }
}

/**
 * Shown until the app may see photos: why it needs access, a button to ask, and a way into the
 * app's Android settings for when the user has said no twice and Android stops asking.
 */
@Composable
fun AccessScreen(onAllow: () -> Unit) {
    val context = LocalContext.current
    EmptyState(
        Icons.Outlined.PhotoLibrary,
        stringResource(R.string.access_title),
        stringResource(R.string.access_body),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = onAllow) { Text(stringResource(R.string.access_allow)) }
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            }) { Text(stringResource(R.string.open_settings)) }
        }
    }
}
