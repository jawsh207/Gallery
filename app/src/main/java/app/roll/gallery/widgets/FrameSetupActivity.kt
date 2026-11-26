package app.roll.gallery.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roll.gallery.R
import app.roll.gallery.data.Album
import app.roll.gallery.data.FrameSource
import app.roll.gallery.image.thumbnail
import app.roll.gallery.ui.collections.BackButton
import app.roll.gallery.ui.theme.GalleryTheme
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Choosing where a Photo frame widget takes its photos from: Favorites, every photo, or one
 * album. Android opens it when the frame is placed, and again from the widget's settings
 * (long-press the widget). Until a choice is made Android treats the frame as not placed, so
 * leaving without choosing cancels it.
 */
class FrameSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Android's rule: a configuration screen that ends without saying OK leaves no widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            GalleryTheme {
                FrameSetup(
                    current = remember { PhotoFrameWidget.source(this, widgetId) },
                    onChoose = { source ->
                        PhotoFrameWidget.setSource(this, widgetId, source)
                        PhotoWidget.refresh(this, PhotoFrameWidget::class.java, intArrayOf(widgetId))
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                        finish()
                    },
                    onCancel = ::finish,
                )
            }
        }
    }

    /** The list of choices, with the albums read from the media store once it opens. */
    @Composable
    private fun FrameSetup(current: FrameSource, onChoose: (FrameSource) -> Unit, onCancel: () -> Unit) {
        val albums by produceState(emptyList<Album>()) {
            value = withContext(Dispatchers.IO) { PhotoWidget.loadLibrary(this@FrameSetupActivity)?.albums.orEmpty() }
        }
        Scaffold(
            topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_frame_setup)) }, navigationIcon = { BackButton(onCancel) }) },
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp)) {
                item {
                    Choice(current == FrameSource.Favorites, { onChoose(FrameSource.Favorites) }, { IconTile(Icons.Outlined.FavoriteBorder) }) {
                        Text(stringResource(R.string.favorites), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.widget_frame_favorites_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    Choice(current == FrameSource.All, { onChoose(FrameSource.All) }, { IconTile(Icons.Outlined.Photo) }) {
                        Text(stringResource(R.string.widget_frame_all), style = MaterialTheme.typography.titleSmall)
                    }
                }
                if (albums.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.albums),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                }
                items(albums, key = { it.id }) { album ->
                    Choice(
                        current == FrameSource.Album(album.id), { onChoose(FrameSource.Album(album.id)) },
                        {
                            AsyncImage(
                                model = album.cover.thumbnail(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                            )
                        },
                    ) {
                        Text(album.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            pluralStringResource(R.plurals.item_count, album.count, album.count),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    /** One row: a picture, words, and a radio button showing the frame's current choice. */
    @Composable
    private fun Choice(selected: Boolean, onClick: () -> Unit, picture: @Composable () -> Unit, words: @Composable () -> Unit) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            picture()
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) { words() }
            RadioButton(selected = selected, onClick = onClick)
        }
    }

    /** A rounded tile with [icon], the size of an album cover. */
    @Composable
    private fun IconTile(icon: ImageVector) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
