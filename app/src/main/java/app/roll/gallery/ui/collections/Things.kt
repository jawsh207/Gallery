package app.roll.gallery.ui.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.roll.gallery.R
import app.roll.gallery.data.Media
import app.roll.gallery.image.thumbnail
import app.roll.gallery.recognition.Thing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import coil3.compose.AsyncImage

/** The name of a [Thing]. */
fun thingName(thing: Thing): Int = when (thing) {
    Thing.Documents -> R.string.thing_documents
    Thing.Receipts -> R.string.thing_receipts
    Thing.Selfies -> R.string.thing_selfies
    Thing.Pets -> R.string.thing_pets
    Thing.Food -> R.string.thing_food
    Thing.Landscapes -> R.string.thing_landscapes
    Thing.Flowers -> R.string.thing_flowers
    Thing.Cars -> R.string.thing_cars
}

/**
 * Things (Gallery Plus): a card for each kind of photo found ([things], photo ids by Thing, best
 * match first), with its best match as the cover; [onOpen] opens one.
 */
@Composable
fun ThingsRow(things: Map<Thing, List<Long>>, photosById: Map<Long, Media>, onOpen: (Thing) -> Unit) {
    val shown = Thing.entries.mapNotNull { thing ->
        val cover = things[thing]?.firstNotNullOfOrNull { photosById[it] } ?: return@mapNotNull null
        thing to cover
    }
    if (shown.isEmpty()) return
    Column {
        Text(
            stringResource(R.string.things),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
            items(shown, key = { it.first.name }) { (thing, cover) ->
                Box(Modifier.size(112.dp).clip(RoundedCornerShape(20.dp)).clickable { onOpen(thing) }) {
                    AsyncImage(
                        model = cover.thumbnail(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
                    Text(
                        stringResource(thingName(thing)),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                    )
                }
            }
        }
    }
}
