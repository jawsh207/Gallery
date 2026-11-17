package app.roll.gallery.ui.viewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.roll.gallery.R
import app.roll.gallery.data.CameraDetails
import app.roll.gallery.data.Media
import app.roll.gallery.data.formatBytes
import app.roll.gallery.data.formatDuration
import app.roll.gallery.data.megapixels
import app.roll.gallery.ui.GalleryViewModel
import app.roll.gallery.ui.components.currentLocale
import app.roll.gallery.ui.components.takenAtText

/**
 * The details sheet for the photo or video on screen: when it was taken, its file and folder,
 * size and resolution, the camera settings stored in a photo, and for trashed items when
 * Android will delete them.
 */
@Composable
fun InfoSheet(media: Media, viewModel: GalleryViewModel, onDismiss: () -> Unit) {
    val locale = currentLocale()
    val camera by produceState<CameraDetails?>(null, media.id) { value = viewModel.cameraDetails(media) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            val (date, time) = takenAtText(media.takenAt, locale)
            Text(date, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                time,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            val sizeParts = listOfNotNull(
                if (media.isVideo) formatDuration(media.durationMs) else megapixels(media.width, media.height),
                if (media.width > 0 && media.height > 0) "${media.width} × ${media.height}" else null,
                formatBytes(media.sizeBytes),
            )
            InfoRow(
                if (media.isVideo) Icons.Outlined.Videocam else Icons.Outlined.Image,
                media.fileName,
                sizeParts.joinToString(" · "),
            )
            // Files handed over by other apps have no folder Gallery can see.
            if (media.externalUri == null) {
                InfoRow(Icons.Outlined.Folder, media.albumName.ifBlank { stringResource(R.string.unknown_folder) }, media.path)
            }
            camera?.let { details ->
                val settings = listOfNotNull(details.aperture, details.exposure, details.focalLength, details.iso)
                InfoRow(
                    Icons.Outlined.CameraAlt,
                    details.camera ?: stringResource(R.string.camera),
                    settings.joinToString(" · ").ifEmpty { null },
                )
            }
            if (media.isTrashed && media.expiresAt > 0) {
                InfoRow(
                    Icons.Outlined.AutoDelete,
                    stringResource(R.string.deleted_on, takenAtText(media.expiresAt * 1000, locale).first),
                    null,
                )
            }
        }
    }
}

/** A line of the sheet: an icon, a main line and an optional second line. */
@Composable
private fun InfoRow(icon: ImageVector, title: String, detail: String?) {
    val supporting: (@Composable () -> Unit)? = detail?.let { text -> @Composable { Text(text) } }
    ListItem(
        supportingContent = supporting,
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) { Text(title) }
}
