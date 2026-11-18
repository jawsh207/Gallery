package app.roll.gallery.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.roll.gallery.R
import app.roll.gallery.data.Creation

/** The icon, name and one-line description of [creation]. */
fun creationLook(creation: Creation): Triple<ImageVector, Int, Int> = when (creation) {
    Creation.Collage -> Triple(Icons.Outlined.AutoAwesomeMosaic, R.string.create_collage, R.string.create_collage_summary)
    Creation.Animation -> Triple(Icons.Outlined.Loop, R.string.create_animation, R.string.create_animation_summary)
    Creation.VideoMaker -> Triple(Icons.Outlined.Movie, R.string.create_video, R.string.create_video_summary)
    Creation.Moods -> Triple(Icons.Outlined.Palette, R.string.create_moods, R.string.create_moods_summary)
}

/**
 * The Create tab: tiles for making a collage, an animation or a video, or giving a photo a mood.
 * Each starts by choosing photos ([onStart]); everything is made on the phone and saved as new
 * files, leaving the photos used as they were.
 */
@Composable
fun CreateTab(onStart: (Creation) -> Unit, contentPadding: PaddingValues) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (pair in Creation.entries.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (creation in pair) CreateTile(creation, Modifier.weight(1f)) { onStart(creation) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** One thing to make: a large tile with its icon, name and what it does. */
@Composable
private fun CreateTile(creation: Creation, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (icon, title, summary) = creationLook(creation)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(Modifier.padding(20.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
            )
        }
    }
}
