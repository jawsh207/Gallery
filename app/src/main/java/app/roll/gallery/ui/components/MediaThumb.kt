package app.roll.gallery.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.roll.gallery.R
import app.roll.gallery.data.Media
import app.roll.gallery.data.formatDuration
import app.roll.gallery.image.Thumbnail
import coil3.compose.AsyncImage

/**
 * One square of the photo grid. Videos show their length, favorites a heart. While selecting,
 * each square shows a check circle, and a selected one shrinks into a rounded tile, as in
 * other gallery apps.
 */
@Composable
fun MediaThumb(
    media: Media,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inset by animateDpAsState(if (selected) 10.dp else 0.dp, label = "inset")
    val corner by animateDpAsState(if (selected) 14.dp else 0.dp, label = "corner")
    val description = stringResource(if (media.isVideo) R.string.video else R.string.photo)
    Box(
        modifier
            .aspectRatio(1f)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = selected
            },
    ) {
        AsyncImage(
            model = Thumbnail(media.uri, media.modifiedAt),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(inset)
                .clip(RoundedCornerShape(corner))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        val shadow = MaterialTheme.typography.labelMedium.copy(
            color = Color.White,
            shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 6f),
        )
        if (media.isVideo) {
            Row(Modifier.align(Alignment.TopEnd).padding(6.dp + inset), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(media.durationMs), style = shadow)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        if (media.isFavorite && !selecting) {
            Icon(
                Icons.Rounded.Favorite, contentDescription = stringResource(R.string.favorite),
                tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(16.dp),
            )
        }
        if (selecting) {
            val check = Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp)
            if (selected) {
                Icon(
                    Icons.Rounded.CheckCircle, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = check.background(MaterialTheme.colorScheme.onPrimary, CircleShape),
                )
            } else {
                Icon(Icons.Rounded.RadioButtonUnchecked, contentDescription = null, tint = Color.White, modifier = check)
            }
        }
    }
}
